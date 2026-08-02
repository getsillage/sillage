package app.sillage.ui.records

import app.sillage.core.domain.records.MemoAI
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsSummaryStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SillageRecordSummarySectionTest {
    @Test
    fun missingSummaryUsesGeneratePresentation() {
        val presentation = presentation(summary = null, loading = false)

        assertEquals("Generate", presentation.actionLabel)
        assertEquals("Empty", presentation.body)
        assertTrue(presentation.bodyMuted)
        assertNull(presentation.sourceRecordsLabel)
        assertNull(presentation.technicalDetails)
    }

    @Test
    fun initialLoadUsesReadingPresentation() {
        val presentation = presentation(summary = null, loading = true)

        assertEquals("Reading", presentation.actionLabel)
        assertEquals("Loading", presentation.body)
        assertTrue(presentation.bodyMuted)
    }

    @Test
    fun generatedSummaryIncludesHostLabelsAndTechnicalDetails() {
        val presentation = presentation(
            summary = summary(body = "Summary"),
            loading = false,
        )

        assertEquals("Regenerate", presentation.actionLabel)
        assertEquals("Summary", presentation.body)
        assertFalse(presentation.bodyMuted)
        assertEquals("2 source records", presentation.sourceRecordsLabel)
        assertEquals("provider / model · 42 tokens", presentation.technicalDetails)
    }

    @Test
    fun regenerationKeepsPublishedSummaryVisible() {
        val presentation = presentation(
            summary = summary(body = "Published"),
            loading = true,
        )

        assertEquals("Generating", presentation.actionLabel)
        assertEquals("Published", presentation.body)
        assertFalse(presentation.bodyMuted)
    }

    @Test
    fun blankPublishedSummaryKeepsMetadataHidden() {
        val presentation = presentation(
            summary = summary(body = "  "),
            loading = false,
        )

        assertEquals("Regenerate", presentation.actionLabel)
        assertEquals("Empty", presentation.body)
        assertTrue(presentation.bodyMuted)
        assertNull(presentation.sourceRecordsLabel)
        assertNull(presentation.technicalDetails)
    }

    private fun presentation(
        summary: MemoAI?,
        loading: Boolean,
    ): SillageRecordSummaryPresentation = sillageRecordSummaryPresentation(
        state = RecordsFeatureStateHolder(
            summary = RecordsSummaryStateHolder(
                summary = summary,
                loading = loading,
            ),
        ),
        strings = strings(),
        sourceRecordsLabel = "2 source records",
        tokenCountLabel = "42 tokens",
    )

    private fun strings(): SillageRecordSummaryStrings = SillageRecordSummaryStrings(
        title = "Summary",
        readingAction = "Reading",
        generatingAction = "Generating",
        generateAction = "Generate",
        regenerateAction = "Regenerate",
        loadingBody = "Loading",
        emptyBody = "Empty",
    )

    private fun summary(body: String): MemoAI = MemoAI(
        memoId = "memo",
        summary = body,
        sentiment = null,
        provider = "provider",
        model = "model",
        profileId = "profile",
        promptVersion = "v1",
        sourceMemoIds = "[]",
        status = "complete",
        errorCode = null,
        startedAt = null,
        finishedAt = null,
        inputTokens = 20,
        outputTokens = 22,
        totalTokens = 42,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
    )
}
