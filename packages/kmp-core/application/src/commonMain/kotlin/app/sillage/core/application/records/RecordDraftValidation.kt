package app.sillage.core.application.records

const val MAX_RECORD_CONTENT_UTF8_BYTES: Int = 1 shl 20

enum class RecordDraftValidationError {
    EmptyContent,
    ContentTooLarge,
    InvalidEntryDate,
}

class InvalidRecordDraftException(
    val validationError: RecordDraftValidationError,
) : IllegalArgumentException(validationError.message())

fun validateRecordDraft(draft: RecordDraft): RecordDraftValidationError? {
    if (draft.content.isEmpty()) return RecordDraftValidationError.EmptyContent
    if (draft.content.encodeToByteArray().size > MAX_RECORD_CONTENT_UTF8_BYTES) {
        return RecordDraftValidationError.ContentTooLarge
    }
    if (!isValidRecordEntryDate(draft.entryDate)) {
        return RecordDraftValidationError.InvalidEntryDate
    }
    return null
}

fun requireValidRecordDraft(draft: RecordDraft) {
    val validationError = validateRecordDraft(draft) ?: return
    throw InvalidRecordDraftException(validationError)
}

fun isValidRecordEntryDate(value: String): Boolean {
    if (value.length != 10 || value[4] != '-' || value[7] != '-') return false
    if (value.take(4).any { it !in '0'..'9' }) return false
    if (value.substring(5, 7).any { it !in '0'..'9' }) return false
    if (value.takeLast(2).any { it !in '0'..'9' }) return false
    val year = value.take(4).toInt()
    val month = value.substring(5, 7).toInt()
    val day = value.takeLast(2).toInt()
    if (month !in 1..12) return false
    val leap = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
    val days = when (month) {
        2 -> if (leap) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..days
}

private fun RecordDraftValidationError.message(): String = when (this) {
    RecordDraftValidationError.EmptyContent -> "Record content must not be empty."
    RecordDraftValidationError.ContentTooLarge ->
        "Record content must not exceed 1 MiB encoded as UTF-8."
    RecordDraftValidationError.InvalidEntryDate ->
        "Record entry date must be a valid YYYY-MM-DD date."
}
