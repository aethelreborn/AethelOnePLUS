package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import net.minecraft.client.gui.screens.TitleScreen
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.navigation.NavigationGroup
import org.polyfrost.oneconfig.api.ui.v1.navigation.NavigationRoute
import org.polyfrost.oneconfig.api.ui.v1.navigation.get
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent

object PolyPlusOneConfigIntegration {
    private val cosmeticsRoute = NavigationRoute(
        id = "cosmetics",
        icon = "assets/polyplus/ico/stars.svg",
        route = PolyPlusCosmeticsRoute,
    )

    @JvmStatic
    fun navigationGroups(original: List<NavigationGroup>): List<NavigationGroup> {
        if (!PrivacyConsent.allowsOnlineServices()) return original

        if (original.any { group -> group.routes.any { it.route == PolyPlusCosmeticsRoute } }) {
            return original
        }

        return original.map { group ->
            if (group.id == "Personalization") {
                val routes = group.routes.toMutableList()
                routes.add(minOf(1, routes.size), cosmeticsRoute)
                NavigationGroup(group.id, *routes.toTypedArray())
            } else {
                group
            }
        }
    }

    @JvmStatic
    fun addRoutes(builder: NavGraphBuilder) {
        if (!PrivacyConsent.allowsOnlineServices()) return
        builder.polyPlusCosmeticsGraph()
    }

    @JvmStatic
    fun openCosmetics() = openOneConfig(PolyPlusCosmeticsRoute)

    fun openCosmeticsFromKeybind() {
        when (ClientPlatform.currentScreen()) {
            null, is TitleScreen, is PolyPlusMainMenuScreen -> openCosmetics()
            else -> Unit
        }
    }

    @JvmStatic
    fun openMods() = OneConfigUI.openMods()

    private fun openOneConfig(route: Any) {
        OneConfigUI.open(route)
    }
}

fun NavGraphBuilder.polyPlusCosmeticsGraph() {
    composable<PolyPlusCosmeticsRoute> {
        PolyPlusCosmeticsScreen()
    }
}
