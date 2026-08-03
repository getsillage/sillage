package app.sillage.core.application.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordDraftValidationTest {
    @Test
    fun validatesContentUsingUtf8ByteLimit() {
        assertEquals(
            RecordDraftValidationError.EmptyContent,
            validateRecordDraft(RecordDraft("", "2026-08-03")),
        )
        assertNull(
            validateRecordDraft(
                RecordDraft("a".repeat(MAX_RECORD_CONTENT_UTF8_BYTES), "2026-08-03"),
            ),
        )
        assertEquals(
            RecordDraftValidationError.ContentTooLarge,
            validateRecordDraft(
                RecordDraft("a".repeat(MAX_RECORD_CONTENT_UTF8_BYTES + 1), "2026-08-03"),
            ),
        )
        assertEquals(
            RecordDraftValidationError.ContentTooLarge,
            validateRecordDraft(
                RecordDraft("你".repeat(MAX_RECORD_CONTENT_UTF8_BYTES / 3 + 1), "2026-08-03"),
            ),
        )
    }

    @Test
    fun validatesGregorianEntryDates() {
        assertNull(validateRecordDraft(RecordDraft("body", "2024-02-29")))
        assertNull(validateRecordDraft(RecordDraft("body", "0000-02-29")))
        assertEquals(
            RecordDraftValidationError.InvalidEntryDate,
            validateRecordDraft(RecordDraft("body", "2100-02-29")),
        )
        assertNull(validateRecordDraft(RecordDraft("body", "2000-02-29")))
        assertEquals(
            RecordDraftValidationError.InvalidEntryDate,
            validateRecordDraft(RecordDraft("body", "2026-13-01")),
        )
        assertEquals(
            RecordDraftValidationError.InvalidEntryDate,
            validateRecordDraft(RecordDraft("body", "+001-01-01")),
        )
    }
}
