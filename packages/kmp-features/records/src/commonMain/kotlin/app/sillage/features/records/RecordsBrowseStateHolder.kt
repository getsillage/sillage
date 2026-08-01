package app.sillage.features.records

enum class MemoViewMode {
    List,
    Calendar,
    ;

    companion object {
        fun fromName(value: String): MemoViewMode {
            return entries.firstOrNull { it.name == value } ?: List
        }
    }
}

/** Immutable records browsing, filter, and calendar-selection state. */
data class RecordsBrowseStateHolder(
    val viewMode: MemoViewMode = MemoViewMode.List,
    val filter: MemoListFilter = MemoListFilter.Unarchived,
    val calendarYear: Int,
    val calendarMonth: Int,
    val selectedCalendarDate: String? = null,
) {
    fun selectViewMode(mode: MemoViewMode): RecordsBrowseStateHolder {
        return copy(
            viewMode = mode,
            filter = if (mode == MemoViewMode.Calendar) MemoListFilter.Unarchived else filter,
        )
    }

    fun selectFilter(value: MemoListFilter): RecordsBrowseStateHolder = copy(filter = value)

    fun selectMonth(year: Int, month: Int): RecordsBrowseStateHolder {
        return copy(
            calendarYear = year,
            calendarMonth = month,
            selectedCalendarDate = null,
        )
    }

    fun selectCalendarDate(value: String?): RecordsBrowseStateHolder {
        return copy(selectedCalendarDate = value)
    }
}
