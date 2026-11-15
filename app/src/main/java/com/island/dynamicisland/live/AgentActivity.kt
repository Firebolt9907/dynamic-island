package com.island.dynamicisland.live

/** How long a newly finished (or failed) thread's badge stays in the compact island. */
internal const val AgentFinishedBadgeMs = 10_000L

/**
 * Per-state thread counts from an agent app's aggregate notification, where every body line reads
 * "<Status> <thread title>", e.g. "Working Fix login · web" or "Done Free up storage · No project".
 */
/** One thread line, split into its title and the project after the last " · " (if any). */
data class AgentThread(val state: CompactStatusGlyph, val title: String, val project: String?)

internal fun agentThread(state: CompactStatusGlyph, rest: String): AgentThread {
    val split = rest.lastIndexOf(" · ")
    if (split <= 0) return AgentThread(state, rest, null)
    val project = rest.substring(split + 3).trim()
    // "No project" is a placeholder, not a name worth showing.
    return AgentThread(state, rest.substring(0, split).trim(),
        project.takeUnless { it.isEmpty() || it.equals("no project", ignoreCase = true) })
}

data class AgentActivity(
    val working: Int,
    val attention: Int,
    val done: Int,
    val failed: Int,
    /** Thread identities per finished state, used to notice newly finished threads. */
    val doneThreads: Set<String> = emptySet(),
    val failedThreads: Set<String> = emptySet(),
    /** Threads waiting on the user; a new one is the only agent change worth expanding the island for. */
    val attentionThreads: Set<String> = emptySet(),
    /** Wall-clock deadline for showing the done/failed badges; 0 when nothing finished recently. */
    val finishedBadgeUntil: Long = 0L,
    /** Every thread in the notification's order, for the expanded list. */
    val threads: List<AgentThread> = emptyList(),
)

private val statusPhrases: List<Pair<String, CompactStatusGlyph>> = buildList {
    fun add(glyph: CompactStatusGlyph, vararg phrases: String) = phrases.forEach { add(it to glyph) }
    add(CompactStatusGlyph.WORKING, "working", "thinking", "reasoning", "running", "generating", "building",
        "compiling", "processing", "analyzing", "analysing", "planning", "writing", "coding", "searching", "in progress", "starting")
    add(CompactStatusGlyph.ATTENTION, "needs input", "input needed", "needs approval", "approval needed",
        "awaiting approval", "awaiting input", "waiting for input", "waiting", "action needed", "action required",
        "needs attention", "blocked")
    add(CompactStatusGlyph.DONE, "done", "completed", "complete", "finished", "succeeded")
    add(CompactStatusGlyph.FAILED, "failed", "errored", "error", "interrupted")
}.sortedByDescending { it.first.length }

/** Split one line into its leading status and the thread it describes, or null if it has no known status. */
internal fun agentStatusLine(line: String): Pair<CompactStatusGlyph, String>? {
    val compact = line.trim().replace(Regex("\\s+"), " ")
    for ((phrase, glyph) in statusPhrases) {
        if (!compact.startsWith(phrase, ignoreCase = true)) continue
        val rest = compact.substring(phrase.length)
        // Require a word boundary and a thread title, so "Workingman" or a bare "Done" is not a thread.
        if (rest.isEmpty() || !(rest[0] == ' ' || rest[0] == ':' || rest[0] == '·' || rest[0] == '-')) continue
        val thread = rest.trimStart(' ', ':', '·', '-').trim()
        if (thread.isEmpty()) continue
        return glyph to thread
    }
    return null
}

/** Only treat a notification as an agent summary when every one of its lines is a status line. */
internal fun parseAgentActivity(lines: List<String>): AgentActivity? {
    val parsed = lines.filter { it.isNotBlank() }.map { agentStatusLine(it) ?: return null }
    if (parsed.isEmpty()) return null
    fun threads(glyph: CompactStatusGlyph) = parsed.filter { it.first == glyph }.map { it.second.lowercase() }
    return AgentActivity(
        working = parsed.count { it.first == CompactStatusGlyph.WORKING },
        attention = parsed.count { it.first == CompactStatusGlyph.ATTENTION },
        done = parsed.count { it.first == CompactStatusGlyph.DONE },
        failed = parsed.count { it.first == CompactStatusGlyph.FAILED },
        doneThreads = threads(CompactStatusGlyph.DONE).toSet(),
        failedThreads = threads(CompactStatusGlyph.FAILED).toSet(),
        attentionThreads = threads(CompactStatusGlyph.ATTENTION).toSet(),
        threads = parsed.map { agentThread(it.first, it.second) },
    )
}

/** Remembers which threads were already finished, so only a newly finished thread re-shows the badges. */
internal class AgentFinishMemory {
    private val seen = HashMap<String, AgentActivity>()

    fun remember(key: String, activity: AgentActivity, now: Long): AgentActivity {
        val previous = seen[key]
        val newlyFinished = previous != null &&
            ((activity.doneThreads - previous.doneThreads).isNotEmpty() ||
                (activity.failedThreads - previous.failedThreads).isNotEmpty())
        val until = if (newlyFinished) now + AgentFinishedBadgeMs else previous?.finishedBadgeUntil ?: 0L
        return activity.copy(finishedBadgeUntil = until).also { seen[key] = it }
    }

    fun forget(key: String) { seen.remove(key) }
    fun clear() = seen.clear()
}
