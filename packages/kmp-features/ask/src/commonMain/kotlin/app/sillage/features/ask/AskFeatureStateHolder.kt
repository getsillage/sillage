package app.sillage.features.ask

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage

/**
 * Aggregated immutable ownership for the Ask feature.
 *
 * Individual holders remain the unit of request identity and late-response
 * validation. This type owns cross-holder transitions that must stay consistent
 * across conversation selection, load ownership, composer scope, and session
 * generation.
 */
data class AskFeatureStateHolder(
    val conversation: AskConversationStateHolder = AskConversationStateHolder(),
    val composer: AskComposerStateHolder = AskComposerStateHolder(),
    val load: AskLoadStateHolder = AskLoadStateHolder(),
    val stream: AskStreamStateHolder = AskStreamStateHolder(),
    val variant: AskVariantStateHolder = AskVariantStateHolder(),
    val session: AskSessionStateHolder = AskSessionStateHolder(),
    val sourceNavigation: AskSourceNavigationStateHolder = AskSourceNavigationStateHolder(),
    val memoSave: AskMemoSaveStateHolder = AskMemoSaveStateHolder(),
) {
    val conversations: List<AskConversation> get() = conversation.conversations
    val activeConversationId: String get() = conversation.activeConversationId
    val headMessageId: String? get() = conversation.headMessageId
    val messages: List<AskMessage> get() = conversation.messages
    val question: String get() = composer.question
    val contextScope: String get() = composer.contextScope
    val sourceKind: String get() = composer.sourceKind
    val loading: Boolean get() = load.loading
    val loadErrorMessage: String? get() = load.errorMessage
    val sending: Boolean get() = stream.sending
    val streaming: Boolean get() = stream.streaming
    val screenSessionId: Long get() = session.generation
    val variantLoading: Boolean get() = variant.loading
    val sourceLoading: Boolean get() = sourceNavigation.loading
    val savingMessageId: String get() = memoSave.savingMessageId

    /**
     * Clears Ask workspace ownership when the authenticated client context ends.
     * Sign-out also invalidates live stream/variant identity; mode switches keep
     * those request counters unless the host cancels the jobs separately.
     */
    fun clearWorkspace(
        invalidateStream: Boolean = false,
        invalidateVariant: Boolean = false,
    ): AskFeatureStateHolder {
        return copy(
            conversation = conversation.clear(),
            composer = composer.clearQuestion(),
            load = load.cancel(),
            stream = if (invalidateStream) stream.invalidate() else stream,
            variant = if (invalidateVariant) variant.invalidate() else variant,
            session = session.advance(),
            sourceNavigation = sourceNavigation.invalidate(),
            memoSave = memoSave.invalidate(),
        )
    }

    /**
     * Enters the Ask screen. Advances screen generation only when no Ask request
     * is already in flight, and always drops source-navigation ownership.
     */
    fun enterScreen(requestInFlight: Boolean): AskFeatureStateHolder {
        return copy(
            session = if (requestInFlight) session else session.advance(),
            sourceNavigation = sourceNavigation.invalidate(),
        )
    }

    /**
     * Starts a blank Ask composition surface without an active conversation.
     */
    fun startNewConversation(): AskFeatureStateHolder {
        return copy(
            conversation = conversation.deselect(),
            composer = composer.clearQuestion(),
            stream = stream.clearPresentation(),
            session = session.advance(),
            variant = variant.invalidate(),
            sourceNavigation = sourceNavigation.invalidate(),
        )
    }

    /**
     * Selects a conversation and begins message loading for that selection.
     */
    fun beginConversationLoad(
        conversationId: String,
        headMessageId: String?,
        contextScope: String? = null,
    ): AskFeatureStateHolder {
        return copy(
            conversation = conversation.select(
                conversationId = conversationId,
                headMessageId = headMessageId,
                messages = emptyList(),
            ),
            composer = contextScope?.let(composer::updateContextScope) ?: composer,
            load = load.begin(),
            session = session.advance(),
            variant = variant.invalidate(),
            sourceNavigation = sourceNavigation.invalidate(),
        )
    }

    /**
     * Applies a validated message snapshot for the active conversation load.
     */
    fun completeConversationLoad(
        conversationId: String,
        headMessageId: String?,
        messages: List<AskMessage>,
    ): AskFeatureStateHolder {
        return copy(
            conversation = conversation.select(
                conversationId = conversationId,
                headMessageId = headMessageId,
                messages = messages,
            ),
            load = load.complete(),
        )
    }

    /**
     * Fails the active conversation/message load while preserving selection.
     */
    fun failConversationLoad(message: String): AskFeatureStateHolder {
        return copy(load = load.fail(message))
    }

    /**
     * Replaces the conversation list after a successful catalog load.
     */
    fun completeConversationCatalog(
        conversations: List<AskConversation>,
    ): AskFeatureStateHolder {
        return copy(
            conversation = conversation.replaceConversations(conversations),
            load = load.complete(),
        )
    }

    /**
     * Begins conversation-catalog loading without changing the current selection.
     */
    fun beginConversationCatalogLoad(): AskFeatureStateHolder {
        return copy(load = load.begin())
    }

    /**
     * Fails conversation-catalog loading.
     */
    fun failConversationCatalogLoad(message: String): AskFeatureStateHolder {
        return copy(load = load.fail(message))
    }
}
