package sk.vladko.pill.rules

// Pure Kotlin: validated stored settings. No Android classes, so plain JVM tests run it.

const val DEFAULT_IDLE_WIDTH_DP = 156f
const val MIN_IDLE_WIDTH_DP = 40f
const val MAX_IDLE_WIDTH_DP = 240f
const val MAX_IDLE_HEIGHT_DP = 56f
const val MAX_MENU_ITEMS = 4
val DEFAULT_ACTIONS_MENU = listOf("torch", "screenshot", "lock", "power")
const val DEFAULT_PET_ANIMAL = "cat"

/** Colour theme of the settings screen (the pill itself is always black). */
enum class ThemeMode(
    val code: String,
    val label: String,
) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark"),
    ;

    companion object {
        fun parse(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: System
    }
}

/** All stored settings, validated. */
data class Settings(
    val idleWidthDp: Float = DEFAULT_IDLE_WIDTH_DP,
    /** Null means default (hole height + 10 dp). */
    val idleHeightDp: Float? = null,
    val collapsed: Boolean = false,
    val theme: ThemeMode = ThemeMode.System,
    /** The light that runs around the pill when something new appears. */
    val arrivalGlow: Boolean = true,
    /** Always contains all five slots. */
    val gestures: Map<IdleSlot, Choice> = defaultGestures(Choice.Nothing),
    val appMenu: List<String> = emptyList(),
    val actionsMenu: List<String> = DEFAULT_ACTIONS_MENU,
    val playersSeen: List<String> = emptyList(),
    val playersOff: Set<String> = emptySet(),
    /** The pet lives in the idle pill and dances in the music pill; off leaves the plain black pill and the music bars. */
    val pet: Boolean = true,
    /** Which animal the pet is: the id of a pet animal. */
    val petAnimal: String = DEFAULT_PET_ANIMAL,
) {
    /** Only Pill's own preference keys, with today's names. Other keys in the file are untouched. */
    fun encode(): Map<String, Any?> =
        buildMap {
            put("idle_width_dp", idleWidthDp)
            put("idle_height_dp", idleHeightDp)
            put("collapsed", collapsed)
            put("theme", theme.code)
            put("arrival_glow", arrivalGlow)
            IdleSlot.entries.forEach { slot ->
                val choice = gestures[slot]
                if (choice != null) put("gesture_${slot.id}", choice.code)
            }
            put("fast_apps", appMenu.joinToString(","))
            put("quick_actions", actionsMenu.joinToString(","))
            put("players_seen", playersSeen.joinToString(","))
            put("players_off", playersOff.joinToString(","))
            put("pet", pet)
            put("pet_animal", petAnimal)
        }

    companion object {
        private fun defaultFor(
            slot: IdleSlot,
            holdRightDefault: Choice,
        ): Choice =
            when (slot) {
                IdleSlot.TapLeft -> Choice.AppMenu
                IdleSlot.TapRight -> Choice.ActionsMenu
                IdleSlot.HoldLeft -> Choice.Recents
                IdleSlot.HoldRight -> holdRightDefault
                IdleSlot.Volume -> Choice.HideIdle
            }

        fun defaultGestures(holdRightDefault: Choice): Map<IdleSlot, Choice> =
            IdleSlot.entries.associateWith { defaultFor(it, holdRightDefault) }

        /** [raw] is SharedPreferences.all. A bad value falls back to its default. [holdRightDefault] is used when no hold-right gesture is stored. */
        fun parse(
            raw: Map<String, Any?>,
            holdRightDefault: Choice,
        ): Settings {
            val width =
                (raw["idle_width_dp"] as? Number)
                    ?.toFloat()
                    ?.takeIf { it.isFinite() }
                    ?.coerceIn(MIN_IDLE_WIDTH_DP, MAX_IDLE_WIDTH_DP)
                    ?: DEFAULT_IDLE_WIDTH_DP
            val height =
                (raw["idle_height_dp"] as? Number)
                    ?.toFloat()
                    ?.takeIf { it.isFinite() && it >= 0f }
                    ?.coerceAtMost(MAX_IDLE_HEIGHT_DP)
            val collapsed = (raw["collapsed"] as? Boolean) ?: false
            val theme = ThemeMode.parse(raw["theme"] as? String)
            val arrivalGlow = (raw["arrival_glow"] as? Boolean) ?: true
            val gestures =
                IdleSlot.entries.associateWith { slot ->
                    (raw["gesture_${slot.id}"] as? String)?.let(Choice::parse) ?: defaultFor(slot, holdRightDefault)
                }
            val appMenu = splitList(raw["fast_apps"] as? String).take(MAX_MENU_ITEMS)
            val actionsRaw = raw["quick_actions"] as? String
            val actionsMenu = if (actionsRaw == null) DEFAULT_ACTIONS_MENU else splitList(actionsRaw).take(MAX_MENU_ITEMS)
            val playersSeen = splitList(raw["players_seen"] as? String)
            val playersOff = splitList(raw["players_off"] as? String).toSet()
            // Before the pet was the only idle view, a list of idle view ids said whether it was on.
            val pet = (raw["pet"] as? Boolean) ?: (raw["idle_views"] as? String)?.let { "pet" in splitList(it) } ?: true
            // The first release stored one id such as "smoothcat" (look then animal); the animal is its tail.
            val oldPet = raw["pet_style"] as? String
            val petAnimal =
                (raw["pet_animal"] as? String ?: oldPet?.let { old -> PetAnimal.ids.firstOrNull { old.endsWith(it) } })
                    ?.takeIf { it in PetAnimal.ids } ?: DEFAULT_PET_ANIMAL
            return Settings(
                idleWidthDp = width,
                idleHeightDp = height,
                collapsed = collapsed,
                theme = theme,
                arrivalGlow = arrivalGlow,
                gestures = gestures,
                appMenu = appMenu,
                actionsMenu = actionsMenu,
                playersSeen = playersSeen,
                playersOff = playersOff,
                pet = pet,
                petAnimal = petAnimal,
            )
        }

        private fun splitList(s: String?): List<String> =
            s
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .distinct()
    }
}
