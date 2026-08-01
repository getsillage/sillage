package app.sillage.core.application.records

class ListRecordsPageUseCase(
    private val repository: RecordsPageRepository,
) {
    suspend operator fun invoke(query: RecordsPageQuery): RecordsPage {
        return repository.listPage(query)
    }
}
