package app.sillage.core.application.records

class GetRecordDetailUseCase(
    private val repository: RecordDetailRepository,
) {
    suspend operator fun invoke(memoId: String): RecordDetail {
        return repository.getRecordDetail(memoId)
    }
}
