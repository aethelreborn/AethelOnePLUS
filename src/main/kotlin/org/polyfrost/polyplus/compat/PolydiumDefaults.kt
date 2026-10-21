package org.polyfrost.polyplus.compat

import com.google.gson.JsonParser
import org.apache.logging.log4j.LogManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Polydium, the performance mod that ships inside the PolyPlus jar, keeps a copy
 * of the chunk sections it has already built so it does not have to build them a
 * second time. That copy costs memory, which is exactly what a weak machine
 * cannot spare, so the low-end mode turns it off.
 *
 * This runs before the game starts because Polydium reads its config while it
 * initialises, and the change is only picked up on the next launch.
 */
object PolydiumDefaults {
    private val logger = LogManager.getLogger("PolyPlus/Polydium")

    private const val POLYPLUS_PERFORMANCE_FILE = "polyplus-performance.json"
    private const val LOW_END_KEY = "lowEndMode"
    private const val POLYDIUM_FILE = "polydium.json"
    private const val SECTION_CACHE_SIZE = "sectionCacheSize"

    fun apply(configDir: Path) {
        if (!lowEndEnabled(configDir)) return
        val path = configDir.resolve(POLYDIUM_FILE)
        if (!path.exists()) return

        val text = runCatching { path.readText() }.getOrNull() ?: return
        val merged = ModConfigDefaults.mergeJson(
            text,
            listOf(ModConfigDefaults.Default("polydium", POLYDIUM_FILE, SECTION_CACHE_SIZE, false)),
        )
        if (merged == text) return
        runCatching { writeAtomically(path, merged) }
            .onSuccess { logger.info("Turned the Polydium section cache off for the low-end mode") }
            .onFailure { logger.warn("Could not write the Polydium config", it) }
    }

    /** Whether the low-end mode is on, read straight from the settings file. */
    internal fun lowEndEnabled(configDir: Path): Boolean = runCatching {
        val file = configDir.resolve(POLYPLUS_PERFORMANCE_FILE)
        if (!file.exists()) return false
        val root = JsonParser.parseString(file.readText()).asJsonObject
        root.get(LOW_END_KEY)?.asBoolean ?: false
    }.getOrDefault(false)

    private fun writeAtomically(path: Path, text: String) {
        val temp = path.resolveSibling("${path.fileName}.polyplus-tmp")
        try {
            temp.writeText(text)
            runCatching { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE) }
                .recoverCatching { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
                .getOrThrow()
        } finally {
            runCatching { temp.deleteIfExists() }
        }
    }
}
