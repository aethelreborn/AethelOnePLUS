package org.polyfrost.polyplus.client.social

import org.polyfrost.polyplus.client.PolyPlusConfig

/**
 * Per-group mute state.
 *
 * The mute toggle lives in the social overlay, but notifications are raised from
 * the network repositories, so the set has to outlive the composable that edits
 * it and survive a client restart. It is stored as a comma-separated id list so
 * it does not depend on collection serialisation, which differs across the
 * OneConfig versions this project is built against.
 */
object MutedGroups {
    @Volatile
    private var cachedRaw: String? = null

    @Volatile
    private var cached: Set<Int> = emptySet()

    fun isMuted(groupId: Int): Boolean = current().contains(groupId)

    /** Flips the mute for [groupId] and returns the new state. */
    fun toggle(groupId: Int): Boolean {
        val next = current().toMutableSet()
        val nowMuted = if (next.remove(groupId)) {
            false
        } else {
            next.add(groupId)
            true
        }
        write(next)
        return nowMuted
    }

    fun current(): Set<Int> {
        val raw = PolyPlusConfig.mutedGroupIds
        if (raw == cachedRaw) return cached
        val parsed = parse(raw)
        cachedRaw = raw
        cached = parsed
        return parsed
    }

    private fun write(ids: Set<Int>) {
        val raw = serialize(ids)
        cachedRaw = raw
        cached = ids
        PolyPlusConfig.mutedGroupIds = raw
    }

    companion object {
        /** Blank, malformed and whitespace-padded entries are all tolerated. */
        fun parse(raw: String): Set<Int> =
            raw.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()

        /** Sorted so the stored value is stable and the cache comparison is meaningful. */
        fun serialize(ids: Set<Int>): String = ids.sorted().joinToString(",")
    }
}