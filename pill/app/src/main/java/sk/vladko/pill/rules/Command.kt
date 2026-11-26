package sk.vladko.pill.rules

/** Typed broadcast commands accepted by `CommandReceiver` (sent by `pill.sh`). */
sealed interface Command {
    data class SetGesture(
        val slot: IdleSlot,
        val choice: Choice,
    ) : Command

    data class SetActionsMenu(
        val ids: List<String>,
    ) : Command

    data class SetAppMenu(
        val packages: List<String>,
    ) : Command

    data object ClearNotifications : Command

    data class RunAction(
        val id: String,
    ) : Command

    /** Sets the pet to [animal] (an id of a pet animal; the receiver checks it). */
    data class SetPetAnimal(
        val animal: String,
    ) : Command

    /** Makes the pet switch to [state] now. */
    data class Pet(
        val state: PetState,
    ) : Command

    /** Makes the pet react to [event] as if it happened. */
    data class PetReact(
        val event: PetEvent,
    ) : Command

    /** Makes the idle pet dance for a while, as it does in the music pill. */
    data object PetDance : Command

    companion object {
        /**
         * Parses one command intent. [action] is the intent action, [extra] reads a string extra, and
         * [knownAction] reports whether an id (catalog id or `app:<pkg>`) exists.
         */
        fun parse(
            action: String?,
            extra: (String) -> String?,
            knownAction: (String) -> Boolean,
        ): Result<Command> =
            when (action) {
                "sk.vladko.pill.SET_GESTURE" -> {
                    val slot =
                        IdleSlot.of(extra("slot").orEmpty())
                            ?: return Result.failure(IllegalArgumentException("slot is one of ${IdleSlot.entries.joinToString { it.id }}"))
                    val code = extra("choice").orEmpty()
                    val choice =
                        when (val parsed = Choice.parse(code)) {
                            is Choice.Action -> if (knownAction(parsed.id)) parsed else null
                            null -> if (knownAction(code)) Choice.Action(code) else null
                            else -> parsed
                        } ?: return Result.failure(IllegalArgumentException("unknown choice '$code'"))
                    Result.success(SetGesture(slot, choice))
                }
                "sk.vladko.pill.SET_ACTIONS_MENU" -> {
                    val ids = extra("ids").orEmpty().split(",").filter { it.isNotBlank() }
                    val bad = ids.firstOrNull { !knownAction(it) }
                    if (bad != null) {
                        Result.failure(IllegalArgumentException("unknown action '$bad'"))
                    } else {
                        Result.success(SetActionsMenu(ids))
                    }
                }
                "sk.vladko.pill.SET_APP_MENU" -> {
                    val pkgs = extra("pkgs").orEmpty().split(",").filter { it.isNotBlank() }
                    Result.success(SetAppMenu(pkgs))
                }
                "sk.vladko.pill.CLEAR_NOTIFICATIONS" -> Result.success(ClearNotifications)
                "sk.vladko.pill.RUN_ACTION" -> {
                    val id = extra("id").orEmpty()
                    if (!knownAction(id)) {
                        Result.failure(IllegalArgumentException("unknown action '$id'"))
                    } else {
                        Result.success(RunAction(id))
                    }
                }
                "sk.vladko.pill.SET_PET_ANIMAL" -> {
                    val animal = extra("animal").orEmpty()
                    if (animal.isBlank()) {
                        Result.failure(IllegalArgumentException("animal is needed"))
                    } else {
                        Result.success(SetPetAnimal(animal))
                    }
                }
                "sk.vladko.pill.PET" -> {
                    val state =
                        PetState.of(extra("state").orEmpty())
                            ?: return Result.failure(IllegalArgumentException("state is one of ${PetState.entries.joinToString { it.id }}"))
                    Result.success(Pet(state))
                }
                "sk.vladko.pill.PET_EVENT" -> {
                    val id = extra("event").orEmpty()
                    if (id == "dance") return Result.success(PetDance)
                    val event =
                        PetEvent.of(id)
                            ?: return Result.failure(
                                IllegalArgumentException("event is one of ${PetEvent.entries.joinToString { it.id }}, dance"),
                            )
                    Result.success(PetReact(event))
                }
                else -> Result.failure(IllegalArgumentException("unknown command"))
            }
    }
}
