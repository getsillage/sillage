package app.sillage.features.records

import app.sillage.core.domain.records.Memo

enum class MemoListFilter {
    Unarchived,
    Archived,
    Favorited,
    Deleted,
}

fun Memo.matchesListFilter(filter: MemoListFilter): Boolean {
    if (filter == MemoListFilter.Deleted) {
        return deletedAt != null && purgedAt == null
    }
    if (deletedAt != null || purgedAt != null) {
        return false
    }
    return when (filter) {
        MemoListFilter.Unarchived -> archivedAt == null && favoritedAt == null
        MemoListFilter.Archived -> archivedAt != null && favoritedAt == null
        MemoListFilter.Favorited -> favoritedAt != null
        MemoListFilter.Deleted -> false
    }
}

fun sortMemos(memos: List<Memo>): List<Memo> {
    return memos.sortedWith(
        compareByDescending<Memo> { it.entryDate }
            .thenByDescending { it.createdAt },
    )
}

fun activeMemos(memos: List<Memo>): List<Memo> {
    return memosForFilter(memos, MemoListFilter.Unarchived)
}

fun memosForFilter(memos: List<Memo>, filter: MemoListFilter): List<Memo> {
    return sortMemos(memos.filter { it.matchesListFilter(filter) })
}

fun excerpt(body: String, max: Int = 120): String {
    val text = body.replace(Regex("\\s+"), " ").trim()
    return if (text.length > max) "${text.take(max)}…" else text
}

fun onThisDay(memos: List<Memo>, todayISO: String): List<Memo> {
    val monthDay = todayISO.drop(5)
    val year = todayISO.take(4)
    return memos
        .filter {
            it.matchesListFilter(MemoListFilter.Unarchived) &&
                it.entryDate.drop(5) == monthDay &&
                it.entryDate.take(4) < year
        }
        .sortedByDescending { it.entryDate }
}

fun yearsBetween(fromISO: String, toISO: String): Int {
    return toISO.take(4).toInt() - fromISO.take(4).toInt()
}

fun entryDateCounts(memos: List<Memo>): Map<String, Int> {
    return memos
        .filter { it.matchesListFilter(MemoListFilter.Unarchived) }
        .groupingBy { it.entryDate }
        .eachCount()
}

fun entriesByDate(memos: List<Memo>, date: String): List<Memo> {
    return activeMemos(memos.filter { it.entryDate == date })
}

data class CalendarMemoCoverage(
    val hasMoreOlderRecords: Boolean,
    val currentMonthMayBeIncomplete: Boolean,
)

fun calendarMemoCoverage(
    memos: List<Memo>,
    nextCursor: String,
    year: Int,
    month: Int,
): CalendarMemoCoverage {
    val hasMore = nextCursor.isNotBlank()
    if (!hasMore) {
        return CalendarMemoCoverage(
            hasMoreOlderRecords = false,
            currentMonthMayBeIncomplete = false,
        )
    }

    val viewedMonth = yearMonthIndex(year, month)
    val oldestLoadedMonth = memos
        .asSequence()
        .filter { it.matchesListFilter(MemoListFilter.Unarchived) }
        .mapNotNull { yearMonthIndex(it.entryDate) }
        .minOrNull()
    return CalendarMemoCoverage(
        hasMoreOlderRecords = true,
        currentMonthMayBeIncomplete = oldestLoadedMonth == null || viewedMonth <= oldestLoadedMonth,
    )
}

private fun yearMonthIndex(year: Int, month: Int): Int {
    require(month in 1..12) { "month must be between 1 and 12" }
    return year * 12 + month - 1
}

private fun yearMonthIndex(isoDate: String): Int? {
    if (isoDate.length < 7 || isoDate[4] != '-') {
        return null
    }
    val year = isoDate.substring(0, 4).toIntOrNull() ?: return null
    val month = isoDate.substring(5, 7).toIntOrNull() ?: return null
    return month.takeIf { it in 1..12 }?.let { yearMonthIndex(year, it) }
}
