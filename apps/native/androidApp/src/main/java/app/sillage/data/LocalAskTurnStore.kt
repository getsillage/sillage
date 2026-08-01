package app.sillage.data

import app.sillage.core.application.ask.AppendAskTurnCommand
import app.sillage.core.application.ask.AskTurnStore

class LocalAskTurnStore(
    private val localDataStore: LocalDataStore,
) : AskTurnStore {
    override suspend fun append(command: AppendAskTurnCommand) {
        localDataStore.appendAskTurn(
            conversationId = command.conversationId,
            question = command.question,
            answer = command.answer,
            sourceRefs = command.sourceRefs,
            model = command.model,
            promptVersion = command.promptVersion,
            parentId = command.parentMessageId,
            forkOfId = command.forkOfMessageId,
        )
    }
}
