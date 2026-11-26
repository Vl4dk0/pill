package sk.vladko.pill.actions

import android.content.Context
import android.content.Intent
import android.util.Log
import sk.vladko.pill.system.Torch

const val APP_ACTION_PREFIX = "app:"

/** What an action may use. [global] runs an accessibility global action; it is null while the service is off. */
class ActionEnv(
    val context: Context,
    val global: ((Int) -> Boolean)?,
    val torch: Torch,
)

/**
 * How an action behaves.
 * Toggle: flips something and has an on/off state. Step: moves to the next value (no on/off state).
 * Command: does one thing. Opens: opens a settings page or an app.
 */
sealed interface Behaviour {
    class Toggle(
        val isOn: (ActionEnv) -> Boolean,
        val set: (ActionEnv, Boolean) -> Unit,
    ) : Behaviour

    class Step(
        val next: (ActionEnv) -> Unit,
    ) : Behaviour

    class Command(
        val keepsMenu: Boolean = false,
        val delayMs: Long = 0,
        val run: (ActionEnv) -> Unit,
    ) : Behaviour

    class Opens(
        val intent: (ActionEnv) -> Intent?,
    ) : Behaviour
}

/** Where an action sits in the picker. */
enum class ActionGroup(
    val title: String,
) {
    Switches("Switches"),
    Display("Display"),
    System("System buttons"),
    Media("Media and sound"),
    Pages("Settings pages"),
    Places("Open something"),
}

/**
 * One thing Pill can do. [icon] is a Material 24x24 path for `Glyph(...)`; an app action has no icon path:
 * use [appPackage] (the app's own icon from `Apps`).
 */
class PillAction(
    val id: String,
    val name: String,
    /** Short label for a menu tile. */
    val short: String,
    val icon: String,
    val group: ActionGroup,
    val description: String,
    val does: Behaviour,
    /** Null when the action works now; else the reason it cannot (shown to the user, never hidden silently). */
    val unavailable: (ActionEnv) -> String? = { null },
    val appPackage: String? = null,
) {
    /** Printed in `state.actions[].kind`. */
    val kindName: String
        get() =
            when (does) {
                is Behaviour.Toggle -> "toggle"
                is Behaviour.Step -> "step"
                is Behaviour.Command -> "command"
                is Behaviour.Opens -> "opens"
            }

    /** True when tapping this tile in the Actions menu leaves the menu open. */
    val keepsMenu: Boolean
        get() =
            when (does) {
                is Behaviour.Toggle, is Behaviour.Step -> true
                is Behaviour.Command -> does.keepsMenu
                is Behaviour.Opens -> false
            }

    /** Wait after closing the menu before running (e.g. screenshot leaves the menu out of the picture). */
    val delayMs: Long
        get() = (does as? Behaviour.Command)?.delayMs ?: 0L

    /** On/off for a Toggle; null for any other kind. Reads the live system state, so call it again to refresh. */
    fun isOn(env: ActionEnv): Boolean? = (does as? Behaviour.Toggle)?.isOn?.invoke(env)

    /** Runs the action. Does nothing (and logs) when it is unavailable. */
    fun run(env: ActionEnv) {
        unavailable(env)?.let {
            Log.w("PILL", "action $id unavailable: $it")
            return
        }
        runCatching {
            when (val b = does) {
                is Behaviour.Toggle -> b.set(env, !b.isOn(env))
                is Behaviour.Step -> b.next(env)
                is Behaviour.Command -> b.run(env)
                is Behaviour.Opens -> {
                    val intent = b.intent(env) ?: return
                    env.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }.onFailure { Log.w("PILL", "action $id failed", it) }
    }
}
