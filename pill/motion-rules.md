# Motion rules for a 20x20 pixel pet

Follow these rules literally. Units: cell = one grid cell; t = key-table time (0..1); s = seconds.
A Roam lasts 3.5 to 5 s, so t 0.01 is about 0.04 s. A key every 0.03 to 0.08 t is 0.13 to 0.34 s.
Tags: [M] measured from `PixelMonkeyRoam.kt` (good) or `PixelBirdRoam.kt` (weak). [S] from a source project, rescaled. [R] rule of this guide: check it with your eyes.

## 1. Plan before you draw
1. Write ONE sentence: "The pet does X." One event only. A second event is a second action.
2. Write the beat list with times. Monkey [M]: lead-in 0.05, anticipation 0.10, main swing 0.59, wind-up 0.10, flip 0.11, settle 0.05.
3. Every big move gets a beat before it that goes the other way (anticipation) and a beat after it that overshoots then rests (follow-through, settle).
4. Hold a pose only on purpose: after a big move (settle), or before a snap. Hold at most 0.05 t. Monkey has two holds in 21 key pairs [M].
5. Start and end on built-in sprites at `10 19`, turn 0. No jump at hand-over.

## 2. Hard minimums
Between two neighbour keys at least ONE must be true [R, from M]:
- the sprite differs by 25 or more cells (monkey swing sprites differ by 35 to 43 [M]; bird `mid`->`down` differs by only 20 and looks still [M]);
- the pin moves 2 or more cells;
- the turn changes 25 degrees or more (monkey swing turns 34 degrees per key [M]).
Anticipation may be smaller: 1 cell AND 14 degrees [M, keys 0.09 and 0.12].
- The largest part (body, wing, whole pet) moves 4 or more cells, or turns 90 or more degrees, at the main event. Monkey flip: 180 degrees in 0.105 t [M].
- A wing, arm or tail sprite differs from its neighbour by 40 or more cells of silhouette. Bird `up`->`mid` is 47 and reads; 20 does not [M].
- Two neighbour keys with the same sprite AND a pin within 1 cell AND a turn within 10 degrees are forbidden. Merge them or use `hold` on purpose [R].
- Bob or rise of the whole body: 1 cell or more in total. Under 1 cell does not read; bird bob is 0.8 [M]. Source numbers of 0.1 cell are invisible at this size [S].

## 3. Recipes
- Breath/idle: body rises 1 cell and falls over 1.7 s [S period]. Give head and tail other periods (2.3 s, 1.9 s) so nothing moves in step [S].
- Blink: closed eyes for 0.13 s every 3.5 s [S]. In a 4 s action: at most one blink. Closed eye = one `k` row removed. Use a blink sprite, no morph.
- Walk: one sine, period 0.8 s [S]. Legs swap sprite twice per period. The body is lowest at each foot-down: bob 1 cell [R]. A leg lifts only in the half of the cycle where it moves forward [S]. A shadow moves with the feet [S].
- Jump: ONE curve drives body, limbs and shadow [S]. Crouch before: lose 2 to 4 rows of 20 (monkey `squash` 18 rows, `tuck` 16 rows) [M]; move 1 cell back or lean 14 degrees. Stretch in the air: 1 to 2 rows taller [R]. Air time 0.3 to 0.5 s [R]. Landing squash: lower and wider, 0.04 t or more, then `stand` [M: `land` 0.045 t].
- Swing/hang: the pin is the gripping hand and it does not move (monkey x 13, y 20 for 0.15 to 0.80 t [M]). Row 22 is the pill's lower edge, so y 20 puts the hand 2 rows inside it (see WHERE THINGS ARE in `PixelAction.kt`). Period 0.24 t (about 1 s). Turn goes -12 to 22 to -12 to -46 [M]. The free limbs and tail sprite changes at the centre key, one key (0.06 t) after the body reached its extreme, and swaps side each pass [M].
- Flap: three sprites `up`, `level`, `down` in order up, level, down, level [M]. Each differs from the next by 40 cells or more [R]. Wing tip is 2 cells wide. Period 0.08 to 0.12 t [R; bird uses 0.12]. Body bob goes the OPPOSITE way to the wing tip: wings down, body up [M: bird y 16.2 on `down`]. Bob 1.5 cells or more [R].
- Look/turn head: the eyes and mouth move 1 to 2 cells in the face (7 cells change) AND the body turns 34 degrees from the key before [M: -12 to 22]. Hold the look 0.05 t [R].
- Land and settle: reach the low point (`land`), overshoot 1 cell past home or 1 row too low, then `out` to `stand` [R]. The landing pose lasts 0.045 t [M].

## 4. Timing and easing
- `out`: arrival (the move ends slow). Use it for landing, stopping, reaching the grip.
- `in`: departure (starts slow). Use it for a throw, a fall, a spin start.
- `smooth`: lazy things: breath, sit to stand, a lean.
- `line`: steady things only: a walk step, a flap stroke, the key before `hold`.
- `hold`: a freeze, then a snap at the next key.
- Pendulum [M]: leave an extreme with `in`, leave the centre with `out`. It is fast in the middle and slow at the ends.
- Exits are faster than entrances: wind-up 0.06 t, then the spin 0.11 t, then the landing 0.045 t [M].
- Offsets: a part follows the body by 0.03 to 0.06 t (half to one key) [R, from M]. Never switch all parts at the same key.

## 5. Silhouette (20x20)
- The acting limb sticks out of the body outline by 3 or more cells. A limb inside the body is invisible.
- The body is the same size in every sprite, 12 to 16 cells tall [PixelAction.kt]. Only squash and tuck may change it, by at most 20% [M].
- Eyes and face stay readable in every sprite: two `k` cells on a light face; never cover them.
- A 1-cell detail disappears. Hands, feet and wing tips are 2 cells.
- A dark part gets a light rim cell; a light part gets a dark outline cell [PixelAction.kt].

## 6. Review loop
Make the frame strip first with `./pill.sh strip <state>`: 12 frames of the real action on the emulator, each with its number and seconds, the pill outlined in grey. For a per-key check, also list each key's number, t, x, y, turn.
AUTHOR checklist, run on your own strip:
1. Each key pair: count changed cells, pin move, turn change. Below the minimums in section 2: fault.
2. Name the pet's action from each beat frame alone. Cannot name it: redraw it.
3. Is there a beat before the main move that goes the other way?
4. Does a part lag or overshoot? If all parts switch at the same key: fault.
5. Does the first key and the last key sit at `10 19`, turn 0, built-in sprite?
6. Is the largest part moving 4 cells or 90 degrees at the main event?
INDEPENDENT REVIEWER checklist (you did not draw it; you see only the strip). Write PASS or FAIL with the frame number on each line:
1. For each beat: "What is the pet doing in frame k?" Write your answer. Wrong or "nothing": FAIL.
2. Frame k to k+1: does anything change that you can see? No: FAIL, give k.
3. Is there anticipation before the main move? Name the frame.
4. Does a limb, tail or wing lag or overshoot? Name the frame.
5. Do the eyes show in every frame? Does the limb leave the body outline?
6. Start and end at home with no jump? Compare frame 1 with the built-in `sit`, last frame with `stand`.
7. More than 1 in 10 key pairs are holds: FAIL.

Method ideas credit: bang-motion (bangtutorial) and huashu-art-motion (alchaincyf), both MIT.
