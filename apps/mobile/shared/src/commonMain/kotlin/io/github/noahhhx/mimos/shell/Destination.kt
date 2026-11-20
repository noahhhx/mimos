package io.github.noahhhx.mimos.shell

enum class Placement {
    /** In the tab bar, as the web's app navigation (`app-nav.tsx`). */
    Tab,
    /** In the profile menu (`profile-menu.tsx`). */
    Profile,
}

/**
 * Every place the web's signed-in area has, with its label, in the order the web lists them, then
 * Settings, which only the app has.
 */
enum class Destination(val label: String, val placement: Placement) {
    Kitchen("Kitchen", Placement.Tab),
    Recipes("Recipes", Placement.Tab),
    Plan("Plan", Placement.Tab),
    Shopping("Shopping", Placement.Tab),
    Log("Log", Placement.Tab),
    Household("Household", Placement.Profile),
    Plugins("Plugins", Placement.Profile),
    Fuel("Fuel", Placement.Profile),
    YourData("Your data", Placement.Profile),
    Settings("Settings", Placement.Profile);

    companion object {
        val tabs: List<Destination> = entries.filter { it.placement == Placement.Tab }
        val profileMenu: List<Destination> = entries.filter { it.placement == Placement.Profile }
        val home: Destination = Kitchen
    }
}
