package app.sillage.features.ask

/** Owns Ask conversation/message loading and its durable retry message. */
data class AskLoadStateHolder(
    val loading: Boolean = false,
    val errorMessage: String? = null,
) {
    fun begin(): AskLoadStateHolder = copy(loading = true, errorMessage = null)

    fun complete(): AskLoadStateHolder = copy(loading = false, errorMessage = null)

    fun fail(message: String): AskLoadStateHolder = copy(
        loading = false,
        errorMessage = message,
    )

    fun cancel(): AskLoadStateHolder = copy(loading = false, errorMessage = null)
}
