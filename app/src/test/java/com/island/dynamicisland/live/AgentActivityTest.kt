package com.island.dynamicisland.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentActivityTest {
    private val lines = listOf(
        "Working Adjust Album Cover Padding and Border · dynamic-island",
        "Working Live Activity Progress Layout · dynamic-island",
        "Done Free Up Laptop Storage · No project",
    )

    @Test fun countsEachThreadState() {
        val agents = parseAgentActivity(lines + "Needs input Review migration · api")!!
        assertEquals(2, agents.working)
        assertEquals(1, agents.attention)
        assertEquals(1, agents.done)
        assertEquals(0, agents.failed)
    }

    @Test fun ignoresOrdinaryNotificationText() {
        assertNull(parseAgentActivity(listOf("Your driver is 3 minutes away")))
        assertNull(parseAgentActivity(lines + "Something else entirely"))
        assertNull(parseAgentActivity(listOf("Workingman's Dead")))
        assertNull(parseAgentActivity(listOf("Done")))
        assertNull(parseAgentActivity(emptyList()))
    }

    @Test fun onlyNewlyFinishedThreadsShowTheBadge() {
        val memory = AgentFinishMemory()
        // Threads already done when first seen do not flash.
        assertEquals(0L, memory.remember("k", parseAgentActivity(lines)!!, now = 1_000).finishedBadgeUntil)
        // A working thread finishing starts a fresh 10 s window.
        val finished = listOf(lines[0], "Done Live Activity Progress Layout · dynamic-island", lines[2])
        assertEquals(5_000 + AgentFinishedBadgeMs, memory.remember("k", parseAgentActivity(finished)!!, now = 5_000).finishedBadgeUntil)
        // A routine re-post keeps the same deadline instead of extending it.
        assertEquals(5_000 + AgentFinishedBadgeMs, memory.remember("k", parseAgentActivity(finished)!!, now = 8_000).finishedBadgeUntil)
    }

    @Test fun splitsThreadTitleAndProject() {
        val threads = parseAgentActivity(lines)!!.threads
        assertEquals(AgentThread(CompactStatusGlyph.WORKING, "Adjust Album Cover Padding and Border", "dynamic-island"), threads[0])
        assertEquals(AgentThread(CompactStatusGlyph.DONE, "Free Up Laptop Storage", null), threads[2])
        assertEquals(AgentThread(CompactStatusGlyph.WORKING, "dynamic-island", null), parseAgentActivity(listOf("Working dynamic-island"))!!.threads[0])
    }
}
