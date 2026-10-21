package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.utils.VanillaOptions
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The graphics backend the game draws through.
 *
 * Polydium puts the game on Vulkan on its own when it sees a discrete graphics
 * card, which is the right call on a machine with a working Vulkan driver and
 * the wrong one on a machine where Vulkan falls back to the processor. Going
 * back to OpenGL means telling Polydium about it as well, or the next launch
 * goes back to Vulkan.
 */
object GraphicsBackend {
    private const val POLYDIUM_DIR = "polydium"
    private const val GRAPHICS_API_FILE = "graphics-api"
    private const val AUTO_VULKAN_FILE = "auto-vulkan"

    /** The backend the game draws through, or "default" when the version has no choice. */
    fun current(): String {
        val options = Minecraft.getInstance().options ?: return "default"
        return VanillaOptions.enumOption(options, "preferredGraphicsBackend")?.lowercase() ?: "default"
    }

    /** Puts the game back on OpenGL, telling Polydium about it as well. */
    fun switchToOpenGL(): Boolean {
        val options = Minecraft.getInstance().options ?: return false
        var changed = VanillaOptions.setEnumOption(options, "OPENGL", "preferredGraphicsBackend")
        changed = write(GRAPHICS_API_FILE, "opengl") || changed
        changed = write(AUTO_VULKAN_FILE, "no") || changed
        if (changed) runCatching { options.save() }
        return changed
    }

    /** The folder Polydium keeps its own choices in. */
    private fun polydiumDir(): Path = FabricLoader.getInstance().gameDir.resolve(POLYDIUM_DIR)

    private fun write(name: String, content: String): Boolean = runCatching {
        val dir = polydiumDir()
        dir.createParentDirectories()
        val file = dir.resolve(name)
        if (file.exists() && file.readText() == content) return@runCatching false
        file.writeText(content)
        true
    }.getOrDefault(false)
}
