# Pill: developer notes (file map and how to extend)

Package `sk.vladko.pill`, in `app/src/main/java/sk/vladko/pill/`.

## File map

| File | What it is |
|---|---|
| `PillApp.kt` | `Application`. Owns process-lifetime `SettingsStore`, `hiddenActivities` (ids the owner flicked away), `Apps`, `NoteFeed`, `Torch`, the two `BorrowedSetting`s, and the live `session`. |
| `PillAccessibilityService.kt` | Creates and closes `PillSession`; forwards window and key events; `dump()` prints `StateDump` JSON. |
| `PillNotificationListener.kt` | Forwards the four notification listener callbacks to `PillApp.notes`. |
| `CommandReceiver.kt` | `Command.parse` -> run -> broadcast result text (DUMP-guarded). |
| `MainActivity.kt` | Launcher activity: `setContent { SettingsScreen(...) }`. |
| `rules/Model.kt`, `rules/Choice.kt`, `rules/PillRules.kt` | Pure Kotlin (no `android.*` / `androidx.*`, enforced by `PureTest`). `handle(Input, now)` decides what the pill shows, the activity/idle carousel, idle gestures, collapsed state, screen visibility and arrival glow. |
| `rules/Settings.kt` | `Settings`, `ThemeMode`, limits/defaults, `parse` and `encode`. |
| `rules/Note.kt` | `Note`, `NoteAction`, `NoteTarget` (plain notification snapshot). |
| `rules/Layout.kt` | `ScreenGeometry`, `Hole`, `Want`, `Frame`, `HoleInfo`, `pillFrame()`, `holeInfo()`, `idleHeight()`. |
| `rules/VolumePair.kt` | Pure volume up+down 150 ms chord detector (`Verdict`). |
| `rules/Command.kt` | Typed `Command` sealed interface and `Command.parse`. |
| `engine/Engine.kt` | Runs `PillRules` against the real clock (`SystemClock.uptimeMillis`), holds Compose `view` state and the single deadline timer. |
| `engine/Feature.kt` | Typed seam: `Source`, `Host`, `Sink`, `PillControls`, `CompactSides`, `ItemView<T>`, `Feature<T>`. |
| `engine/Registry.kt` | The list of features (one line per type; notification claim order). |
| `engine/PillSession.kt` | Everything alive while the accessibility service is connected: window, screen watch, borrowed settings, sources, `Controls`, `Glow`, `VolumePair`, action runner, `PILL` log line, `snapshot()`. |
| `system/NoteFeed.kt` | `NoteFeed`, `NoteHandle`, `NoteListener`; the only code that reads `StatusBarNotification`. Keeps the last 16 removed claimed notifications so an open pill message can still send intents. |
| `system/Apps.kt` | `Apps`, `AppIcon`, `AppItem`: off-main-thread 96 px full-bleed icon & accent LRU cache (48 packages), labels, `launchable()`, `recents()`. |
| `system/SettingsStore.kt` | `SharedPreferences("pill")` <-> validated `StateFlow<Settings>`. |
| `system/BorrowedSetting.kt` | Saves old system setting once (`headsup_saved`, `icons_saved`), applies while running, restores on stop and reconciles at start. |
| `system/Torch.kt` | Camera flash controller, probed lazily and watched per session. |
| `system/ScreenWatch.kt` | Lock / landscape / status bar / cut-out -> `ScreenState` + `ScreenGeometry` (dp). |
| `system/Permissions.kt` | `PermissionStatus`, `permissions(context)`, `accessibilityEnabled(context)`. |
| `actions/PillAction.kt`, `actions/Catalog.kt` | `ActionEnv`, `Behaviour` (`Toggle`/`Step`/`Command`/`Opens`), `ActionGroup`, `PillAction`, `Catalog.all` and `Catalog.byId(id, apps)`. |
| `overlay/OverlayWindow.kt` | `TYPE_ACCESSIBILITY_OVERLAY` window, flags derived from `PillView`, touchable region, root view `GONE` while nothing is drawn. |
| `overlay/OutlinePath.kt` | `outlinePath()` and `OutlineShape`: one path shared by clip, glow and touchable region. |
| `overlay/PillRoot.kt` | Generic shell composable: picks `Want`, animates frame arms in layout/draw, clips and hosts the active view or menu. |
| `overlay/Gestures.kt` | Raw touch to tap / long press / swipes / flick / press-move-release. |
| `overlay/Controls.kt` | Session-owned registry for named buttons, hover, owned areas (seek bar) and reported extras. |
| `overlay/Glow.kt` | `Glow` state, `arrivalGlow` modifier and `accentOf` dominant-colour extractor. |
| `overlay/Menus.kt` | App menu (`FastMenu`), Actions menu (`QuickMenu`), Recent apps (`RecentApps`). |
| `ui/Tokens.kt` | Shared sizes, outline constants, type scale, colours and `SquircleShape` (music cover). |
| `ui/Components.kt` | `HoleRow`, `CanopyContent`, `LocalHole`, `CapsuleButton`, `PillIconButton`, `PillText`, `PillTextField`, `pillButton`, `tapToOpen`. |
| `ui/Glyphs.kt` | All Material 24x24 icon paths and glyph composables, defined once. |
| `rules/PetMachine.kt`, `idle/PetIdle.kt`, `idle/PetStyle.kt`, `idle/Pixel*.kt` (one pixel scene per animal: cat, dog, bird, monkey, Clawd; Clawd's scene is `PixelClawd.kt` with its poses in `PixelClawdActions.kt` and its excursion, a typed staircase, in `rules/ClawdRoam.kt`) | The pet, the only idle content (setting `pet`, default on). `PetMachine` (pure, takes a `Random`) decides the state, its length, where the pet stands, blinks and marks (heart, z, bark, sweat) as a `PetStep`, with per-animal chance tables (`PetAnimal`), and reacts to `PetEvent`s (notify, clear, plug, low, open, close) and to `place(dance)`: what the shown view asks (dance). Hide, hidden and peek start only from the notify event (sent by `PillSession` while the pill shows a message or ordinary notification, not for calls, alarms, navigation or music; `clear` is sent when it is not shown any more: the pet stays hidden in between, peeks, and a new notify during the peek hides it again) and only when the pet can reach a real camera hole; by chance never. `PetIdle` plays it: frames only inside `PetStep.moving`, otherwise one timer; `event()` reacts now or when the pet is seen again. `PillRoot`'s pet layer is its one host and always draws in the idle pill's box (`petBox`), so a view opening never rebuilds the scene or moves the pet; `PetPlace` decides dance per view. A `PetSpot` (compact charger bolt, compact music bars, right of the hole in large music) only gives a target: the pet glides there and back. `PillSession` sends the reactions (charger, open/close of a large view, battery below 20 %). The pet has one size in every view: the idle pill gives it (an idle pill taller than 40.5 dp keeps that size, so the pet fits the 37 dp compact pill whole). It draws on its own layer, so its frames do not redraw the pill. Drawing: `PetScene` (KDoc in `PetIdle.kt` is the contract: what each state shows; the default is `PetState.drawAs`), one pixel scene per animal; Roam: from a still Sit ends with 7 % (`ROAM_CHANCE`, only while the idle view shows in portrait), one pure path per animal (`RoamPlan.pointAt`, offsets in pixels), drawn on the pet's own layer which the pill's shape clips only while the pet is inside (`PetIdle.overflows`); `PetFrame.out` and `PetScene.drawsRoam` are the hook for a scene's own Roam pose; an interrupted excursion runs a 0.3 s hurry home first; `petScene(animal, ...)` in `PetStyle.kt` picks it. Setting `petAnimal` (default `cat`). The smooth and doodle looks were removed; git tag `looks-smooth-doodle` keeps them. |
| `features/<Type>.kt`, `features/<Type>View.kt` | One pair per type (model + pure parsers + `Source`, `ItemView`): `Call`, `Alarm`, `Navigation`, `Message`, `Charger`, `Music`. |
| `agent/StateDump.kt` | `Snapshot` -> `JSONObject` contract for `pill.sh state`. |
| `screen/` | Settings UI (the Pill tab also lists an "Animations" panel of chips for the chosen animal, built from `PetState.entries`; a tap calls `PetIdle.play`, stores nothing) split across `SettingsScreen.kt`, `GesturesTab.kt`, `MenusTab.kt`, `PillTab.kt`, `Picker.kt`, `Palette.kt`. |
| `pill.sh` | Emulator-only agent tool (see below). |
| `phone-install.sh` | Installs the optimised `release` build (R8, signed with the release key if `~/.pill-signing/keystore.properties` exists, else the debug key) on a real phone (`phone-install.sh <serial>`); waits for the screen to be off before installing so a mid-touch reinstall cannot block touch. |

## Add an event or an activity

1. **Model + source** (`features/Foo.kt`):
   - Event: `data class Foo(...) : PillEvent` (`kind`, `key`, `priority`, `showMs`, `label`, `accent`, `open()`). Same `key` updates the shown event. Priority: message 2, charger 1.
   - Activity: `data class Foo(...) : PillActivity` (`kind`, `id`, `priority`, `since`, `ringing`, optional `coveredBy` = package whose being in front hides it, `label`, `accent`, `open()`). Priority: call/alarm 2, music 1.
   - Source: `val FooSource = Source { host -> ... }` returning an `AutoCloseable` that undoes registration. Register broadcasts via `host.onBroadcast(...)` or notifications via `host.notes.listen(...)`, and publish on the main thread via `host.sink.event(...)`, `host.sink.activity(...)`, `host.sink.ended(id)`. Keep source-local bookkeeping inside what `start` creates.
2. **View** (`features/FooView.kt`):
   - `object FooView : ItemView<Foo>` implementing `compactWidth`, `Compact(item, pill)` and `Large(item, pill)`.
   - Use `HoleRow` in `Compact` so nothing sits under the camera hole. Override `compactSides(...)` if left and right arms size independently by content; call `pill.showFor(item, ms)` if an event needs longer than `showMs`.
   - Wrap `Large` in `CanopyContent(Modifier.tapToOpen(pill, item::open), crownLeft, crownRight) { shelf }`. Use `pill.close()`, `pill.dismiss(key)` and `pill.typing(on)` rather than faking gestures.
   - Mark every tappable control with `CapsuleButton`, `PillIconButton` or `Modifier.pillButton("name") { ... }` so it appears in `state.buttons`.
3. **Register** (`engine/Registry.kt`):
   - Add one line: `Feature(Foo::class, FooSource, FooView)` (notification sources are offered in list order; the first to claim a notification keeps it).

Nothing else changes: the rules, engine, shell and dump know no concrete feature type.

## Redraw a pet

A pet is a pixel scene file (`idle/Pixel<Animal>.kt` with its helpers). Draw from `PetFrame` only (state, progress, blend, face, x, blink, `mark(...)`, beat, low) and follow the `PetScene` KDoc. Do not change `PetMachine`, `PetIdle`, `PetStyle` or another animal's files. For Roam override `drawsRoam = true` and draw from `PetFrame.out` and `PetFrame.face` (the pet layer shifts the drawing by `out`; the hole is not cut). A pixel action can instead be written as data: letter-grid sprites and a key table in a `PixelAction` (`idle/PixelAction.kt`, whose KDoc is the guide), passed to `PixelScene` as `roam`; `idle/PixelMonkeyRoam.kt` is the example. Try each state with `pill.sh pet <state>` and each reaction with `pill.sh petevent <event>`.

## `pill.sh state` fields (add only; never rename)

`shown` {type: idle|event|activity, kind, key|id, label}, `size` (compact|large|fastmenu|quickmenu|recentapps|recentmenu), `menu` (fast|quick|recent|""), `collapsed`, `torch`, `gestures` {tapLeft, tapRight, holdLeft, holdRight, volume: code}, `appMenu` [pkg], `actionsMenu` [id], `actions` [{id, kind, group, available, reason, on}], `visible`, `hiddenBy` (lock|landscape|statusbar|""), `bounds` [l,t,r,b] of the pill in screen pixels (zeros when nothing is drawn), `waiting` {kind,key,label} (omitted when none), `activities` [{id,kind,hidden,ringing,label}], `buttons` {name: [l,t,r,b]}, `popupsOff`, `popupsPermission`, `hole` [l,t,r,b], `idleWidth` (dp, 40-240, default 156), `idleHeight` (drawn dp: hole + 4 .. 56, default hole + 10), `pet` (switch), `petState` (state id, "" before the pet first showed), `petAnimal`, `glow` {running, color}, `statusIconsHidden`, `players` [{pkg, shown}], `fastApps` [pkg, ...], `quickActions` [id, ...] (alias of `actionsMenu`), `seek` {bounds [l,t,r,b], supported, position, duration}|null (music large view only).

The log line (`pill.sh logs`, tag `PILL`): `shown=... size=... visible=... waiting=... activities=[...]`, once per change.

## Look and window behaviour

Every large view (music, calls, alarm, the three menus, the opened message) is a canopy: `pillFrame` (`rules/Layout.kt`) computes the target frame, `PillRoot` animates its arms in layout/draw, and `outlinePath` (`overlay/OutlinePath.kt`) builds the single path used for clip, arrival glow and the window's touchable region. `CanopyContent` (`ui/Components.kt`) lays out the narrow crown beside the camera hole and the shelf below the status bar (its `modifier` applies to the shelf only; a single tap on the crown sends `Gesture.Outside` to close the opened view). Compact views are `COMPACT_H` (37 dp) pills centred on the hole; a compact message sizes its left and right arms independently around the hole (snapped to status-bar icon slots in portrait) and scrolls overflowing text once in full via `pill.showFor`. `ScreenState` maps locked -> `Locked` (draws nothing) and landscape/fullscreen -> `Away` (only events and ringing activities show at top centre, at most 460 dp wide). `OverlayWindow` derives window flags from `PillView`: root view `GONE` and untouchable once collapsed/hidden, `FLAG_WATCH_OUTSIDE_TOUCH` only when not compact, and focusable only while `view.typing` is true (bounded to 60 s or screen change in `PillSession`). Arrival glow (one lap; the `arrivalGlow` setting turns it off) is decided by `PillRules` (`Effect.Arrived` / `Effect.StopGlow`), owned by `PillSession.glow`, and drawn outside the clip by `arrivalGlow` (`overlay/Glow.kt`).

## Agent tool verbs

`pill.sh` with no argument prints all verbs. Verbs that act on the pill (`tap`, `longpress`, `swipeup|left|right`) read its bounds from `state`. `button <name>` taps a named button. `flick [px] [ms]` sends a short upward flick (default 30 px in 80 ms). `drag x1 y1 x2 y2 hold-ms` presses, holds, moves and releases (recent apps). `chat one|two <chat> <text> [read,mute,delete,archive,custom,noreply]` posts a message from a fake chat app (same chat name = same notification key). `settings` opens Pill's main screen. `tapleft|tapright|holdleft|holdright` act on one half of the idle pill; `swipedown` pulls down the notification shade; `doubletap x y` taps twice inside one shell command; `volkeys` sends volume up+down (adb-injected keys bypass accessibility key filters on the emulator); `voip <name> [style|plain]` posts a fake VoIP call; `alarm [ring|silent|stop] [label]` posts a fake alarm notification; `nav [stop|<title> [sub [text]]]` posts a fake navigation notification (button `navaction`); `battery <0-100|reset>` sets or resets battery level. Every verb targets only an `emulator-NNNN` device.

`pet <state>` forces that state on the pet now (every `PetState` id, `roam` too; `unavailable` when the pet is not shown, the pill is too narrow for a walk, or the state belongs to another animal). `petevent notify|clear|plug|low|open|close|dance` makes the pet react as if it happened (`ok: waits until the pet is seen` when it is not shown). `petstyle <cat|dog|bird|monkey|clawd>` sets the animal. `strip <state> [out.png]` records the emulator while the pet runs that state and writes one picture of 12 frames of the action (default `build/strip-<state>.png`). `clip <state> [out.mp4]` records the same action as a video (0.5 s still, the action, 0.5 s still, then the action again at half speed; default `build/clip-<state>.mp4`). `reel <animal> [label] [out.mp4]` records every action of that pet once, at real speed, into one video with a title and the action's name under the picture (default `build/reel-<animal>.mp4`). `gesture <tapLeft|tapRight|holdLeft|holdRight|volume> <choice>`, `actionsmenu <id,id,...>`, `appmenu <pkg,pkg,...>`, `clearnotifs`, `pet <state>`, `petevent <event>`, `petstyle <animal>` and `action <id>` talk to `CommandReceiver` (validated by `Command.parse`; prints `ok`, `unavailable: <reason>` or `error: ...`).

## Settings and catalog

Settings live in `rules/Settings.kt` (pure, validated) and `system/SettingsStore.kt` (`StateFlow<Settings>` backed by `SharedPreferences("pill")`). Gestures map `IdleSlot` (`TapLeft`, `TapRight`, `HoldLeft`, `HoldRight`, `Volume`) to `Choice` (`AppMenu`, `ActionsMenu`, `Recents`, `HideIdle`, `Nothing`, `Action(id)`). The action catalog lives in `actions/Catalog.kt` (`Catalog.all`, `Catalog.byId(id, apps)`), where each `PillAction` carries a `Behaviour` (`Toggle`, `Step`, `Command`, `Opens`). Permissions come from `system/Permissions.kt` (`permissions(ctx)`). The three-tab settings UI (`Gestures`, `Menus`, `Pill`) lives in `screen/` and supports `ThemeMode` (`System`, `Light`, `Dark`) via `Palette.kt`.

## Tests and comparison pictures

- Plain JVM tests: `./gradlew --no-daemon testDebugUnitTest` (`RulesTest`, `PureTest`, `SettingsTest`, `NoteParseTest`, `NavigationValueTest`, `VolumePairTest`, `LayoutTest`, `AgentTest`, `PetMachineTest`).
- Strictness checks: `./gradlew --no-daemon ktlintCheck lint testDebugUnitTest assembleDebug` (`allWarningsAsErrors = true`, Android lint `warningsAsErrors = true`, ktlint).
- Full run: `./pill.sh test` runs unit tests, installs/enables on the emulator, and generates iPhone-vs-Pill comparison pictures in `comparison/` at the repo root (git-ignored) (driven by `pill/compare/features.tsv` and `pill/compare/compare.sh`).
