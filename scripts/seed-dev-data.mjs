#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const DB_NAME = "sillage-db";
const MODEL = "seed/sillage-current-design-v1";
const SOURCE_TYPES = {
  entry: JSON.stringify(["entry"]),
  entryAi: JSON.stringify(["entry", "entry-ai"]),
  all: JSON.stringify(["entry", "entry-ai", "summary"]),
};

function usage() {
  console.error(
    [
      "usage: node scripts/seed-dev-data.mjs --local|--remote [--date YYYY-MM-DD] [--skip-migrations] [--dry-run] [--force]",
      "",
      "Seeds the Sillage D1 database with current-design sample data.",
      "The seed clears D1 business tables before inserting records, summaries, and ask conversations.",
    ].join("\n"),
  );
}

function parseArgs(argv) {
  const options = {
    mode: null,
    date: new Date().toISOString().slice(0, 10),
    skipMigrations: false,
    dryRun: false,
    force: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--local" || arg === "--remote") {
      options.mode = arg.slice(2);
    } else if (arg === "--date") {
      const next = argv[index + 1];
      if (!next) {
        throw new Error("--date requires a YYYY-MM-DD value");
      }
      options.date = next;
      index += 1;
    } else if (arg === "--skip-migrations") {
      options.skipMigrations = true;
    } else if (arg === "--dry-run") {
      options.dryRun = true;
    } else if (arg === "--force") {
      options.force = true;
    } else if (arg === "--help" || arg === "-h") {
      usage();
      process.exit(0);
    } else {
      throw new Error(`unknown argument: ${arg}`);
    }
  }
  if (!options.mode && !options.dryRun) {
    throw new Error("choose exactly one target: --local or --remote");
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(options.date)) {
    throw new Error("--date must use YYYY-MM-DD");
  }
  return options;
}

function addDays(dateISO, days) {
  const date = new Date(`${dateISO}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function withYear(dateISO, year) {
  return `${year}-${dateISO.slice(5, 10)}`;
}

function timestamp(dateISO, time = "21:00") {
  return Date.parse(`${dateISO}T${time}:00.000Z`);
}

function uuidv7For(ms, seed) {
  const bytes = new Uint8Array(16);
  const ts = BigInt(Math.max(0, Math.floor(ms)));
  bytes[0] = Number((ts >> 40n) & 0xffn);
  bytes[1] = Number((ts >> 32n) & 0xffn);
  bytes[2] = Number((ts >> 24n) & 0xffn);
  bytes[3] = Number((ts >> 16n) & 0xffn);
  bytes[4] = Number((ts >> 8n) & 0xffn);
  bytes[5] = Number(ts & 0xffn);
  const digest = createHash("sha256").update(seed).digest();
  bytes.set(digest.subarray(0, 10), 6);
  bytes[6] = (bytes[6] & 0x0f) | 0x70;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function sqlString(value) {
  if (value === null || value === undefined) {
    return "NULL";
  }
  return `'${String(value).replaceAll("'", "''")}'`;
}

function sqlNumber(value) {
  return value === null || value === undefined ? "NULL" : String(value);
}

function values(columns, rows) {
  return rows
    .map(
      (row) =>
        `(${columns
          .map((column) => {
            const value = row[column];
            return typeof value === "number" ? sqlNumber(value) : sqlString(value);
          })
          .join(", ")})`,
    )
    .join(",\n");
}

function weekRange(dateISO) {
  const date = new Date(`${dateISO}T00:00:00.000Z`);
  const daysSinceMonday = (date.getUTCDay() + 6) % 7;
  const start = new Date(date);
  start.setUTCDate(date.getUTCDate() - daysSinceMonday);
  const end = new Date(start);
  end.setUTCDate(start.getUTCDate() + 6);
  return {
    startDate: start.toISOString().slice(0, 10),
    endDate: end.toISOString().slice(0, 10),
  };
}

function monthRange(dateISO) {
  const year = Number(dateISO.slice(0, 4));
  const month = Number(dateISO.slice(5, 7));
  const end = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return {
    startDate: `${year}-${String(month).padStart(2, "0")}-01`,
    endDate: `${year}-${String(month).padStart(2, "0")}-${String(end).padStart(2, "0")}`,
  };
}

function createEntryFactory(anchorDate) {
  const entryRows = [];
  const aiRows = [];
  const revisionRows = [];
  const byKey = new Map();

  function addEntry(input) {
    const createdAt = timestamp(input.createdDate ?? input.date, input.createdTime ?? "21:00");
    const updatedAt = input.updatedDate
      ? timestamp(input.updatedDate, input.updatedTime ?? "21:30")
      : input.updatedTime
        ? timestamp(input.createdDate ?? input.date, input.updatedTime)
        : createdAt;
    const deletedAt = input.deletedDate
      ? timestamp(input.deletedDate, input.deletedTime ?? "22:00")
      : null;
    const id = uuidv7For(createdAt, `entry:${input.key}`);
    const revisionBodies = input.revisions ?? [input.body];
    const version = input.version ?? revisionBodies.length;
    entryRows.push({
      id,
      entry_date: input.date,
      body: input.body,
      version,
      created_at: createdAt,
      updated_at: updatedAt,
      deleted_at: deletedAt,
    });
    revisionBodies.forEach((body, index) => {
      const revisionCreatedAt =
        index === revisionBodies.length - 1
          ? updatedAt
          : createdAt + (index + 1) * 10 * 60 * 1000;
      revisionRows.push({
        id: uuidv7For(revisionCreatedAt, `revision:${input.key}:${index + 1}`),
        entry_id: id,
        version: index + 1,
        entry_date: input.date,
        body,
        created_at: revisionCreatedAt,
      });
    });
    if (input.summary) {
      const generatedAt = updatedAt + 4 * 60 * 1000;
      aiRows.push({
        entry_id: id,
        summary: input.summary,
        sentiment: input.sentiment ?? "平稳",
        model: MODEL,
        duration_ms: input.durationMs ?? 1200,
        generation_count: input.generationCount ?? 1,
        generated_at: generatedAt,
      });
    }
    byKey.set(input.key, { ...input, id, createdAt, updatedAt });
  }

  const d = (days) => addDays(anchorDate, days);
  const today = anchorDate;
  const lastYearToday = withYear(anchorDate, Number(anchorDate.slice(0, 4)) - 1);
  const twoYearsAgoToday = withYear(anchorDate, Number(anchorDate.slice(0, 4)) - 2);

  addEntry({
    key: "today-seed-data",
    date: today,
    createdTime: "08:40",
    updatedTime: "10:05",
    body: `早上把本地开发数据重新整理了一遍，目标是让首页、历史、日历、问答和总结都有能看的样本。

这次没有再补天气、地点、标签这些旧字段，所有用户内容都放在正文里。顺手记下几个要验证的点：搜索“预算”“跑步”“客户访谈”都应该能命中；那年今日要出现；问答侧栏要有置顶和归档会话。`,
    revisions: [
      "早上开始整理样本数据，先列了需要覆盖的页面。",
      `早上把本地开发数据重新整理了一遍，目标是让首页、历史、日历、问答和总结都有能看的样本。

这次没有再补天气、地点、标签这些旧字段，所有用户内容都放在正文里。顺手记下几个要验证的点：搜索“预算”“跑步”“客户访谈”都应该能命中；那年今日要出现；问答侧栏要有置顶和归档会话。`,
    ],
    summary: "整理开发样本数据，确认当前记录模型只保留日期与正文，并规划搜索、问答和回顾总结的验证点。",
    sentiment: "专注",
    generationCount: 2,
  });

  addEntry({
    key: "today-family-call",
    date: today,
    createdTime: "21:30",
    body: `晚上和家里视频了半小时。妈妈说院子里的茉莉开得很好，爸爸又开始研究怎么把旧相册扫描成电子版。

聊完之后把厨房台面收拾干净，煮了一小锅绿豆汤。今天的状态不算轻松，但有一种事情正在回到秩序里的感觉。`,
    summary: "晚上与家人视频，整理厨房并煮绿豆汤，忙碌之后获得一点秩序感。",
    sentiment: "温和",
  });

  addEntry({
    key: "customer-interviews",
    date: d(-1),
    createdTime: "20:50",
    body: `今天做了四个客户访谈。最有价值的一句话是：“我不是不想记录，是回头找不到以前写过什么。”

记下来的线索：
- 搜索要能容忍很口语的关键词。
- 总结不能写得像报告，要像有人真的读过这些记录。
- 用户愿意补充上下文，但不愿意先填一堆字段。`,
    summary: "四个客户访谈都指向同一个问题：记录容易沉下去，检索和总结要足够自然，不能依赖复杂字段。",
    sentiment: "有收获",
  });

  addEntry({
    key: "running-interval",
    date: d(-2),
    createdTime: "07:20",
    body: `晨跑 4.8 公里，最后一公里做了三组短间歇。左膝没有明显不舒服，但下楼梯时还是有一点紧。

回来做了拉伸，把泡沫轴放在客厅显眼处，提醒自己晚上不要直接倒在沙发上。跑步这件事还是要慢一点，宁可少跑，也不要又养伤两周。`,
    summary: "完成一次短间歇晨跑，左膝整体稳定但仍需控制强度和保持拉伸。",
    sentiment: "谨慎积极",
  });

  addEntry({
    key: "budget-review",
    date: d(-3),
    createdTime: "22:10",
    body: `晚上把六月预算重新看了一遍。外卖比预期多了 420，交通少了 180，订阅服务里有两个已经三个月没打开。

决定：
1. 取消旧的图片素材订阅。
2. 七月给“学习”和“运动恢复”留固定预算。
3. 大件支出先放进愿望清单，过 14 天再决定。`,
    summary: "复盘六月预算，发现外卖和订阅偏高，决定取消闲置订阅并为学习、运动恢复预留预算。",
    sentiment: "克制",
  });

  addEntry({
    key: "product-review",
    date: d(-4),
    createdTime: "19:45",
    body: `下午开产品评审，大家终于把“记录入口要极简”这件事说清楚了。以前总忍不住想加字段，但真实使用时，字段越多越像填表。

我负责把几个边缘状态列出来：空记录、重复提交、编辑冲突、AI 失败、软删除恢复。评审结束后脑子很累，但方向更清楚。`,
    summary: "产品评审确认记录入口保持极简，并列出空记录、编辑冲突、AI 失败、软删除等边界状态。",
    sentiment: "清晰但疲惫",
  });

  addEntry({
    key: "kitchen-reset",
    date: d(-5),
    createdTime: "16:35",
    body: `周日下午做了一次厨房重置。冰箱里扔掉两盒过期酱料，擦了抽屉轨道，把常用香料挪到灶台右侧。

晚饭是番茄牛腩和清炒空心菜。吃完没有立刻刷手机，而是把下周三顿便当的大概组合写在便签上。家务如果拆小一点，其实没有那么可怕。`,
    summary: "整理厨房和冰箱，做饭并规划下周便当，感受到家务拆小后更容易执行。",
    sentiment: "踏实",
  });

  addEntry({
    key: "photo-walk",
    date: d(-6),
    createdTime: "18:10",
    body: `傍晚带相机去河边走了一圈。雨后云层很低，桥洞下面有一片反光，拍到了几张喜欢的照片。

没有刻意发朋友圈，只是把照片导进电脑，给文件夹命名为“低云和河”。这种不以产出为目的的摄影，反而让我更想继续。`,
    summary: "雨后河边散步摄影，享受不急着发布的创作过程。",
    sentiment: "放松",
  });

  addEntry({
    key: "requirements-thread",
    date: d(-7),
    createdTime: "23:10",
    body: `今天把需求线程重新梳理了一遍。真正紧急的只有三件：同步游标、问答停止、备份列表。

其余想法先放到“以后再说”。这四个字看起来消极，其实是在保护注意力。晚上十一点之后没有再开新的代码文件，只写了明天第一步。`,
    summary: "梳理需求线程并缩小优先级，只保留同步、问答停止和备份列表三个重点。",
    sentiment: "收束",
  });

  addEntry({
    key: "dentist",
    date: d(-8),
    createdTime: "12:30",
    body: `中午去洗牙。医生说右下那颗智齿附近还是容易藏东西，建议先观察，不急着处理。

回来的路上买了新的牙线棒。健康类的事情经常不是靠一次大的决心，而是把工具放到够顺手的位置。`,
    summary: "洗牙后确认智齿暂时观察，买牙线棒并意识到健康习惯需要顺手的工具。",
    sentiment: "平稳",
  });

  addEntry({
    key: "reading-night",
    date: d(-9),
    createdTime: "22:40",
    body: `睡前读完《始于极限》的两个章节。里面关于“不要急着把复杂经验压成一句正确答案”的说法很打动我。

想到自己最近写总结时也有这个问题：太快下结论，反而把细节弄丢。明天试着在记录里多留一点不确定。`,
    summary: "阅读后反思写作和总结中过快下结论的问题，提醒自己保留复杂细节。",
    sentiment: "思考",
  });

  addEntry({
    key: "weekday-cooking",
    date: d(-10),
    createdTime: "20:15",
    body: `今天下班后没有点外卖，做了鸡蛋豆腐、蒜蓉油麦菜和一小碗紫菜汤。

做饭用了 42 分钟，比等外卖久一点，但吃完身体舒服很多。把“工作日做饭”理解成恢复，而不是任务，心态会轻一点。`,
    summary: "工作日晚餐自己做饭，虽然耗时更久，但带来更好的身体感受。",
    sentiment: "满足",
  });

  addEntry({
    key: "travel-packing",
    date: d(-11),
    createdTime: "21:05",
    body: `为下个月的短途旅行列了打包清单：轻便雨衣、备用数据线、常用药、一本薄书、相机电池。

以前出门总是临时乱塞，这次想少带一点。旅行最需要的不是把家搬过去，而是给路上留出余地。`,
    summary: "为短途旅行提前列清单，决定轻装出门，给行程留余地。",
    sentiment: "期待",
  });

  addEntry({
    key: "family-dinner",
    date: d(-12),
    createdTime: "22:20",
    body: `晚上和表姐一家吃饭。小朋友开始学写自己的名字，一笔一画特别认真。

饭桌上聊到每个人对“稳定”的理解。有人觉得稳定是房子和工作，有人觉得是遇到事能睡得着。我大概更接近后者。`,
    summary: "家庭晚餐中聊到稳定感，意识到自己更重视遇事还能安睡的能力。",
    sentiment: "亲近",
  });

  addEntry({
    key: "anxious-night",
    date: d(-13),
    createdTime: "23:55",
    body: `凌晨前有点焦虑，脑子一直在排明天要做的事。后来把所有担心写成清单，发现真正需要处理的只有两件，其他只是噪音。

泡脚十分钟，关灯前没有再看消息。虽然睡得不算早，但至少没有被焦虑牵着走到两点。`,
    summary: "夜里焦虑时把担心写成清单，识别出真正事项并用泡脚帮助自己停下来。",
    sentiment: "焦虑后缓和",
  });

  addEntry({
    key: "backlog-pruning",
    date: d(-14),
    createdTime: "18:25",
    body: `下午把积压列表删掉了三分之一。很多“也许有用”的想法，其实已经没有上下文了。

保留的事项都补了一句为什么要做。写不出理由的，基本就不值得继续占位置。`,
    summary: "清理积压事项，删除缺少上下文的想法，并为保留事项补充动机。",
    sentiment: "轻一点",
  });

  addEntry({
    key: "old-colleague-coffee",
    date: d(-15),
    createdTime: "17:30",
    body: `和以前同事喝咖啡。他现在转去做教育工具，聊起用户研究时眼睛发亮。

我们都承认，真正难的不是把功能做出来，而是坚持把问题问清楚。回家路上想起刚入行时那种笨拙但兴奋的状态。`,
    summary: "与旧同事聊教育工具和用户研究，重新想起入行早期的兴奋感。",
    sentiment: "被点亮",
  });

  addEntry({
    key: "gym-strength",
    date: d(-16),
    createdTime: "21:15",
    body: `力量训练：深蹲 5 组、硬拉 4 组、划船 4 组。重量没有加，重点是动作干净。

教练提醒我不要用肩膀抢动作。这个提醒也适用于工作：不是所有事情都要靠硬顶。`,
    summary: "完成一次力量训练，重点放在动作质量，并联想到工作中不必所有事都硬顶。",
    sentiment: "稳定",
  });

  addEntry({
    key: "rain-commute",
    date: d(-17),
    createdTime: "19:05",
    body: `下班时突然大雨，地铁口排队的人绕了两圈。原本有点烦，后来听完了一期关于城市树木的播客。

雨停后空气里有桂花和泥土的味道。通勤没有变短，但心情没有被它完全拿走。`,
    summary: "雨天通勤拥挤，但通过听播客和感受雨后气味保持了心情。",
    sentiment: "被安抚",
  });

  addEntry({
    key: "learning-notes",
    date: d(-18),
    createdTime: "22:00",
    body: `晚上学了一小时数据库索引。把 B-tree、覆盖索引、FTS 这几个概念重新画了一遍。

以前总觉得自己应该一次看懂，现在会允许自己第二天再回来补。学习不是证明聪明，是持续修正理解。`,
    summary: "复习数据库索引和 FTS，接受学习需要多次回看与修正。",
    sentiment: "耐心",
  });

  addEntry({
    key: "plants",
    date: d(-20),
    createdTime: "09:10",
    body: `给阳台植物换了位置。薄荷长得太快，已经挤到迷迭香；琴叶榕的新叶边缘有点干，先挪到散射光更好的地方。

植物很诚实，照顾得乱七八糟时，它们不会假装没事。`,
    summary: "调整阳台植物位置，观察薄荷和琴叶榕状态，提醒自己照顾需要持续。",
    sentiment: "观察",
  });

  addEntry({
    key: "sleep-reset",
    date: d(-22),
    createdTime: "08:05",
    body: `昨晚十点半就睡了，早上醒来明显清醒。过去一周一直觉得效率低，可能不是方法问题，只是睡眠债太重。

今天先不安排晚间学习，把恢复当成正式事项。`,
    summary: "早睡后状态明显改善，决定把恢复和睡眠当作正式事项。",
    sentiment: "恢复",
  });

  addEntry({
    key: "remote-work",
    date: d(-25),
    createdTime: "18:45",
    body: `在家办公的一天。上午效率很好，下午被楼上装修打断了几次。

试了一个办法：把需要深度思考的事放到上午，下午处理回复、整理和测试。不是每天都能完美专注，但可以顺着环境重新排布。`,
    summary: "在家办公受装修影响，调整为上午深度工作、下午处理轻任务。",
    sentiment: "务实",
  });

  addEntry({
    key: "invoice-tax",
    date: d(-28),
    createdTime: "20:35",
    body: `把发票和报销单据整理完。最麻烦的是几笔跨月的小额支出，幸好当时在备注里写了用途。

以后每周五花十分钟清一次，不要等到月底像考古。`,
    summary: "整理发票和报销，意识到及时备注和每周清理能减少月底压力。",
    sentiment: "松一口气",
  });

  addEntry({
    key: "month-close",
    date: d(-31),
    createdTime: "22:25",
    body: `五月结束。这个月最好的变化是恢复了晨间散步，最糟的是晚上太容易被短视频带走。

下个月只设三个目标：每周两次跑步；工作日少点两次外卖；把读书笔记从收藏夹里搬出来。`,
    summary: "五月复盘：晨间散步恢复，短视频消耗偏多；六月目标聚焦跑步、外卖和读书笔记。",
    sentiment: "复盘",
  });

  addEntry({
    key: "camping",
    date: d(-35),
    createdTime: "21:40",
    body: `周末去近郊露营。夜里风很大，帐篷一直响，但早上五点多看到雾从水面升起来，觉得折腾也值得。

带回来的经验：头灯比氛围灯重要；湿纸巾永远不嫌多；不要在睡前喝太多茶。`,
    summary: "近郊露营虽然夜里风大，但清晨景色很好，也总结了装备经验。",
    sentiment: "愉快",
  });

  addEntry({
    key: "doctor-check",
    date: d(-42),
    createdTime: "11:50",
    body: `体检报告出来，维生素 D 还是偏低，其他指标基本正常。医生建议增加日照和力量训练，暂时不用额外紧张。

我把补剂放到早餐旁边，能不能坚持就看这个动作够不够顺手。`,
    summary: "体检整体正常但维生素 D 偏低，计划通过日照、力量训练和顺手放置补剂改善。",
    sentiment: "安心",
  });

  addEntry({
    key: "financial-plan",
    date: d(-49),
    createdTime: "22:05",
    body: `重新分配了储蓄账户：应急金、旅行、学习、年度保险分开。以前所有钱混在一起，看余额会误判自己很宽裕。

这不是为了变得更会理财，而是为了少一点月底才发现的惊讶。`,
    summary: "把储蓄账户按应急、旅行、学习、保险拆分，减少对余额的误判。",
    sentiment: "清楚",
  });

  addEntry({
    key: "museum",
    date: d(-58),
    createdTime: "18:00",
    body: `去看了城市影像展。最喜欢一组老居民楼的楼梯照片，磨损的扶手、贴歪的通知、半开的窗户，都比宏大的天际线更像一座城市。

出来后在路边吃了一碗热干面。一个人的周末也可以很完整。`,
    summary: "参观城市影像展，被老居民楼细节打动，感到独处周末也很完整。",
    sentiment: "充实",
  });

  addEntry({
    key: "review-talk",
    date: d(-67),
    createdTime: "19:30",
    body: `和主管做季度回顾。反馈比想象中具体：优点是能把复杂问题拆开，风险是有时太晚暴露不确定。

给自己的提醒：卡住超过半天就把问题写出来，不要等到已经绕了三圈才求助。`,
    summary: "季度回顾收到具体反馈：拆解能力强，但需要更早暴露不确定和求助。",
    sentiment: "认真",
  });

  addEntry({
    key: "hiking",
    date: d(-80),
    createdTime: "20:20",
    body: `徒步 12 公里，爬升不高但路很碎。前半程一直想拍照，后半程只想专心走路。

山里有一段松针路，脚步声特别轻。回到车站时腿酸，但心里很干净。`,
    summary: "完成一次 12 公里徒步，后半程放下拍照专心走路，身心都被清理了一遍。",
    sentiment: "舒展",
  });

  addEntry({
    key: "disagreement",
    date: d(-96),
    createdTime: "23:00",
    body: `今天和朋友有一点争执。起因很小，但背后其实是我们对“及时回复”的期待不同。

晚上重新发了一条消息，把自己的感受说清楚，也承认白天语气有点冲。关系里的修复经常比争对错更重要。`,
    summary: "与朋友因回复期待发生争执，晚上主动解释和修复关系。",
    sentiment: "反省",
  });

  addEntry({
    key: "workshop",
    date: d(-118),
    createdTime: "17:45",
    body: `参加了一场写作工作坊。练习是用五分钟描述一个常见物件，我写了桌上的旧马克杯。

老师说“具体不是堆细节，而是让读者知道你为什么看见它”。这句话可以贴到我的编辑器旁边。`,
    summary: "写作工作坊提醒自己，具体描写要服务于为什么看见，而不是堆砌细节。",
    sentiment: "被启发",
  });

  addEntry({
    key: "gift",
    date: d(-132),
    createdTime: "21:25",
    body: `给朋友挑生日礼物，最后选了一本关于植物染的书和一包蓝晒纸。

我喜欢送能让人开始做点什么的东西，而不是只能摆着看的东西。希望她会喜欢。`,
    summary: "为朋友挑选植物染书和蓝晒纸，偏好送能开启行动的礼物。",
    sentiment: "期待",
  });

  addEntry({
    key: "spring-prep",
    date: d(-154),
    createdTime: "18:55",
    body: `年前大扫除。书架清出两袋不再看的杂志，衣柜里有几件已经不合身但一直舍不得扔的衣服。

处理旧东西时最难的不是丢弃物品，是承认某个阶段已经结束。`,
    summary: "年前大扫除清理杂志和旧衣服，也意识到丢弃意味着承认阶段结束。",
    sentiment: "告别",
  });

  addEntry({
    key: "new-year-reset",
    date: d(-177),
    createdTime: "09:30",
    body: `新年第一天没有写宏大的计划，只写了三个希望经常发生的画面：早上有光，晚上有饭，周末有路。

如果这一年能让这些画面多出现几次，就已经很好。`,
    summary: "新年第一天用三个日常画面代替宏大计划：早上有光、晚上有饭、周末有路。",
    sentiment: "安静",
  });

  addEntry({
    key: "year-end-2025",
    date: "2025-12-31",
    createdTime: "23:20",
    body: `2025 年最后一天。没有想象中的仪式感，只是在家洗了床单，换了新的台历。

这一年最大的进步是更能承认自己精力有限。以前总想同时抓住很多东西，现在更愿意认真放下。`,
    summary: "2025 年末复盘，最大的变化是更能承认精力有限并认真放下。",
    sentiment: "沉静",
  });

  addEntry({
    key: "moving",
    date: "2025-11-20",
    createdTime: "20:45",
    body: `搬家第二天。新房子的早晨光线很好，但热水器声音有点大。

今天只完成了三件事：装好书桌、找到常用药、把第一顿饭做出来。一个地方开始像家，可能就是从能坐下来吃饭开始。`,
    summary: "搬家后整理书桌、药品并做第一顿饭，新空间开始有家的感觉。",
    sentiment: "安顿",
  });

  addEntry({
    key: "hangzhou-trip",
    date: "2025-10-05",
    createdTime: "22:30",
    body: `杭州短途旅行的第二天。上午在九溪走了很久，下午找了家小店喝龙井。

最喜欢的不是景点，是在公交车上看见一位阿姨抱着一大束桂花枝。那一刻觉得假期真正开始了。`,
    summary: "杭州旅行中在九溪散步、喝龙井，公交车上的桂花枝成为最有记忆点的画面。",
    sentiment: "轻快",
  });

  addEntry({
    key: "new-routine",
    date: "2025-09-01",
    createdTime: "07:50",
    body: `九月第一天，重新安排早晨：起床后先喝水，十分钟拉伸，再打开电脑。

不追求完美晨间流程，只希望不要一睁眼就被消息推走。`,
    summary: "九月开始调整晨间流程，目标是减少醒来后立刻被消息带走。",
    sentiment: "清爽",
  });

  addEntry({
    key: "family-health",
    date: "2025-08-16",
    createdTime: "21:10",
    body: `陪家人复诊。结果比上次好，医生说继续按现在的节奏来。

医院走廊里人很多，大家都在等一个确定的答案。回家路上买了桃子，想着能做的事有时就是把晚饭吃好。`,
    summary: "陪家人复诊，结果向好；在医院的不确定里提醒自己先把能做的小事做好。",
    sentiment: "松动",
  });

  addEntry({
    key: "beta-deploy",
    date: "2025-07-10",
    createdTime: "19:40",
    body: `第一次把测试版部署出去。看到页面在手机上打开的那一刻很开心，也马上发现两个小问题：按钮文字太长，深色模式下边框太重。

真实设备永远比想象更诚实。`,
    summary: "测试版首次部署后在手机上验证，发现按钮文字和深色模式边框问题。",
    sentiment: "兴奋",
  });

  addEntry({
    key: "on-this-day-2025",
    date: lastYearToday,
    createdTime: "20:10",
    body: `去年的今天，下班后绕路去了旧书店。买到一本二手的《设计中的设计》，扉页上有前主人写的日期。

当时还不知道一年后会把很多记录重新整理成现在这个样子。时间有时会悄悄把线头接上。`,
    summary: "去年的今天在旧书店买书，如今回看觉得记录和时间悄悄接上了线。",
    sentiment: "怀旧",
  });

  addEntry({
    key: "on-this-day-2024",
    date: twoYearsAgoToday,
    createdTime: "21:00",
    body: `两年前的今天，第一次认真尝试每天写一点。那时写得很短，常常只有一句“今天很累”。

现在看，那些短句也不是没用。它们像路标，至少证明自己曾经经过那里。`,
    summary: "两年前开始尝试每日记录，意识到短句也能成为回看的路标。",
    sentiment: "温柔",
  });

  addEntry({
    key: "winter-reading",
    date: "2024-12-03",
    createdTime: "22:15",
    body: `冬天适合读长一点的书。今晚读到一句：“一个人真正拥有的，是他反复返回的地方。”

我反复返回的地方大概是厨房、河边、书桌，还有这些断断续续的记录。`,
    summary: "冬夜读书时想到自己反复返回的地方：厨房、河边、书桌和记录。",
    sentiment: "安静",
  });

  addEntry({
    key: "first-run",
    date: "2024-03-18",
    createdTime: "08:30",
    body: `第一次用手表记录跑步，只跑了 2 公里，配速很慢，但按下保存时还是很有成就感。

后来才知道，开始一件事时最重要的不是漂亮，而是愿意留下第一条笨拙的数据。`,
    summary: "第一次用手表记录跑步，虽然慢且短，但留下了开始的证据。",
    sentiment: "新鲜",
  });

  addEntry({
    key: "retro-note",
    date: d(-85),
    createdDate: today,
    createdTime: "12:05",
    body: `补记：四月初那次项目复盘其实很关键。当时只觉得混乱，现在回头看，它逼着我们把“谁来决定”和“什么时候算完成”说清楚。

这条是补写的，所以创建时间和归属日期不一样，正好用来检查界面里的“归属日期”显示。`,
    summary: "补写四月项目复盘，强调决策权和完成定义的重要性，也用于测试归属日期显示。",
    sentiment: "回看",
  });

  addEntry({
    key: "deleted-sample",
    date: d(-54),
    createdTime: "10:00",
    updatedTime: "10:20",
    deletedDate: d(-53),
    deletedTime: "09:00",
    body: "这条记录用于测试软删除墓碑。正常列表和全文搜索里不应该出现它，但同步接口应该能看见 deletedAt。",
    summary: "软删除样本记录，用于验证列表、搜索和同步墓碑行为。",
    sentiment: "测试",
  });

  return { entryRows, aiRows, revisionRows, byKey };
}

function source(kind, id, title, label, href) {
  return { id, title, label, href, kind };
}

function createSummaries(anchorDate, entriesByKey) {
  const rows = [];
  const byKey = new Map();
  const now = timestamp(anchorDate, "12:30");
  const week = weekRange(anchorDate);
  const month = monthRange(anchorDate);

  function ids(keys) {
    return keys.map((key) => entriesByKey.get(key)?.id).filter(Boolean);
  }

  function addSummary(input, index) {
    const generatedAt = input.generatedAt ?? now + index * 60 * 1000;
    const id = uuidv7For(generatedAt, `summary:${input.key}`);
    rows.push({
      id,
      scope: input.scope,
      period_type: input.periodType ?? null,
      start_date: input.startDate,
      end_date: input.endDate,
      style: input.style,
      filter: input.filter ? JSON.stringify(input.filter) : null,
      title: input.title,
      content: input.content,
      model: MODEL,
      source_entry_ids: JSON.stringify(input.sourceEntryIds),
      trigger: input.trigger ?? "manual",
      generated_at: generatedAt,
      created_at: generatedAt,
      updated_at: input.deletedAt ? input.deletedAt : generatedAt,
      deleted_at: input.deletedAt ?? null,
    });
    byKey.set(input.key, { ...input, id });
  }

  addSummary(
    {
      key: "current-week",
      scope: "period",
      periodType: "week",
      startDate: week.startDate,
      endDate: week.endDate,
      style: "structured",
      title: "本周：把复杂度收回来",
      sourceEntryIds: ids([
        "today-seed-data",
        "today-family-call",
        "customer-interviews",
        "running-interval",
        "budget-review",
        "product-review",
        "kitchen-reset",
      ]),
      content: `## 主要线索

- 工作上在收束：样本数据、客户访谈和产品评审都指向同一件事，当前设计要少一点字段，多一点可回看的上下文。
- 生活上在恢复秩序：厨房重置、家庭视频、跑步和预算复盘都不是大动作，但都在减少失控感。
- 需要继续关注左膝和睡眠，不要把“能推进”误读成“可以透支”。`,
    },
    1,
  );

  addSummary(
    {
      key: "current-month",
      scope: "period",
      periodType: "month",
      startDate: month.startDate,
      endDate: month.endDate,
      style: "brief",
      title: "六月：轻装、修复、少填表",
      sourceEntryIds: ids([
        "today-seed-data",
        "customer-interviews",
        "running-interval",
        "budget-review",
        "product-review",
        "kitchen-reset",
        "requirements-thread",
        "dentist",
        "reading-night",
        "weekday-cooking",
        "travel-packing",
        "family-dinner",
        "anxious-night",
        "backlog-pruning",
        "gym-strength",
        "rain-commute",
        "learning-notes",
        "sleep-reset",
      ]),
      content:
        "六月的关键词是“减负”。记录入口、积压列表、旅行打包和预算都在做同一件事：把不必要的东西移开，让真正重要的事情更容易发生。健康方面，跑步、力量训练、牙科和睡眠都需要稳定的小动作，而不是一次性冲刺。",
    },
    2,
  );

  addSummary(
    {
      key: "health-running",
      scope: "topic",
      periodType: "custom",
      startDate: "2024-03-18",
      endDate: anchorDate,
      style: "structured",
      filter: { keyword: "跑步 健康 体检 睡眠" },
      title: "健康线索：慢一点，但别停",
      sourceEntryIds: ids([
        "running-interval",
        "dentist",
        "gym-strength",
        "sleep-reset",
        "doctor-check",
        "family-health",
        "first-run",
      ]),
      content: `## 趋势

- 身体反馈越来越被当成正式信息：左膝、睡眠债、维生素 D、牙齿清洁都被记录下来。
- 运动策略从“证明自己能跑”转向“可持续地恢复和加强”。

## 建议

继续保留低门槛动作：泡沫轴放客厅、补剂放早餐旁、每周两次低强度跑步。`,
    },
    3,
  );

  addSummary(
    {
      key: "budget-home",
      scope: "topic",
      periodType: "custom",
      startDate: addDays(anchorDate, -60),
      endDate: anchorDate,
      style: "structured",
      filter: { keyword: "预算 家务 发票 搬家 厨房" },
      title: "预算和家务：少一点月底惊讶",
      sourceEntryIds: ids([
        "budget-review",
        "kitchen-reset",
        "invoice-tax",
        "financial-plan",
        "moving",
        "weekday-cooking",
      ]),
      content: `## 已经有效的做法

- 把账户和预算拆分，减少对余额的误判。
- 每周清理发票，不等月底考古。
- 用做饭、厨房重置和便当计划降低外卖频率。

## 风险

订阅服务和临时外卖仍然容易悄悄变成固定开支，需要继续每月复盘。`,
    },
    4,
  );

  addSummary(
    {
      key: "travel-outdoor",
      scope: "topic",
      periodType: "custom",
      startDate: "2025-10-05",
      endDate: anchorDate,
      style: "narrative",
      filter: { keyword: "旅行 露营 徒步 摄影" },
      title: "路上的余地",
      sourceEntryIds: ids([
        "travel-packing",
        "photo-walk",
        "camping",
        "hiking",
        "hangzhou-trip",
        "museum",
      ]),
      content:
        "这些记录里的出门并不追求打卡。河边的低云、九溪公交上的桂花枝、露营清晨的雾、徒步时松针路上的脚步声，都说明真正留下来的往往是行程表之外的细节。下一次旅行可以继续轻装，把空间留给偶然发生的画面。",
    },
    5,
  );

  addSummary(
    {
      key: "year-2025",
      scope: "period",
      periodType: "year",
      startDate: "2025-01-01",
      endDate: "2025-12-31",
      style: "narrative",
      title: "2025：认真放下，也认真开始",
      sourceEntryIds: ids([
        "year-end-2025",
        "moving",
        "hangzhou-trip",
        "new-routine",
        "family-health",
        "beta-deploy",
        "on-this-day-2025",
      ]),
      content:
        "2025 年的记录里有几次明显的转折：测试版第一次部署，搬家后重新建立日常，家人复诊带来的松动，以及年末对精力有限的承认。这一年不是把所有事情都抓紧，而是学会判断哪些值得继续，哪些可以认真放下。",
      trigger: "scheduled",
    },
    6,
  );

  addSummary(
    {
      key: "deleted-summary",
      scope: "period",
      periodType: "day",
      startDate: addDays(anchorDate, -54),
      endDate: addDays(anchorDate, -54),
      style: "brief",
      title: "已删除总结样本",
      sourceEntryIds: ids(["deleted-sample"]),
      content: "这条总结用于测试软删除，不应该出现在常规总结列表中。",
      deletedAt: timestamp(addDays(anchorDate, -53), "10:00"),
    },
    7,
  );

  return { summaryRows: rows, summariesByKey: byKey };
}

function createAskData(anchorDate, entriesByKey, summariesByKey) {
  const conversationRows = [];
  const messageRows = [];
  const now = timestamp(anchorDate, "13:30");

  function entrySource(key) {
    const entry = entriesByKey.get(key);
    const title = entry.body.replace(/\s+/g, " ").slice(0, 28);
    return source("entry", entry.id, title, `${entry.date} · ${title}`, `/entries/${entry.id}`);
  }

  function summarySource(key) {
    const summary = summariesByKey.get(key);
    return source(
      "summary",
      summary.id,
      summary.title,
      `AI 总结 · ${summary.title}`,
      `/ask#summary-${summary.id}`,
    );
  }

  function addConversation(input, index) {
    const createdAt = input.createdAt ?? now + index * 20 * 60 * 1000;
    const conversationId = uuidv7For(createdAt, `ask-conversation:${input.key}`);
    let previousId = null;
    let lastMessageAt = createdAt;
    const messageIds = new Map();
    input.messages.forEach((message, messageIndex) => {
      const messageAt = createdAt + messageIndex * 90 * 1000;
      const id = uuidv7For(messageAt, `ask-message:${input.key}:${message.key}`);
      const parentId = message.parentKey ? messageIds.get(message.parentKey) : previousId;
      const forkOfId = message.forkOfKey ? messageIds.get(message.forkOfKey) : null;
      messageRows.push({
        id,
        conversation_id: conversationId,
        parent_id: parentId ?? null,
        fork_of_id: forkOfId ?? null,
        role: message.role,
        content: message.content,
        status: message.status ?? "completed",
        sources: message.sources ? JSON.stringify(message.sources) : null,
        source_types: message.sourceTypes ?? input.sourceTypes,
        model: message.role === "assistant" ? MODEL : null,
        duration_ms: message.role === "assistant" ? (message.durationMs ?? 1400) : null,
        created_at: messageAt,
        updated_at: messageAt,
      });
      messageIds.set(message.key, id);
      previousId = id;
      lastMessageAt = messageAt;
    });
    const headMessageId = messageIds.get(input.headKey ?? input.messages.at(-1).key);
    conversationRows.push({
      id: conversationId,
      title: input.title,
      source_types: input.sourceTypes,
      head_message_id: headMessageId,
      pinned_at: input.pinned ? createdAt + 30 * 1000 : null,
      archived_at: input.archived ? lastMessageAt + 30 * 1000 : null,
      created_at: createdAt,
      updated_at: lastMessageAt,
    });
  }

  addConversation(
    {
      key: "weekly-focus",
      title: "这周工作和生活的重点是什么？",
      sourceTypes: SOURCE_TYPES.all,
      pinned: true,
      messages: [
        {
          key: "u1",
          role: "user",
          content: "这周工作和生活的重点是什么？请只根据记录回答。",
        },
        {
          key: "a1",
          role: "assistant",
          content: `这周的重点可以归成三类：

1. 工作上在确认当前设计：客户访谈、产品评审和样本数据整理都强调“少字段、重上下文”。
2. 生活上在恢复秩序：厨房、预算、家庭视频和跑步都在把日常重新放稳。
3. 风险是透支：左膝、睡眠和焦虑记录都提醒你不要把推进速度当成健康状态。`,
          sources: [
            summarySource("current-week"),
            entrySource("customer-interviews"),
            entrySource("running-interval"),
            entrySource("budget-review"),
          ],
          durationMs: 1680,
        },
      ],
    },
    1,
  );

  addConversation(
    {
      key: "running-health",
      title: "最近关于跑步和体检有什么线索？",
      sourceTypes: SOURCE_TYPES.entryAi,
      messages: [
        {
          key: "u1",
          role: "user",
          content: "最近关于跑步、体检、睡眠有什么值得注意的线索？",
        },
        {
          key: "a1",
          role: "assistant",
          content: "线索是：运动在恢复，但身体还没有完全稳定。左膝、维生素 D 和睡眠债都说明强度要慢慢加。",
          sources: [entrySource("running-interval"), entrySource("doctor-check"), entrySource("sleep-reset")],
          durationMs: 980,
        },
        {
          key: "a2",
          role: "assistant",
          parentKey: "u1",
          forkOfKey: "a1",
          content: `更完整地看，有三个动作最值得继续：

- 跑步维持低强度，不急着追配速。
- 力量训练关注动作质量，保护膝盖和肩颈。
- 睡眠和补剂放进固定流程，减少靠意志力硬撑。

这条线索的共同点是“慢一点，但别停”。`,
          sources: [
            summarySource("health-running"),
            entrySource("running-interval"),
            entrySource("gym-strength"),
            entrySource("doctor-check"),
            entrySource("first-run"),
          ],
          durationMs: 1520,
        },
      ],
      headKey: "a2",
    },
    2,
  );

  addConversation(
    {
      key: "budget-spending",
      title: "帮我找一下预算和大件支出",
      sourceTypes: SOURCE_TYPES.all,
      messages: [
        {
          key: "u1",
          role: "user",
          content: "帮我找一下最近和预算、大件支出、订阅有关的记录。",
        },
        {
          key: "a1",
          role: "assistant",
          content: `相关记录集中在六月预算、发票整理和账户拆分。

你已经决定取消闲置订阅，并给大件支出设置 14 天冷静期。这个规则和“应急金、旅行、学习、保险分账户”的做法是配套的。`,
          sources: [
            summarySource("budget-home"),
            entrySource("budget-review"),
            entrySource("invoice-tax"),
            entrySource("financial-plan"),
          ],
          durationMs: 1310,
        },
        {
          key: "u2",
          role: "user",
          content: "那七月最应该盯住什么？",
        },
        {
          key: "a2",
          role: "assistant",
          content: "七月先盯三件事：外卖次数、闲置订阅是否真的取消、学习和运动恢复预算有没有被临时消费挤掉。",
          sources: [entrySource("budget-review"), summarySource("budget-home")],
          durationMs: 880,
        },
      ],
    },
    3,
  );

  addConversation(
    {
      key: "travel-outdoor",
      title: "哪些记录提到旅行或户外？",
      sourceTypes: SOURCE_TYPES.all,
      archived: true,
      messages: [
        {
          key: "u1",
          role: "user",
          content: "哪些记录提到旅行、露营、徒步或摄影？",
        },
        {
          key: "a1",
          role: "assistant",
          content:
            "旅行和户外相关记录包括短途旅行打包、河边摄影、近郊露营、12 公里徒步、杭州旅行和城市影像展。它们的共同点是更重视路上的细节，而不是打卡。",
          sources: [
            summarySource("travel-outdoor"),
            entrySource("travel-packing"),
            entrySource("photo-walk"),
            entrySource("camping"),
            entrySource("hiking"),
            entrySource("hangzhou-trip"),
          ],
          durationMs: 1260,
        },
      ],
    },
    4,
  );

  addConversation(
    {
      key: "stress",
      title: "五月到六月的压力来源",
      sourceTypes: SOURCE_TYPES.entry,
      messages: [
        {
          key: "u1",
          role: "user",
          content: "把五月到六月的压力来源归类一下。",
        },
        {
          key: "a1",
          role: "assistant",
          content: `压力来源主要有四类：

- 工作边界：需求线程、产品评审、客户访谈后的收束。
- 身体恢复：睡眠债、左膝、体检指标。
- 日常管理：预算、发票、厨房、外卖。
- 关系沟通：家庭健康、朋友争执、旧同事聊天带来的比较和回望。`,
          sources: [
            entrySource("requirements-thread"),
            entrySource("anxious-night"),
            entrySource("sleep-reset"),
            entrySource("budget-review"),
            entrySource("disagreement"),
          ],
          durationMs: 1420,
        },
      ],
    },
    5,
  );

  addConversation(
    {
      key: "interrupted-sample",
      title: "一次被中断的问答样本",
      sourceTypes: SOURCE_TYPES.entry,
      archived: true,
      messages: [
        {
          key: "u1",
          role: "user",
          content: "测试：这条会话用于查看中断状态。",
        },
        {
          key: "a1",
          role: "assistant",
          status: "interrupted",
          content: "这条回答在生成中被停止，因此保留为中断状态。",
          sources: [entrySource("today-seed-data")],
          durationMs: 430,
        },
      ],
    },
    6,
  );

  return { conversationRows, messageRows };
}

function buildSql(anchorDate) {
  const { entryRows, aiRows, revisionRows, byKey: entriesByKey } = createEntryFactory(anchorDate);
  const { summaryRows, summariesByKey } = createSummaries(anchorDate, entriesByKey);
  const { conversationRows, messageRows } = createAskData(anchorDate, entriesByKey, summariesByKey);

  const statements = [
    "PRAGMA foreign_keys=OFF;",
    "DELETE FROM ask_messages;",
    "DELETE FROM ask_conversations;",
    "DELETE FROM summaries;",
    "DELETE FROM attachments;",
    "DELETE FROM entry_ai;",
    "DELETE FROM entry_revisions;",
    "DELETE FROM entries;",
    "PRAGMA foreign_keys=ON;",
    `INSERT INTO entries (id, entry_date, body, version, created_at, updated_at, deleted_at) VALUES\n${values(
      ["id", "entry_date", "body", "version", "created_at", "updated_at", "deleted_at"],
      entryRows,
    )};`,
    `INSERT INTO entry_revisions (id, entry_id, version, entry_date, body, created_at) VALUES\n${values(
      ["id", "entry_id", "version", "entry_date", "body", "created_at"],
      revisionRows,
    )};`,
    `INSERT INTO entry_ai (entry_id, summary, sentiment, model, duration_ms, generation_count, generated_at) VALUES\n${values(
      [
        "entry_id",
        "summary",
        "sentiment",
        "model",
        "duration_ms",
        "generation_count",
        "generated_at",
      ],
      aiRows,
    )};`,
    `INSERT INTO summaries (id, scope, period_type, start_date, end_date, style, filter, title, content, model, source_entry_ids, trigger, generated_at, created_at, updated_at, deleted_at) VALUES\n${values(
      [
        "id",
        "scope",
        "period_type",
        "start_date",
        "end_date",
        "style",
        "filter",
        "title",
        "content",
        "model",
        "source_entry_ids",
        "trigger",
        "generated_at",
        "created_at",
        "updated_at",
        "deleted_at",
      ],
      summaryRows,
    )};`,
    `INSERT INTO ask_conversations (id, title, source_types, head_message_id, pinned_at, archived_at, created_at, updated_at) VALUES\n${values(
      [
        "id",
        "title",
        "source_types",
        "head_message_id",
        "pinned_at",
        "archived_at",
        "created_at",
        "updated_at",
      ],
      conversationRows,
    )};`,
    `INSERT INTO ask_messages (id, conversation_id, parent_id, fork_of_id, role, content, status, sources, source_types, model, duration_ms, created_at, updated_at) VALUES\n${values(
      [
        "id",
        "conversation_id",
        "parent_id",
        "fork_of_id",
        "role",
        "content",
        "status",
        "sources",
        "source_types",
        "model",
        "duration_ms",
        "created_at",
        "updated_at",
      ],
      messageRows,
    )};`,
    "INSERT INTO entries_fts(entries_fts) VALUES('delete-all');",
    "INSERT INTO entries_fts(rowid, body) SELECT rowid, body FROM entries WHERE deleted_at IS NULL;",
    [
      "SELECT",
      "(SELECT COUNT(*) FROM entries WHERE deleted_at IS NULL) AS live_entries,",
      "(SELECT COUNT(*) FROM entries WHERE deleted_at IS NOT NULL) AS deleted_entries,",
      "(SELECT COUNT(*) FROM entry_ai) AS entry_ai_rows,",
      "(SELECT COUNT(*) FROM summaries WHERE deleted_at IS NULL) AS live_summaries,",
      "(SELECT COUNT(*) FROM ask_conversations WHERE archived_at IS NULL) AS live_conversations,",
      "(SELECT COUNT(*) FROM ask_conversations WHERE archived_at IS NOT NULL) AS archived_conversations;",
    ].join(" "),
  ];

  return {
    sql: `${statements.join("\n\n")}\n`,
    counts: {
      entries: entryRows.length,
      liveEntries: entryRows.filter((row) => row.deleted_at === null).length,
      entryAi: aiRows.length,
      summaries: summaryRows.length,
      conversations: conversationRows.length,
      messages: messageRows.length,
    },
  };
}

function run(command, args, options = {}) {
  console.log(`$ ${[command, ...args].join(" ")}`);
  const result = spawnSync(command, args, {
    stdio: options.input ? ["pipe", "inherit", "inherit"] : "inherit",
    input: options.input,
    encoding: "utf8",
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(" ")} failed with exit code ${result.status}`);
  }
}

function assertLocalDevServerStopped(options) {
  if (options.mode !== "local" || options.force) {
    return;
  }
  const result = spawnSync("lsof", ["-nP", "-iTCP:5173", "-sTCP:LISTEN"], {
    encoding: "utf8",
  });
  const output = `${result.stdout ?? ""}${result.stderr ?? ""}`.trim();
  if (!output) {
    return;
  }
  throw new Error(
    [
      "Local dev server appears to be running on port 5173.",
      "Stop `npm run dev` before reseeding local D1; Miniflare can otherwise leave D1 locked and surface an opaque internal error.",
      "If you know no server is using this project's D1 state, rerun with `-- --force`.",
      "",
      output,
    ].join("\n"),
  );
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const { sql, counts } = buildSql(options.date);
  if (options.dryRun) {
    process.stdout.write(sql);
    return;
  }

  assertLocalDevServerStopped(options);

  const targetFlag = `--${options.mode}`;
  if (!options.skipMigrations) {
    run("npx", ["wrangler", "d1", "migrations", "apply", DB_NAME, targetFlag], {
      input: "y\n",
    });
  }

  const dir = mkdtempSync(join(tmpdir(), "sillage-seed-"));
  const file = join(dir, "seed.sql");
  writeFileSync(file, sql);
  try {
    run("npx", ["wrangler", "d1", "execute", DB_NAME, targetFlag, "--yes", "--file", file]);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }

  console.log(
    [
      "",
      `Seeded ${options.mode} D1 using anchor date ${options.date}.`,
      `Rows: ${counts.liveEntries} live entries (${counts.entries} total), ${counts.entryAi} entry AI rows, ${counts.summaries} summaries, ${counts.conversations} conversations, ${counts.messages} ask messages.`,
    ].join("\n"),
  );
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  usage();
  process.exit(1);
}
