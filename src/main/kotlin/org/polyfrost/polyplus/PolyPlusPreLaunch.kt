package org.polyfrost.polyplus

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.compat.ModConfigDefaults
import org.polyfrost.polyplus.compat.PolydiumDefaults
import org.polyfrost.polyplus.compat.RrlsConfigCompat

//? if fabric || ornithe {
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
//?}

class PolyPlusPreLaunch
//? if fabric || ornithe {
    : PreLaunchEntrypoint
//?}
{

    //? if fabric || ornithe {
    override
    //?}
    fun onPreLaunch() {
        PolyPlusSentry.markGameThread()
        PolyPlusSentry.initialize()
        RrlsConfigCompat.apply()
        ModConfigDefaults.apply()
        // Polydium reads its config while it initialises, so the low-end mode has to reach it now.
        runCatching { PolydiumDefaults.apply(FabricLoader.getInstance().configDir) }
            .onFailure { LOGGER.warn("Could not apply the Polydium defaults", it) }
    }

    companion object {
        private val LOGGER = LogManager.getLogger("PolyPlus/PreLaunch")
    }

}
