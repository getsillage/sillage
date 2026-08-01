package app.sillage.features.ask

/** Owns the Ask prompt and retrieval options independently from request execution. */
data class AskComposerStateHolder(
    val question: String = "",
    val contextScope: String = "recent_30_days",
    val sourceKind: String = "records",
) {
    fun updateQuestion(value: String): AskComposerStateHolder = copy(question = value)

    fun updateContextScope(value: String): AskComposerStateHolder {
        require(value.isNotBlank()) { "Ask context scope must not be blank" }
        return copy(contextScope = value)
    }

    fun updateSourceKind(value: String): AskComposerStateHolder {
        require(value.isNotBlank()) { "Ask source kind must not be blank" }
        return copy(sourceKind = value)
    }

    fun clearQuestion(): AskComposerStateHolder = copy(question = "")
}
