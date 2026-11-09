package com.island.dynamicisland.live

/** Direction is relative to this device; never infer it from an app/package name. */
enum class TransferDirection { UPLOAD, DOWNLOAD, SYNC }
data class TransferActivity(val direction: TransferDirection, val paused: Boolean, val progress: Float?) {
    val label: String get() = if (paused) "Paused" else when (direction) {
        TransferDirection.UPLOAD -> "Uploading"
        TransferDirection.DOWNLOAD -> "Downloading"
        TransferDirection.SYNC -> "Transferring"
    }
}

private val uploadWords = Regex("\\b(upload(?:ing|s)?|sending|backing up)\\b", RegexOption.IGNORE_CASE)
private val downloadWords = Regex("\\b(download(?:ing|s)?|receiving|fetching)\\b", RegexOption.IGNORE_CASE)
private val syncWords = Regex("\\b(syncing|synchronizing|synchronising|transferring|copying)\\b", RegexOption.IGNORE_CASE)
private val transferObject = Regex("\\b(files?|photos?|videos?|items?|documents?|backup|data|media)\\b", RegexOption.IGNORE_CASE)
private val terminalTransfer = Regex("\\b(upload|download|transfer|sync|backup)\\s+(complete(?:d)?|finished|failed|cancelled|canceled)\\b|\\b(?:successfully uploaded|successfully downloaded|uploaded successfully|downloaded successfully)\\b", RegexOption.IGNORE_CASE)
private val percentage = Regex("(?<![\\d.])([0-9]{1,3}(?:\\.[0-9]+)?)\\s*%")

/** English fallback for ordinary notifications; structured progress remains authoritative. */
internal fun detectTransferActivity(
    texts: List<String>, ongoing: Boolean, progress: Float? = null, indeterminate: Boolean = false,
): TransferActivity? {
    val text = texts.filter { it.isNotBlank() }.joinToString("\n")
    if (terminalTransfer.containsMatchIn(text)) return null
    val up = uploadWords.containsMatchIn(text)
    val down = downloadWords.containsMatchIn(text)
    val sync = syncWords.containsMatchIn(text)
    val explicitTransfer = Regex("\\b(upload(?:ing|s)?|download(?:ing|s)?|backing up)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    val deviceTransfer = Regex("\\b(?:sending to|receiving from)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    if (!explicitTransfer && !transferObject.containsMatchIn(text) && !deviceTransfer && progress == null && !indeterminate) return null
    if (!up && !down && !(sync && transferObject.containsMatchIn(text))) return null
    val percent = percentage.findAll(text).mapNotNull { it.groupValues[1].toFloatOrNull() }
        .firstOrNull { it in 0f..100f }?.div(100f)
    // Text alone cannot promote a dismissible message or an invitation to upload.
    if (!ongoing && progress == null && !indeterminate) return null
    if (progress == null && !indeterminate && percent == null &&
        !Regex("\\b(uploading|downloading|sending|receiving|backing up|syncing|synchronizing|synchronising|transferring|copying|paused|waiting|queued)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
    return TransferActivity(when {
        up && !down -> TransferDirection.UPLOAD
        down && !up -> TransferDirection.DOWNLOAD
        else -> TransferDirection.SYNC
    }, Regex("\\b(paused|waiting|queued)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text),
        if (indeterminate) null else progress ?: percent)
}

/** Remove standalone progress readouts, preserving percentages embedded in filenames. */
internal fun stripTransferPercentage(text: String): String = text
    .replace(Regex("(?<![\\p{L}\\p{N}_.])(?:100|[0-9]{1,2})(?:\\.[0-9]+)?\\s*%(?![\\p{L}\\p{N}_.])"), "")
    .replace(Regex("\\(\\s*\\)|\\[\\s*]"), "")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("(?m)^\\s*[·•|:–—-]+\\s*|\\s*[·•|:–—-]+\\s*$"), "")
    .trim()

internal fun transferStatusIsRedundant(label: String, vararg fields: String): Boolean =
    fields.any { Regex("\\b${Regex.escape(label)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
