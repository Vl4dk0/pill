package sk.vladko.pill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sk.vladko.pill.actions.Catalog
import sk.vladko.pill.idle.PetIdle
import sk.vladko.pill.rules.Command
import sk.vladko.pill.rules.PetAnimal
import sk.vladko.pill.rules.PetState

/**
 * Lets `pill.sh` set a gesture, the App menu or the Actions menu and run an action by id, through the same SettingsStore the
 * settings screen uses. Only holders of the DUMP permission (adb shell, the system) may send to it; the result
 * text goes back as the broadcast result. Actions: SET_GESTURE (slot, choice), SET_ACTIONS_MENU (ids), SET_APP_MENU (pkgs), CLEAR_NOTIFICATIONS, RUN_ACTION (id), PET (state), PET_EVENT (event), SET_PET_ANIMAL (animal).
 */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(
        ctx: Context,
        i: Intent,
    ) {
        val result = runCatching { handle(ctx, i) }.getOrElse { "error: ${it.message}" }
        setResultData(result)
    }

    private fun handle(
        ctx: Context,
        i: Intent,
    ): String {
        val app = ctx.pill
        val cmd =
            Command
                .parse(i.action, i::getStringExtra) { id -> Catalog.byId(id, app.apps) != null }
                .getOrElse { return "error: ${it.message}" }
        return when (cmd) {
            is Command.SetGesture -> {
                app.settings.update { it.copy(gestures = it.gestures + (cmd.slot to cmd.choice)) }
                "ok"
            }
            is Command.SetActionsMenu -> {
                app.settings.update { it.copy(actionsMenu = cmd.ids) }
                "ok"
            }
            is Command.SetAppMenu -> {
                app.settings.update { it.copy(appMenu = cmd.packages) }
                "ok"
            }
            Command.ClearNotifications -> {
                if (app.notes.cancelAll()) "ok" else "unavailable: notification access is off"
            }
            is Command.SetPetAnimal -> {
                if (cmd.animal !in PetAnimal.ids) {
                    "error: animal is one of ${PetAnimal.ids.joinToString()}"
                } else {
                    app.settings.update { it.copy(petAnimal = cmd.animal) }
                    "ok"
                }
            }
            is Command.Pet -> PetIdle.play(cmd.state)
            is Command.PetReact -> PetIdle.event(cmd.event)
            Command.PetDance -> PetIdle.play(PetState.Dance)
            is Command.RunAction -> {
                val action = Catalog.byId(cmd.id, app.apps) ?: return "error: unknown action '${cmd.id}'"
                val env = app.actionEnv()
                val reason = action.unavailable(env)
                if (reason != null) {
                    "unavailable: $reason"
                } else {
                    app.session?.runAction(cmd.id, fromMenu = false) ?: action.run(env)
                    "ok"
                }
            }
        }
    }
}
