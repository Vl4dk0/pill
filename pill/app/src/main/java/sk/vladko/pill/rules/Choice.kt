package sk.vladko.pill.rules

/** The five gestures of the idle pill (the fifth is both volume keys pressed together). [id] is the name in state.gestures, pill.sh and the stored key. */
enum class IdleSlot(
    val id: String,
) {
    TapLeft("tapLeft"),
    TapRight("tapRight"),
    HoldLeft("holdLeft"),
    HoldRight("holdRight"),
    Volume("volume"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** What one gesture does. [Action] runs one catalog action at once (`app:<package>` opens that app). */
sealed interface Choice {
    val code: String

    data object AppMenu : Choice {
        override val code = "appmenu"
    }

    data object ActionsMenu : Choice {
        override val code = "actionsmenu"
    }

    /** On a hold: hold-drag-release picker. On a tap: a normal tap menu. */
    data object Recents : Choice {
        override val code = "recents"
    }

    data object Nothing : Choice {
        override val code = "none"
    }

    /** Collapse the idle pill to nothing, or bring it back. */
    data object HideIdle : Choice {
        override val code = "hideidle"
    }

    data class Action(
        val id: String,
    ) : Choice {
        override val code = "action:$id"
    }

    companion object {
        fun parse(code: String): Choice? =
            when {
                code == "appmenu" -> AppMenu
                code == "actionsmenu" -> ActionsMenu
                code == "recents" -> Recents
                code == "none" -> Nothing
                code == "hideidle" -> HideIdle
                code.startsWith("action:") && code.length > 7 -> Action(code.removePrefix("action:"))
                else -> null
            }
    }
}
