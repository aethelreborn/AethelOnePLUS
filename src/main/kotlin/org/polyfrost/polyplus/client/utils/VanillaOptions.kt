package org.polyfrost.polyplus.client.utils

import java.lang.reflect.Field

/**
 * Minecraft's own video options changed shape over the years: 1.8.9 keeps plain
 * public fields on the options object while the modern versions hand out option
 * accessors that wrap a value. Every lookup here goes by the option name and
 * tries both shapes, so one call works on every supported game version and a
 * version that does not have the option at all is skipped instead of failing.
 */
object VanillaOptions {
    const val FRAME_RATE_LIMIT = "framerateLimit"
    const val FRAME_RATE_LIMIT_LEGACY = "fpsLimit"
    const val ENTITY_SHADOWS = "entityShadows"
    const val RENDER_DISTANCE = "renderDistance"
    const val SIMULATION_DISTANCE = "simulationDistance"
    const val PARTICLES = "particles"
    const val MIPMAP_LEVELS = "mipmapLevels"
    const val GRAPHICS_MODE = "graphicsMode"

    /** The particle levels, from the most to the least expensive. */
    val PARTICLE_LEVELS = listOf("ALL", "DECREASED", "MINIMAL")

    fun intOption(options: Any?, vararg names: String): Int? {
        if (options == null) return null
        for (name in names) {
            val holder = holder(options, name)
            if (holder != null) {
                val value = holder.value()
                if (value is Number) return value.toInt()
                continue
            }
            val field = field(options, name) ?: continue
            if (field.type == Integer.TYPE) return field.getInt(options)
        }
        return null
    }

    fun setIntOption(options: Any?, value: Int, vararg names: String): Boolean {
        if (options == null) return false
        var changed = false
        for (name in names) {
            val holder = holder(options, name)
            if (holder != null) {
                changed = holder.write(value) || changed
                continue
            }
            val field = field(options, name) ?: continue
            if (field.type == Integer.TYPE) {
                field.setInt(options, value)
                changed = true
            }
        }
        return changed
    }

    fun boolOption(options: Any?, vararg names: String): Boolean? {
        if (options == null) return null
        for (name in names) {
            val holder = holder(options, name)
            if (holder != null) {
                val value = holder.value()
                if (value is Boolean) return value
                continue
            }
            val field = field(options, name) ?: continue
            if (field.type == java.lang.Boolean.TYPE) return field.getBoolean(options)
        }
        return null
    }

    fun setBoolOption(options: Any?, value: Boolean, vararg names: String): Boolean {
        if (options == null) return false
        var changed = false
        for (name in names) {
            val holder = holder(options, name)
            if (holder != null) {
                changed = holder.write(value) || changed
                continue
            }
            val field = field(options, name) ?: continue
            if (field.type == java.lang.Boolean.TYPE) {
                field.setBoolean(options, value)
                changed = true
            }
        }
        return changed
    }

    /** The name of an enum option, or its number when the version keeps it as a plain field. */
    fun enumOption(options: Any?, vararg names: String): String? {
        if (options == null) return null
        for (name in names) {
            val holder = holder(options, name)
            if (holder != null) {
                val value = holder.value()
                if (value is Enum<*>) return value.name
                continue
            }
            val field = field(options, name) ?: continue
            if (field.type == Integer.TYPE) return field.getInt(options).toString()
        }
        return null
    }

    fun setEnumOption(options: Any?, constant: String, vararg names: String): Boolean {
        if (options == null) return false
        for (name in names) {
            val holder = holder(options, name)
            if (holder != null) {
                val value = holder.value()
                if (value !is Enum<*>) continue
                val target = value.javaClass.enumConstants
                    ?.firstOrNull { (it as Enum<*>).name == constant } ?: continue
                return holder.write(target)
            }
            val field = field(options, name) ?: continue
            if (field.type == Integer.TYPE) {
                val number = constant.toIntOrNull() ?: continue
                field.setInt(options, number)
                return true
            }
        }
        return false
    }

    /** The particle level, counted from the most expensive one down. */
    fun particleLevel(options: Any?): Int? {        if (options == null) return null
        val holder = holder(options, PARTICLES)
        if (holder != null) {
            val value = holder.value()
            if (value is Enum<*>) return PARTICLE_LEVELS.indexOf(value.name).takeIf { it >= 0 }
        }
        val field = field(options, PARTICLES) ?: return null
        if (field.type != Integer.TYPE) return null
        return field.getInt(options)
    }

    fun setParticleLevel(options: Any?, level: Int): Boolean {
        if (options == null) return false
        val name = PARTICLE_LEVELS.getOrElse(level) { PARTICLE_LEVELS.first() }
        val holder = holder(options, PARTICLES)
        if (holder != null) {
            val value = holder.value()
            if (value is Enum<*>) {
                val target = value.javaClass.enumConstants
                    ?.firstOrNull { (it as Enum<*>).name == name } ?: return false
                return holder.write(target)
            }
        }
        val field = field(options, PARTICLES) ?: return false
        if (field.type != Integer.TYPE) return false
        field.setInt(options, level)
        return true
    }

    /** An option accessor, which holds the value behind a getter and a setter. */
    private class Holder(private val instance: Any) {
        private val getter = instance.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 0 }
        private val setter = instance.javaClass.methods.firstOrNull { it.name == "set" && it.parameterCount == 1 }

        fun value(): Any? = getter?.let { runCatching { it.invoke(instance) }.getOrNull() }

        fun write(value: Any): Boolean {
            val set = setter ?: return false
            return runCatching { set.invoke(instance, value) }.isSuccess
        }
    }

    private fun holder(options: Any, name: String): Holder? {
        val method = options.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: return null
        val instance = runCatching { method.invoke(options) }.getOrNull() ?: return null
        val getter = instance.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 0 } ?: return null
        val setter = instance.javaClass.methods.firstOrNull { it.name == "set" && it.parameterCount == 1 } ?: return null
        return Holder(instance)
    }

    private fun field(options: Any, name: String): Field? =
        runCatching { options.javaClass.getField(name) }.getOrNull()
}
