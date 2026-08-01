package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

enum class RecordsQueryScope {
    Unarchived,
    Archived,
    Favorited,
    Deleted,
}

data class RecordsPageQuery(
    val scope: RecordsQueryScope,
    val cursor: String = "",
)

data class RecordsPage(
    val memos: List<Memo>,
    val nextCursor: String,
)
