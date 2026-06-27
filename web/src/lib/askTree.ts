import type { AskMessage } from "./api";

export interface ActiveEntry {
  message: AskMessage;
  /** Sibling messages of the same role under the same parent (answer variants);
   *  length > 1 means the user can switch between regenerated answers. */
  variants: AskMessage[];
  /** Index of `message` within `variants`. */
  index: number;
}

function childrenByParent(messages: AskMessage[]): Map<string, AskMessage[]> {
  const map = new Map<string, AskMessage[]>();
  for (const message of messages) {
    const key = message.parentId ?? "";
    const list = map.get(key) ?? [];
    list.push(message);
    map.set(key, list);
  }
  for (const list of map.values()) {
    list.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  }
  return map;
}

function newest(messages: AskMessage[]): AskMessage | null {
  return messages.reduce<AskMessage | null>(
    (best, m) => (best && best.createdAt >= m.createdAt ? best : m),
    null,
  );
}

/**
 * Linearizes a message tree into the active conversation path. The active leaf
 * is `headId` (falling back to the newest message); the path is its ancestor
 * chain root-first. Each entry exposes its sibling variants so the UI can render
 * a "< n/m >" switcher for regenerated answers.
 */
export function buildActivePath(
  messages: AskMessage[],
  headId: string | null,
): ActiveEntry[] {
  if (messages.length === 0) {
    return [];
  }
  const byId = new Map(messages.map((m) => [m.id, m]));
  const children = childrenByParent(messages);

  let leaf = headId ? byId.get(headId) : undefined;
  if (!leaf) {
    leaf = newest(messages) ?? undefined;
  }
  if (!leaf) {
    return [];
  }

  const pathIds: string[] = [];
  const seen = new Set<string>();
  let cursor: AskMessage | undefined = leaf;
  while (cursor && !seen.has(cursor.id)) {
    seen.add(cursor.id);
    pathIds.push(cursor.id);
    cursor = cursor.parentId ? byId.get(cursor.parentId) : undefined;
  }
  pathIds.reverse();

  return pathIds.map((id) => {
    const message = byId.get(id) as AskMessage;
    const siblings = (children.get(message.parentId ?? "") ?? []).filter(
      (s) => s.role === message.role,
    );
    return {
      message,
      variants: siblings,
      index: siblings.findIndex((s) => s.id === message.id),
    };
  });
}

/**
 * The leaf of the branch rooted at `fromId`: descend through the newest child at
 * each step. Switching to an answer variant makes this its new active head.
 */
export function branchLeafId(messages: AskMessage[], fromId: string): string {
  const children = childrenByParent(messages);
  let current = fromId;
  for (;;) {
    const kids = children.get(current);
    if (!kids || kids.length === 0) {
      return current;
    }
    current = kids[kids.length - 1].id;
  }
}
