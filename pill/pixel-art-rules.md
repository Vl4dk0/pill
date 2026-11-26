# Pixel-art rules for a 20x20 pet (addendum to motion-rules.md)

Units: cell = one grid cell. Tags: [S1] [S2] sources at the end. [R] = our own rule: check it with your eyes.
This page does not repeat motion-rules.md. It covers drawing, palette, clean cells and animal notes.

## 1. A tiny animal that reads at a glance
1. Draw the silhouette first, in ONE colour. Name the animal from it alone. Cannot name it: redraw. [S1]
2. Head is 40 to 50 percent of the pet's height (cute proportion). Head 7 to 9 cells wide, body 8 to 11 cells long. [S1]
3. Put the eyes in the LOWER half of the head. Low eyes under a big forehead say "small and cute". [S1]
4. Eyes are 2x2 cells at this size (1x2 for a tired or small look). Never cut the eyes; cut brows, nose, whiskers first. [S1]
5. One bright cell (white) at the top-left of each pupil, same place in both eyes. Hide it on blink and on sleep. [S1]
6. Pupil 1 cell off centre looks alive. Centred pupil on a front face is a dead stare. [S1]
7. A mouth needs 1 empty cell above and below. Face has 3 to 5 inner cells in total. Skip inner outlines. [S1]
8. Make ONE feature too big. It flips the species. [S1]
   - Cat: round head, two triangle ears 3 cells tall, a long tail that curls up. Muzzle is tiny (1 cell).
   - Dog: a muzzle as long as the skull (3 cells), ears that hang or flop, a thick tail.
   - Bird: egg body, ball head, a 2x1 cell beak in its own colour (use that colour nowhere else). Legs 1 cell thin, ankle kink points BACK. [S1]
   - Monkey: big round head, two side ears (1 cell each), a pale face patch, long arms that reach the knee, a long tail with a curled tip. [R]
9. Two limbs whose outlines touch fuse into one blob. Leave a 1-cell gap, or overlap them clearly. Keep tail and hind leg apart too. [S1]
10. Feet are one block of 2 to 3 cells. Never draw toes. A walking cat shows NO claw cells. [S1]
11. A limb or feature under 2 cells wide is noise. Exceptions: eye, catch-light, whisker tip. [S1]
12. Break symmetry: one ear tilted, tail on one side, head turned 1 cell. [S1]

## 2. Palette
1. Use 8 to 12 colours per animal. Per material (fur, belly, skin): 3 steps, light, mid, dark. [S1]
2. Judge values in grey first. If the grey picture mushes, change values, not hue. [S1]
3. Shadows go cooler and a little duller (toward blue or purple). Lights go warmer (toward yellow). Never "same hue, darker". [S1]
4. One light direction for ALL sprites of the pet: top-left. Light on the back and forehead; a 1-row shadow under the belly. [S1]
5. Reuse the darkest step on every material. It ties the pet together and costs no colour. [S1]
6. Outline: one dark colour, a deep tint of the fur (not pure black), 1 cell, on the outside only. [S1]
7. For black AND light wallpaper: add a rim. Dark fur gets a light rim cell on its top-left edge. Light fur keeps the dark outline. Two steps between body and the ground behind it. [S1][R]
8. No outer soft edge, no half-tone cell outside the outline: it makes a halo on the wrong wallpaper. Inside only. [S1]
9. Eyes, beak, face patch, cheeks get the one accent colour each. Accent is the only saturated colour. [S1]

## 3. Clean cells
1. No stray single cells. Each cell touches a same-colour cell by an edge. Allowed alone: eye, catch-light, beak tip. At most one per sprite. [S1]
2. Straight lines use ONE run length: 2,2,2 or 3,3,3. Never 3,2,4,1. Fix a bump by redrawing the whole segment. [S1]
3. Curves: runs shrink, then grow, like 4,2,1,2,4. A 2,1,2 pattern is a jag: delete or move one cell. [S1]
4. Banding: two shade edges of equal length ending on the same row. Shift one end 1 cell, or shorten it. [S1]
6. Anti-alias only a step of 3+ cells, 1 cell long, inside the outline. None on 45 degree lines. [S1]
7. Never rotate a sprite by a formula. Only 90 degrees and mirrors are safe. [S1]
8. The engine turns whole sprites. Past about 30 degrees, draw the turned pose as its own sprite and keep `turn` small. [S1][R]
9. Same feet row, same body size, same palette in every sprite of one animal. Copy the body rows, change only the limb rows. [S1]
10. Count cells per row twice. Long runs of one letter are where models miscount. Write the grid, then re-read each row length. [S1]

## 4. Sub-pixel tricks (moves under 1 cell)
1. Move a SHADE boundary by 1 cell, not the outline. Needs 3 shades per material. Test: hide colour; if motion is gone, it was sub-pixel. [S1]
2. Breathing: the belly highlight moves down 1 cell and the chest row gets one darker cell. The outline stays. [S1][R]
3. Blink: swap the 2x2 eye for a 2x1 dark line. Hold 0.13 s. Do not move the head. [R]
4. Ear twitch: move 1 or 2 cells of the ear tip, not the ear. The root cells never move. [S1]
5. Tail tip sways 1 cell, 1 to 2 keys after the body; root cells never move. Sway 3, 2, 1 cells, then rest. [S1]
6. Shift ONE row or one limb at a time, never the whole body. Move the inside before or after the outline, not on the same key. [S1]
7. A new row on an edge must be 2 to 3 cells long. A single new cell reads as noise. [S1]
9. Keep the face on whole cells. Let the chest or tail move half-steps while the head holds. [S1]
10. Mass stays constant: cell count changes by 2 percent at most between sprites, except squash. [S1]

## 5. Animal notes (key poses; times in s)
- CAT walk: a low log body, head 1 cell lower than the back. Lateral order: hind, fore, other hind, other fore. Far legs one shade darker. Body bob 1 cell. Tail up with the tip lagging. [S1]
- CAT jump: crouch low, tail down and back, hind legs push, body stretches 1 to 2 cells, land on the front paws first, tail follows 1 key later. Cats get high, light arcs. [S1]
- CAT gestures: groom = one front paw to the mouth, head tilts 14 degrees, 2 sprites; stretch = front paws forward, rump up 2 cells; fidget = ear flick + tail tip. [R]
- DOG walk: front legs are columns; hind leg zigzags with one sharp hock cell. Chest and rump tilt opposite by 1 cell. Head and tail lag the chest by 1 key. [S1]
- DOG gestures: tail wags fast (period 0.08 t) while the body holds; sit = hind legs fold, front legs straight; heavy dog bounces less than a cat. [S1][R]
- BIRD walk: a biped with a back-bending ankle. The head thrusts forward on each step, then pulls back. [S1]
- BIRD flap: four poses: wings up (body lowest), mid, down (body highest 2 cells), mid. Wing = 2 segments, tip trails 20 to 35 degrees. Small bird beats fast. Hover: 1 cell bob, no flap. [S1]
- BIRD at rest: wings folded in a Z along the tail root; fluff breath is 1 row. [S1]
- MONKEY walk: knuckle walk, arms long, body low, head forward; or sit with a scratch gesture (one hand to the head, 2 sprites). Hands and feet are 2-cell mitten blocks. [R]
- MONKEY swing: the gripping arm is a straight line from pin to shoulder; the body hangs under it; the free arm and the tail trail on the other side. Tail tip lags 1 to 2 keys. [R]
- Walk loop must not look like a loop: give the tail or head another period than the legs. [S1]
- Heavy gestures (sleep, tired) hold longer. Light and quick ones (startle, excited) change in 1 key. [S1]

## 6. How many sprites an action needs
1. Idle or breath: 2 sprites (body A, body B). Add a third only for a blink. [S1]
2. Walk: 4 sprites (contact, pass, contact, pass with other legs). Reuse the same body rows; swap only the 4 leg rows. [S1]
3. Jump: 4 (crouch, stretch, tuck or apex, land). Fly: 3 to 4 (up, level, down). [S1][R]
4. Gesture (groom, stretch): 2 to 3 sprites. Head turns: 2 (front, turned). [R]
6. Build one base body. Every other sprite copies its rows and edits less than 40 percent of cells. A sprite that is new from scratch has drift risk. [S1][R]

## Sources
- [S1] https://github.com/with-pebbly/aseprite-ai-artist (folder `rules/`, 740 stars, MIT, pushed 2026-11). Files 03, 10, 11, 12, 14, 20, 33, 41, 45, 46, 50, 51, 52, 53, 75. Cites Saint11, Pixel Logic, Williams, Blair, Hultgren.
- [S2] https://github.com/paean-ai/deeptide, `skills/pixel-art-code/SKILL.md` (1112 stars, no licence file found). Generic: outline first, palette, grayscale test.
- Checked and rejected (video or UI motion, not character pixel art): bangtutorial/bang-motion, alchaincyf/huashu-art-motion (MIT).
