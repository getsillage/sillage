package app.sillage.core.application.ask

data class AskClient(
    val repository: AskRepository,
    val answerStreamer: AskAnswerStreamer,
)

/** Creates one authenticated Ask client bound to a normalized Sillage server. */
fun interface AskClientFactory {
    fun createAskClient(baseUrl: String): AskClient
}
