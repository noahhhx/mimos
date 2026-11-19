package io.github.noahhhx.mimos.shell

enum class Placement {
    /** In the tab bar, as the web's app navigation (`app-nav.tsx`). */
    Tab,
    /** In the profile menu (`profile-menu.tsx`). */
    Profile,
}

/**
 * Every place the web's signed-in area has, with its label and its page on the web, in the order
 * the web lists them, then Settings, which only the app has. Screens the app has not built yet send
 * people to [webPath].
 */
enum class Destination(val label: String, val webPath: String?, val placement: Placement) {
    Kitchen("Kitchen", "/app", Placement.Tab),
    Recipes("Recipes", "/app/recipes", Placement.Tab),
    Plan("Plan", "/app/plan", Placement.Tab),
    Shopping("Shopping", "/app/shopping-list", Placement.Tab),
    Log("Log", "/app/log", Placement.Tab),
    Household("Household", "/app/household", Placement.Profile),
    Plugins("Plugins", "/app/plugins", Placement.Profile),
    Fuel("Fuel", "/app/fuel", Placement.Profile),
    YourData("Your data", "/app/account", Placement.Profile),
    Settings("Settings", null, Placement.Profile);

    /** This page on the instance whose web address is [origin]; null for one the web lacks. */
    fun webUrl(origin: String): String? = webPath?.let { origin.trimEnd('/') + it }

    companion object {
        val tabs: List<Destination> = entries.filter { it.placement == Placement.Tab }
        val profileMenu: List<Destination> = entries.filter { it.placement == Placement.Profile }
        val home: Destination = Kitchen
    }
}
