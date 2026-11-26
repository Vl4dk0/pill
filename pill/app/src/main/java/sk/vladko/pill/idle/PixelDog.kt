package sk.vladko.pill.idle

import androidx.compose.ui.geometry.Rect
import sk.vladko.pill.rules.PET_BEAT_MS
import sk.vladko.pill.rules.PET_BLEND_MS
import sk.vladko.pill.rules.PetMark
import sk.vladko.pill.rules.PetPose
import sk.vladko.pill.rules.PetState
import kotlin.math.floor
import kotlin.math.max

// The dog: a golden puppy with a cream muzzle, chest and paws, big floppy brown ears and a teal collar with a gold tag.
// It is drawn as a paper doll: hand-drawn parts (head, ears, body halves, legs, tail) are laid over the 20 x 20 grid, one
// picture per frame, so every pose and every step of a movement is a drawn picture and no shape is ever stretched.

private val PALETTE =
    mapOf(
        'o' to 0xFFDD9A4E.toInt(),
        'l' to 0xFFF6C98A.toInt(),
        'd' to 0xFFAC6A2F.toInt(),
        'e' to 0xFF7B4322.toInt(),
        'f' to 0xFFA8683A.toInt(),
        'c' to 0xFFFFF1DA.toInt(),
        's' to 0xFFE3CBA6.toInt(),
        'k' to 0xFF20151A.toInt(),
        'w' to 0xFFFFFFFF.toInt(),
        't' to 0xFFFF7792.toInt(),
        'm' to 0xFF8A2638.toInt(),
        'b' to 0xFF2BB6C9.toInt(),
        'y' to 0xFFFFD43B.toInt(),
        'n' to 0xFF3A2418.toInt(),
        'g' to 0xFF8A5A34.toInt(),
        'u' to 0xFFD9A066.toInt(),
    )

/** The far side of the body: each part's colour one step darker. */
private val FAR = mapOf('o' to 'd', 'l' to 'o', 'c' to 's', 'f' to 'e')

/** One hand-drawn part: rows of letters of [PALETTE], '.' empty, facing right. */
private class Art(
    lines: List<String>,
) {
    val w = lines.maxOf { it.length }
    val rows = lines.map { it.padEnd(w, '.') }
}

private val PARTS: Map<String, Art> =
    buildMap {
        var name: String? = null
        var rows = mutableListOf<String>()

        fun close() {
            name?.let { put(it, Art(rows)) }
            rows = mutableListOf()
        }
        PARTS_TEXT.lines().forEach {
            when {
                it.startsWith("## ") -> {
                    close()
                    name = it.drop(3).trim()
                }
                it.isNotBlank() -> rows += it.trim()
            }
        }
        close()
    }

/** The letters of one picture, laid over the grid part by part. */
private class Canvas {
    val c = CharArray(GRID * GRID) { '.' }

    fun put(
        part: String,
        x: Int,
        y: Int,
        far: Boolean = false,
        flip: Boolean = false,
    ) {
        val a = PARTS.getValue(part)
        a.rows.forEachIndexed { j, row ->
            for (i in 0 until a.w) {
                var ch = row[if (flip) a.w - 1 - i else i]
                if (ch == '.') continue
                if (far) ch = FAR[ch] ?: ch
                val gx = x + i
                val gy = y + j
                if (gx in 0 until GRID && gy in 0 until GRID) c[gy * GRID + gx] = ch
            }
        }
    }

    fun raster() = Raster().also { r -> c.forEachIndexed { i, ch -> if (ch != '.') r.c[i] = PALETTE.getValue(ch) } }

    /** The lowest cell nearest the home column: where the pin of a roam sprite goes. */
    fun pin(): Pair<Int, Int> {
        val y = (GRID - 1 downTo 0).first { j -> (0 until GRID).any { c[j * GRID + it] != '.' } }
        val x = (0 until GRID).filter { c[y * GRID + it] != '.' }.minBy { kotlin.math.abs(it - 10) }
        return x to y
    }
}

/** One picture: the dog on [canvas], its head at ([hx], [hy]), the whole grid moved [dx] ahead and [lift] up (cells). */
private class Shot(
    val canvas: Canvas,
    val hx: Float,
    val hy: Float,
    val dx: Float = 0f,
    val lift: Float = 0f,
)

private const val TURN_MS = 130L
private const val STEP_MS = 130L

private const val FRONT = "front_head"

/** Where the four legs stand: near front, far front, near hind, far hind. */
private val LEG_X = intArrayOf(11, 8, 4, 2)

/**
 * A leg of kind [kind] at column [x]: L straight, F reaching forward, B pushing back, U lifted and swinging, S folded
 * short, T tall; [far] draws it on the far side.
 */
private fun Canvas.leg(
    kind: Char,
    x: Int,
    far: Boolean,
    y: Int = 16,
) = when (kind) {
    'F' -> put("leg_f", x, y, far)
    'B' -> put("leg_b", x - 1, y, far)
    'U' -> put("leg_u", x, y, far)
    'S' -> put("leg_s", x, 18, far)
    'T' -> put("leg_t", x, 15, far)
    '-' -> Unit
    else -> put("leg", x, y, far)
}

private fun standing(
    head: String = "head",
    ear: String? = "ear",
    tail: String = "tail",
    legs: String = "LLLL",
    bob: Int = 0,
    hdx: Int = 0,
    hdy: Int = 0,
    dx: Float = 0f,
    lift: Float = 0f,
    legY: Int = 16,
    extra: (Canvas) -> Unit = {},
): Shot {
    val c = Canvas()
    c.leg(legs[1], LEG_X[1], true, legY)
    c.leg(legs[3], LEG_X[3], true, legY)
    c.leg(legs[0], LEG_X[0], false, legY)
    c.leg(legs[2], LEG_X[2], false, legY)
    c.put(tail, 0, 6 + bob)
    c.put("bs_back", 1, 10 + bob)
    c.put("bs_front", 8, 10 + bob)
    c.put("collar", 11, 12 + bob)
    extra(c)
    c.put(head, 10 + hdx, 5 + bob + hdy)
    ear?.let { c.put(it, 10 + hdx, 6 + bob + hdy) }
    return Shot(c, 14f + hdx, 8f + bob + hdy, dx, lift)
}

private fun sitting(
    head: String = "head",
    ear: String? = "ear",
    tail: String = "tail_sit",
    hdx: Int = 0,
    hdy: Int = 0,
    flip: Boolean = false,
    dx: Float = 0f,
    lift: Float = 0f,
    extra: (Canvas) -> Unit = {},
): Shot {
    val c = Canvas()
    c.put(tail, 0, 18)
    c.put("body_sit", 1, 9)
    c.put("leg_sit", 12, 14)
    c.put("leg_sit", 10, 14, far = true)
    c.put("collar", 10, 10)
    extra(c)
    val x = 9 + hdx
    c.put(head, x, 3 + hdy, flip = flip)
    ear?.let { c.put(it, if (flip) x + 10 - PARTS.getValue(it).w else x, 4 + hdy, flip = flip) }
    return Shot(c, x + 4.5f, 6.5f + hdy, dx, lift)
}

private fun lying(
    head: String = "head_blink",
    ear: String? = "ear",
    breath: Boolean = false,
): Shot {
    val c = Canvas()
    c.put("tail_lie", 0, 14)
    if (breath) c.put("body_lie_b", 2, 13) else c.put("body_lie", 2, 14)
    c.put(head, 10, 12)
    ear?.let { c.put(it, 10, 13) }
    c.put("paws", 16, 18)
    return Shot(c, 14.5f, 15.5f)
}

/** Low and flat on its legs: between standing and lying, and behind the camera hole. */
private fun crouching(
    head: String = "head",
    tail: String = "tail_c",
    ear: String? = "ear",
    hdx: Int = 0,
    hdy: Int = 0,
    flip: Boolean = false,
): Shot {
    val c = Canvas()
    c.put("leg_s", 9, 18, far = true)
    c.put("leg_s", 2, 18, far = true)
    c.put("leg_s", 11, 18)
    c.put("leg_s", 4, 18)
    c.put(tail, 0, 9)
    c.put("body_crouch", 1, 12)
    c.put("collar", 11, 14)
    val x = 10 + hdx
    c.put(head, x, 7 + hdy, flip = flip)
    ear?.let { c.put(it, if (flip) x + 10 - PARTS.getValue(it).w else x, 8 + hdy, flip = flip) }
    return Shot(c, x + 4f, 10f + hdy)
}

/** Half up, the hind legs under it: between sitting and standing. */
private fun rising() = standing(bob = 1, legs = "LLFB", tail = "tail_c")

/** Chest down and rump up with the front paws stretched out ahead; [deep] 0 or 1. */
private fun bowing(
    deep: Int,
    head: String,
    tail: String,
): Shot {
    val c = Canvas()
    val drop = 1 + 2 * deep
    c.leg('T', LEG_X[3], true)
    c.leg('T', LEG_X[2], false)
    if (deep == 1) {
        c.put("arm_fwd", 11, 18, far = true)
        c.put("arm_fwd", 13, 18)
    } else {
        c.leg('L', LEG_X[1], true, 16 + 1)
        c.leg('L', LEG_X[0], false, 16 + 1)
    }
    c.put(tail, 0, 5)
    c.put("bs_back", 1, 9)
    c.put("bs_front", 8, 9 + drop)
    c.put("collar", 11, 11 + drop)
    c.put(head, 10, 4 + drop + 1)
    c.put("ear", 10, 5 + drop + 1)
    return Shot(c, 14f, 8f + drop)
}

private fun posed(
    p: PetPose,
    head: String? = null,
): Shot =
    when (p) {
        PetPose.Sit -> if (head == null) sitting() else sitting(head = head, ear = null)
        PetPose.Stand -> if (head == null) standing() else standing(head = head, ear = null)
        PetPose.Lie -> if (head == null) lying() else lying(head = head, ear = null)
    }

/** The pictures between two bodies: standing in between, and low to lie down. */
private fun route(
    from: PetPose,
    to: PetPose,
): List<Shot> =
    when {
        from == to -> emptyList()
        from == PetPose.Lie && to == PetPose.Sit -> listOf(crouching(), rising())
        from == PetPose.Sit && to == PetPose.Lie -> listOf(rising(), crouching())
        from == PetPose.Lie || to == PetPose.Lie -> listOf(crouching())
        else -> listOf(rising())
    }

/** The body an action starts in (a state's own body is the one it ends in). */
private fun startsIn(state: PetState): PetPose =
    when (state) {
        PetState.Tired -> PetPose.Sit
        PetState.Peek -> PetPose.Stand
        else -> state.pose
    }

/** Legs of a walk at [phase] 0 to 3: the near front and far hind leg (one pair) against the far front and near hind. */
private fun gait(phase: Int): String {
    val (a, b) = listOf('F' to 'B', 'L' to 'U', 'B' to 'F', 'U' to 'L')[phase and 3]
    return "$a$b$b$a"
}

internal object DogRig : Rig {
    override fun base(p: PetPose): Pose =
        Pose().with {
            when (p) {
                PetPose.Sit -> {
                    hx = 13.5f
                    hy = 6.5f
                }
                PetPose.Stand -> {
                    hx = 14f
                    hy = 8f
                }
                PetPose.Lie -> {
                    hx = 14.5f
                    hy = 15.5f
                }
            }
        }

    override fun rest(p: PetPose) = posed(p).canvas.raster()

    override fun pose(
        f: PetFrame,
        walked: Float,
    ) = base(f.step.pose)

    /** The dog draws whole pictures ([picture]), not a posed rig. */
    override fun paint(
        r: Raster,
        p: Pose,
    ) {}

    override val excitedHeart = false

    override fun mouth(p: Pose) = p.hx + 4f to p.hy + 2.5f

    override fun picture(
        f: PetFrame,
        walked: Float,
    ): Drawn {
        val s = f.step
        val route = route(s.poseFrom, startsIn(s.state))
        val turnMs = if (s.faceFrom != s.face && s.state != PetState.Peek && s.state != PetState.Hidden) TURN_MS else 0L
        val pre = if (s.state == PetState.Dance && s.poseFrom == PetPose.Stand) 0L else turnMs + route.size * STEP_MS
        var facing = s.face
        val shot =
            when {
                f.ms < turnMs -> {
                    facing = s.faceFrom
                    posed(s.poseFrom, FRONT)
                }
                f.ms < pre -> route[((f.ms - turnMs) / STEP_MS).toInt().coerceAtMost(route.size - 1)]
                else -> act(f, f.ms - pre, s.ms - pre, walked)
            }
        val pose =
            Pose().with {
                hx = shot.hx
                hy = shot.hy
                dx = shot.dx
                lift = shot.lift
                turn = 1f
            }
        return Drawn(shot.canvas.raster(), pose, facing < 0)
    }

    private fun act(
        f: PetFrame,
        a: Long,
        len: Long,
        walked: Float,
    ): Shot {
        val t = (a.toFloat() / max(1L, len)).coerceIn(0f, 1f)
        val rest = if (f.low) "head_half" else "head"
        return when (f.state) {
            PetState.Sit -> sitting(head = if (f.blink) "head_blink" else rest)
            PetState.LookAround -> look(t, a, rest)
            PetState.Walk -> walk(f, walked, false)
            PetState.Hide -> if (f.progress > 0.9f) crouching() else walk(f, walked, true)
            PetState.Hidden -> crouching()
            PetState.Sleep -> {
                val z = f.mark(PetMark.Z)
                lying(breath = z >= 0f && (z * 3f).toInt() % 2 == 0)
            }
            PetState.Heart -> heart(t, a)
            PetState.Stretch -> stretch(t, a)
            PetState.Groom -> groom(t, a)
            PetState.Fidget -> fidget(t, a)
            PetState.Jump -> jump(t)
            PetState.Peek -> peek(t)
            PetState.Bark -> bark(f, a)
            PetState.Dig -> dig(t, a)
            PetState.Excited -> excited(t, a)
            PetState.Tired -> tired(t, a)
            PetState.Startle -> startle(t)
            PetState.Dance -> dance(f.ms)
            else -> sitting()
        }
    }

    /** The tail swings between two drawings every [ms] ms. */
    private fun wag(
        a: Long,
        ms: Long,
        one: String,
        two: String,
    ) = if ((a / ms) % 2 == 0L) one else two

    /** Turns its head to the viewer, looks up, looks back over its shoulder, and faces front again. */
    private fun look(
        t: Float,
        a: Long,
        rest: String,
    ): Shot =
        when {
            t < 0.08f || t > 0.94f -> sitting(head = rest)
            t < 0.2f || (t in 0.46f..0.52f) || (t in 0.78f..0.86f) -> sitting(head = FRONT, ear = null)
            t < 0.44f -> sitting(head = "head_wide", ear = "ear_up", hdy = -1, tail = wag(a, 220, "tail_sit", "tail_sit_b"))
            t < 0.78f -> sitting(head = "head", flip = true, hdx = -5, hdy = 1, tail = wag(a, 220, "tail_sit", "tail_sit_b"))
            else -> sitting(head = rest, hdy = 0)
        }

    /** A trot: legs by [gait] from the cells [walked], the body down at each step, the ears and the tail swinging. */
    private fun walk(
        f: PetFrame,
        walked: Float,
        hurry: Boolean,
    ): Shot {
        val going = f.ms >= PET_BLEND_MS && f.progress < 0.97f && walked > 0.05f
        if (!going) return standing(head = if (hurry) "head" else "head_pant")
        val phase = floor(walked).toInt() + 1
        val down = if (phase and 1 == 0) 1 else 0
        return standing(
            head = if (hurry) "head" else "head_pant",
            ear = if (phase and 1 == 0) "ear" else "ear_out",
            tail =
                if (hurry) {
                    "tail_c"
                } else if (phase and 2 == 0) {
                    "tail"
                } else {
                    "tail_b"
                },
            legs = gait(phase),
            bob = down,
            hdy = if (hurry) 1 else 0,
        )
    }

    /** Hops with joy while it sits: the heart rises from its head. */
    private fun heart(
        t: Float,
        a: Long,
    ): Shot {
        val on = t in 0.06f..0.94f
        return sitting(
            head = if (on) "head_happy" else "head",
            ear = if (on) "ear_up" else "ear",
            tail = if (on) wag(a, 160, "tail_sit", "tail_sit_b") else "tail_sit",
            lift = if (on && a % 480 < 200) 1f else 0f,
        )
    }

    /** A play bow with a yawn and a wagging tail, then an arched stretch of the back legs, then up. */
    private fun stretch(
        t: Float,
        a: Long,
    ): Shot =
        when {
            t < 0.06f || t > 0.94f -> standing()
            t < 0.2f -> bowing(0, "head", "tail_d")
            t < 0.64f -> bowing(1, if (t < 0.5f) "head_yawn" else "head_blink", wag(a, 140, "tail_d", "tail_b"))
            t < 0.78f -> standing(head = "head_blink", legs = "LLBB", bob = -1, hdy = -1, tail = "tail_d")
            else -> standing(head = "head_blink", legs = "LLLL", tail = "tail")
        }

    /** Scratches behind its ear with a hind paw, the head tipped into it and the eyes shut. */
    private fun groom(
        t: Float,
        a: Long,
    ): Shot {
        if (t < 0.06f || t > 0.94f) return sitting()
        val up = (a / 90) % 2 == 0L
        return sitting(
            head = "head_blink",
            ear = if (up) "ear_out" else "ear",
            hdy = 1,
            hdx = -1,
            extra = { it.put(if (up) "scr_a" else "scr_b", 5, 5 + if (up) 0 else 1) },
        )
    }

    /** A shake from head to tail: the body, the head and the ears swing against each other, the whole dog jolting. */
    private fun fidget(
        t: Float,
        a: Long,
    ): Shot {
        if (t < 0.06f || t > 0.9f) return sitting()
        val k = (a / 70) % 2 == 0L
        return sitting(
            head = "head_blink",
            ear = if (k) "ear_out" else "ear_up",
            tail = if (k) "tail_sit" else "tail_sit_b",
            hdx = if (k) 2 else -1,
            hdy = if (k) 0 else 1,
            dx = if (k) -1f else 1f,
            lift = if (k) 1f else 0f,
        )
    }

    private fun jump(t: Float): Shot =
        when {
            t < 0.04f || t > 0.94f -> standing()
            t < 0.22f -> crouching("head", "tail_c")
            t < 0.32f -> standing(legs = "FFBB", head = "head_pant", ear = "ear_out", tail = "tail_d", bob = -1, lift = 1f)
            t < 0.5f -> standing(legs = "FFBB", head = "head_pant", ear = "ear_out", tail = "tail_d", bob = -1, lift = 4f)
            t < 0.62f -> standing(legs = "FFBB", head = "head_pant", ear = "ear_out", tail = "tail_d", bob = -1, lift = 5f)
            t < 0.78f -> standing(legs = "LLLL", head = "head_pant", ear = "ear_up", tail = "tail_d", lift = 2f)
            t < 0.88f -> crouching("head_pant", "tail_d")
            else -> standing(bob = 1, legs = "LLFB", tail = "tail_c")
        }

    /** Behind the hole: the head leans out to the side, eyes wide, looks to the viewer and the other way, then it sits up. */
    private fun peek(t: Float): Shot =
        when {
            t < 0.12f -> crouching()
            t < 0.28f -> crouching("head_wide", ear = "ear_up", hdx = 2, hdy = -1)
            t < 0.44f -> crouching(FRONT, ear = null, hdx = 3, hdy = -2)
            t < 0.6f -> crouching("head", ear = "ear_up", hdx = -6, hdy = -1, flip = true)
            t < 0.74f -> crouching("head", ear = "ear_up", hdx = 2, hdy = -1)
            t < 0.86f -> rising()
            else -> sitting()
        }

    /** Ready with its ears up; on each bark mark it rears back, throws its head up and opens its mouth. */
    private fun bark(
        f: PetFrame,
        a: Long,
    ): Shot {
        val tail = wag(a, 100, "tail_d", "tail_b")
        val m = f.mark(PetMark.Bark)
        return when {
            m < 0f -> standing(ear = "ear_up", tail = tail, hdy = -1)
            m < 0.18f -> standing(ear = "ear_up", tail = tail, hdy = 1, dx = 0f, bob = 1)
            m < 0.62f -> standing(head = "head_bark", ear = "ear_up", tail = "tail_d", hdy = -3, dx = -1f)
            else -> standing(ear = "ear_up", tail = tail, hdy = -1, dx = -1f)
        }
    }

    /** Digs with the front paws in turn, the head down, the tail wagging, dirt flying back between the legs. */
    private fun dig(
        t: Float,
        a: Long,
    ): Shot {
        if (t < 0.06f || t > 0.94f) return standing()
        val ph = ((a / 80) % 4).toInt()
        val near = "FBUU"[ph]
        val far = "UUFB"[ph]
        val s =
            standing(
                head = "head",
                ear = "ear_out",
                tail = wag(a, 110, "tail_d", "tail_b"),
                legs = "$near${far}LL",
                hdy = 3,
                hdx = 1,
                bob = 1,
            )
        s.canvas.flecks((a % 500) / 500f)
        return s
    }

    /** Joyful hops with the mouth open; at the top of the middle hops it looks at the viewer. */
    private fun excited(
        t: Float,
        a: Long,
    ): Shot {
        if (t < 0.03f || t > 0.95f) return standing()
        val h = a % 700
        val tail = wag(a, 100, "tail_d", "tail_b")
        val turned = t in 0.35f..0.65f
        val head = if (turned) "front_head" else "head_pant"
        val ear = if (turned) null else "ear_up"
        return when {
            h < 110 -> crouching("head_pant", "tail_d")
            h < 260 -> standing(legs = "FFBB", head = head, ear = ear, tail = tail, bob = -1, lift = 3f)
            h < 420 -> standing(legs = "FFBB", head = head, ear = ear, tail = tail, bob = -1, lift = 5f)
            h < 560 -> standing(legs = "LLLL", head = head, ear = ear, tail = tail, lift = 2f)
            else -> crouching("head_pant", "tail_d")
        }
    }

    /** Droops, yawns, lets its head sink, then lies down to sleep. */
    private fun tired(
        t: Float,
        a: Long,
    ): Shot =
        when {
            t < 0.16f -> sitting(head = "head_half", ear = "ear", hdy = 1)
            t < 0.4f -> sitting(head = "head_yawn", ear = "ear_up", hdy = 0)
            t < 0.56f -> sitting(head = "head_half", hdy = 2)
            t < 0.72f -> crouching("head_half", "tail_c")
            else -> lying(head = if (t < 0.9f) "head_half" else "head_blink")
        }

    /** Leaps back with its fur up and eyes wide, freezes low, then looks down at the menu. */
    private fun startle(t: Float): Shot =
        when {
            t < 0.05f || t > 0.97f -> standing()
            t < 0.14f -> standing(legs = "FFBB", head = "head_wide", ear = "ear_up", tail = "tail_c", bob = -1, lift = 3f, dx = -1f)
            t < 0.28f -> standing(legs = "FFBB", head = "head_wide", ear = "ear_up", tail = "tail_c", bob = -1, lift = 6f, dx = -2f)
            t < 0.4f -> standing(legs = "LLLL", head = "head_wide", ear = "ear_up", tail = "tail_c", lift = 2f, dx = -2f)
            t < 0.7f -> crouching("head_wide", ear = "ear_up").let { Shot(it.canvas, it.hx, it.hy, dx = -2f) }
            t < 0.88f -> standing(head = "head_half", ear = "ear", tail = "tail_c", hdy = 2, hdx = 1, bob = 1, dx = -2f)
            else -> standing(head = "head", ear = "ear", tail = "tail_c", dx = -1f)
        }

    /**
     * A two-beat hip-hop that loops every second: on each beat the dog lands low, springs up and throws one front paw high,
     * the head tossing back, and it leans to the other side on the next beat. A picture every eighth of a beat.
     */
    private fun dance(ms: Long): Shot {
        val q = ((ms % (PET_BEAT_MS * 2)) / (PET_BEAT_MS / 4)).toInt()
        val near = q < 4
        val lean = if (near) -1f else 1f
        val tail = wag(ms, 125, "tail_d", "tail_b")

        fun up(lift: Float): Shot =
            standing(
                head = "head_pant",
                ear = "ear_up",
                tail = tail,
                legs = if (near) "-LLL" else "L-LL",
                bob = -1,
                hdy = -1,
                dx = lean,
                lift = lift,
                legY = 15,
                extra = { c -> if (near) c.put("leg_f", 13, 12) else c.put("leg_f", 12, 13, far = true) },
            )
        return when (q % 4) {
            0 -> standing(head = "head_pant", ear = "ear_out", tail = tail, legs = "LLFB", bob = 1, hdy = 1, dx = lean)
            1 -> up(2f)
            2 -> up(3f)
            else -> up(1f)
        }
    }
}

internal class PixelDogScene(
    w: Float,
    h: Float,
    hole: Rect?,
    fixedUnit: Float? = null,
) : PixelScene(w, h, hole, DogRig, fixedUnit, DogRoam)

private fun sprite(
    name: String,
    s: Shot,
): Pair<PixelSprite, Pair<Int, Int>> {
    val (px, py) = s.canvas.pin()
    val rows =
        (0 until GRID).joinToString("\n") { j ->
            (0 until GRID).joinToString("") { i ->
                val ch = s.canvas.c[j * GRID + i]
                if (i == px && j == py) ch.uppercaseChar().toString() else ch.toString()
            }
        }
    return PixelSprite(name, rows) to (px to py)
}

/** Dirt thrown back under the belly and out behind the hind legs: six lumps at the stage [q] (0 to 1) of the throw, only over empty cells. */
private fun Canvas.flecks(q: Float) {
    fun dab(
        x: Int,
        y: Int,
        ch: Char,
    ) {
        if (x in 0 until GRID && y in 0 until GRID && c[y * GRID + x] == '.') c[y * GRID + x] = ch
    }
    for (i in 0..5) {
        val u = (q + i / 6f) % 1f
        val x = 9 - (u * 12).toInt()
        val y = 18 - (8 * kotlin.math.sin(u * Math.PI)).toInt()
        dab(x, y, 'u')
        dab(x + 1, y, 'u')
        dab(x, y + 1, 'g')
    }
}

/** Sniffs the pill's floor, digs through it, falls out of the screen, walks back in from the pill's end and shakes itself. */
private val DogRoam: PixelAction =
    run {
        val dig = { ph: Int ->
            standing(ear = "ear_out", tail = "tail_d", legs = "${"FBUU"[ph]}${"UUFB"[ph]}LL", hdy = 3, hdx = 1, bob = 1)
        }
        val shots =
            buildMap<String, Shot> {
                put("sniffA", standing(ear = "ear", legs = "LLLL", hdy = 3, hdx = 1, bob = 1, tail = "tail_b"))
                put("sniffB", standing(ear = "ear_out", legs = "LLLL", hdy = 4, hdx = 1, bob = 1, tail = "tail"))
                for (ph in 0..3) put("dig$ph", dig(ph).also { it.canvas.flecks(ph / 4f) })
                put("burst", standing(legs = "FFBB", head = "head_wide", ear = "ear_up", tail = "tail_d", bob = -1))
                put("paddleA", standing(legs = "FBBF", head = "head_wide", ear = "ear_up", tail = "tail_d", bob = -1))
                put("paddleB", standing(legs = "BFFB", head = "head_wide", ear = "ear_up", tail = "tail_d", bob = -1))
                for (i in 0..3) {
                    put("trot$i", standing(legs = gait(i), head = "head_pant", ear = if (i % 2 == 0) "ear" else "ear_out", bob = 1 - i % 2))
                }
                put("glance", standing(head = FRONT, ear = null))
                put("shakeA", standing(head = "head_blink", ear = "ear_out", tail = "tail_d"))
                put("shakeB", standing(head = "head_blink", ear = "ear_up", tail = "tail_b", hdx = -1))
            }
        val built = shots.map { (n, s) -> sprite(n, s) }
        val pins = shots.keys.zip(built.map { it.second }).toMap()
        val keys =
            buildList {
                fun key(
                    t: Float,
                    name: String,
                    dx: Int = 0,
                    dy: Int = 0,
                ) = add(
                    pins[name]?.let { (x, y) -> "$t $name ${x + dx} ${y + dy} 0 hold" } ?: "$t $name 10 19 0 hold",
                )
                key(0f, "stand")
                var t = 0.02f
                var n = 0
                while (t < 0.14f) key(t, if (n++ % 2 == 0) "sniffA" else "sniffB").also { t += 0.03f }
                n = 0
                t = 0.14f
                while (t < 0.36f) key(t, "dig${n++ % 4}").also { t += 0.02f }
                key(0.36f, "burst", dy = -1)
                n = 0
                t = 0.38f
                while (t < 0.66f) key(t, if (n++ % 2 == 0) "paddleA" else "paddleB").also { t += 0.025f }
                n = 0
                t = 0.66f
                while (t < 0.86f) key(t, "trot${n++ % 4}").also { t += 0.04f }
                key(0.86f, "glance")
                key(0.88f, "shakeA", dx = -1)
                key(0.9f, "shakeB", dx = 1)
                key(0.92f, "shakeA", dx = -1)
                key(0.94f, "shakeB", dx = 1)
                key(0.96f, "shakeA", dx = -1)
                key(0.98f, "stand")
                key(1f, "stand")
            }.joinToString("\n")
        PixelAction(PALETTE, built.map { it.first }, keys)
    }

private const val PARTS_TEXT = """
## head
..oooo....
.ollllo...
ollloooo..
oooowkoooo
ooooookccn
.oooooccck
.oooccccss
..ooccsmmm

## head_blink
..oooo....
.ollllo...
ollloooo..
oooooooooo
ooookkkccn
.oooooccck
.oooccccss
..ooccsmmm

## head_happy
..oooo....
.ollllo...
ollloooo..
oooookoooo
ooookokccn
.oooooccck
.oooccccss
..ooccsmmm

## head_half
..oooo....
.ollllo...
ollloooo..
oooooooooo
ooookkoccn
.oooooccck
.oooccccss
..ooccsmmm

## head_pant
..oooo....
.ollllo...
ollloooo..
oooowkoooo
ooooookccn
.oooooccck
.oooccccmm
..ooccsmtm
.....ttt..

## head_bark
..oooo....
.ollllo...
ollloooo..
oooowkoooo
ooooookccn
.oooooccck
.ooocmmmmm
..oocmmmtt
...oss.ttt

## head_wide
..oooo....
.ollllo...
olllwkooo.
ooookkoooo
ooookkoccn
.oooooccck
.oooccccss
..ooccsmmm

## front_head
ee.oooo.ee
eeolllloee
eeooooooee
eewkoowkee
eekkookkee
.eoocckkce
.eocccckcc
..eocmmcs.

## ear
ee
ee
ee
ee
.e

## ear_up
e.
ee
ee
.e

## ear_out
.ee
eee
ee.
e..

## bs_back
..lllll
.llllll
llooooo
ooooooo
doooooo
.dooooo

## bs_front
lll...
lllo..
ooooo.
ooooco
ooccco
occccs

## body_crouch
..llllllll...
.lllllllllo..
llooooooooooo
dddooooooccco
.dddddccccccs

## leg
oo
oo
oo
cc

## leg_f
oo.
oo.
.oo
.cc

## leg_b
.oo
.oo
oo.
cc.

## leg_u
oo.
.oo
.cc

## leg_s
oo
cc

## tail
o...
oo..
.oo.
..oo

## tail_b
.oo.
.oo.
..oo
..oo

## tail_c
....
....
.o..
.oo.
..oo

## tail_d
oo..
oo..
.oo.
..oo

## tail_sit
.oo
oooo

## tail_sit_b
..o
.oo
ooo

## collar
b.
bb
.y

## body_sit
........lllooo
.......lloooooo
......llooooooc
.....llooooooccc
....looooooooccc
...loooooooooccc
..looooooooooccc
..oooooooooooccc
.dooooooooooooccc
.ddooooooooooo.
..ddddddsssss..

## leg_sit
oo
oo
oo
oo
oo
cc

## scratch
.o
oo
o.
o.

## body_lie
..lllllllllll...
.llllllllllllll.
lloooooooooooooo
ooooooooooooooo.
dddooooooooodddd

## paws
cc
cc

## tail_lie
....
oo..
.ooo
....


## leg_t
oo
oo
oo
oo
cc

## arm_fwd
ooooc
ooccc

## head_yawn
..oooo....
.ollllo...
ollloooo..
oooooooooo
ooookkkccn
.oooooccck
.ooocmmmmm
..oocmmmtt
...oss.ttt

## body_lie_b
...lllllllll....
..lllllllllllll.
.lloooooooooooo.
ooooooooooooooo.
ooooooooooooooo.
dddooooooooodddd

## scr_a
.oo
.od
od.
od.
o..

## scr_b
oo.
oo.
od.
od.
o..
"""
