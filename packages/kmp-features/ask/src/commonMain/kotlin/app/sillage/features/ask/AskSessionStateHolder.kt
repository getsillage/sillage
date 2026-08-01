package app.sillage.features.ask

/** Monotonic Ask screen generation used to invalidate callbacks after navigation. */
data class AskSessionStateHolder(
    val generation: Long = 0,
) {
    fun advance(): AskSessionStateHolder = copy(generation = generation + 1)
}
