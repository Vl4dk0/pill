package sk.vladko.pill.idle

import androidx.compose.ui.geometry.Rect
import sk.vladko.pill.rules.PetAnimal

/**
 * The pixel scene that draws [animal] for a box of [w] by [h] pixels. [unit] fixes the pet's size (see [PetScene.unit]);
 * null lets the box give it. The state machine and the view do not depend on the animal.
 */
internal fun petScene(
    animal: PetAnimal,
    w: Float,
    h: Float,
    hole: Rect?,
    unit: Float? = null,
): PetScene =
    when (animal) {
        PetAnimal.Cat -> PixelCatScene(w, h, hole, unit)
        PetAnimal.Dog -> PixelDogScene(w, h, hole, unit)
        PetAnimal.Bird -> PixelBirdScene(w, h, hole, unit)
        PetAnimal.Monkey -> PixelMonkeyScene(w, h, hole, unit)
        PetAnimal.Clawd -> ClawdScene(w, h, hole, unit)
    }
