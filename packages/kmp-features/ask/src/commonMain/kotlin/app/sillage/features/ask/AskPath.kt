package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage

data class AskPathEntry(
    val message: AskMessage,
    val variants: List<AskMessage>,
    val index: Int,
)

fun buildAskActivePath(
    messages: List<AskMessage>,
    headId: String?,
): List<AskPathEntry> {
    val active = activeAskMessages(messages)
    if (active.isEmpty()) return emptyList()

    val byId = active.associateBy { it.id }
    val children = askChildrenByParent(active)
    var leaf = headId?.let(byId::get) ?: active.maxByOrNull { it.createdAt }
    if (leaf == null) return emptyList()

    val pathIds = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    while (leaf != null && seen.add(leaf.id)) {
        pathIds += leaf.id
        leaf = leaf.parentId?.let(byId::get)
    }

    return pathIds.asReversed().mapNotNull { id ->
        val message = byId[id] ?: return@mapNotNull null
        val variants = children[message.parentId.orEmpty()].orEmpty()
            .filter { it.role == message.role }
        AskPathEntry(
            message = message,
            variants = variants,
            index = variants.indexOfFirst { it.id == message.id },
        )
    }
}

fun askBranchLeafId(
    messages: List<AskMessage>,
    fromId: String,
): String {
    val children = askChildrenByParent(activeAskMessages(messages))
    var current = fromId
    while (true) {
        val kids = children[current].orEmpty()
        if (kids.isEmpty()) return current
        current = kids.last().id
    }
}

fun lastAssistantMessageId(entries: List<AskPathEntry>): String? =
    entries.lastOrNull { it.message.role == "assistant" }?.message?.id

private fun askChildrenByParent(
    messages: List<AskMessage>,
): Map<String, List<AskMessage>> = messages
    .groupBy { it.parentId.orEmpty() }
    .mapValues { (_, children) -> children.sortedBy { it.createdAt } }
