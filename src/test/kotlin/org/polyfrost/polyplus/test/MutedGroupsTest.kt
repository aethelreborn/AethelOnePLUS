package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.social.MutedGroups

class MutedGroupsTest {

    @Test
    fun `blank value means nothing is muted`() {
        assertTrue(MutedGroups.parse("").isEmpty())
    }

    @Test
    fun `single and multiple ids are parsed`() {
        assertEquals(setOf(7), MutedGroups.parse("7"))
        assertEquals(setOf(3, 11, 42), MutedGroups.parse("3,11,42"))
    }

    @Test
    fun `whitespace and malformed entries are tolerated`() {
        // A hand-edited or truncated config must not throw or wipe valid ids.
        assertEquals(setOf(1, 4), MutedGroups.parse(" 1 , 4 "))
        assertEquals(setOf(5), MutedGroups.parse("5,,oops,9x,"))
        assertTrue(MutedGroups.parse("not a number at all").isEmpty())
    }

    @Test
    fun `duplicates collapse`() {
        assertEquals(setOf(2, 8), MutedGroups.parse("2,8,2,8"))
    }

    @Test
    fun `serialize is sorted so the cached comparison stays stable`() {
        assertEquals("1,2,30", MutedGroups.serialize(setOf(30, 2, 1)))
    }

    @Test
    fun `round trips through serialize and parse`() {
        val ids = setOf(4, 17, 256)
        assertEquals(ids, MutedGroups.parse(MutedGroups.serialize(ids)))
        assertTrue(MutedGroups.serialize(emptySet()).isEmpty())
    }
}