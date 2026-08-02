package app.sillage.features.records

fun RecordsFeatureStateHolder.shouldShowRecordListLoadFailure(): Boolean =
    viewMode == MemoViewMode.List &&
        search.query.isBlank() &&
        refresh.status == RecordsRefreshStatus.Failed &&
        records.isEmpty() &&
        search.results == null

fun RecordsFeatureStateHolder.shouldShowRecordSearchFailure(): Boolean {
    val query = search.query.trim()
    return viewMode == MemoViewMode.List &&
        query.isNotBlank() &&
        query == search.failureQuery.trim() &&
        !search.searching &&
        search.currentResults() == null
}
