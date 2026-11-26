package sk.vladko.pill.rules

enum class Verdict { Pass, Swallow, SwallowAndFire }

/** One key passes at once. The other key going down within 150 ms is swallowed (down, repeats, up) and fires once. */
class VolumePair {
    companion object {
        const val WINDOW_MS = 150L
    }

    private val downAt = HashMap<Boolean, Long>()
    private val swallowed = HashSet<Boolean>()

    fun onKey(
        up: Boolean,
        volumeUp: Boolean,
        repeat: Int,
        timeMs: Long,
    ): Verdict {
        if (up) return if (swallowed.remove(volumeUp)) Verdict.Swallow else Verdict.Pass
        if (volumeUp in swallowed) return Verdict.Swallow
        if (repeat > 0) return Verdict.Pass
        val otherTime = downAt[!volumeUp]
        if (otherTime != null && timeMs - otherTime in 0..WINDOW_MS) {
            downAt.clear()
            swallowed += volumeUp
            return Verdict.SwallowAndFire
        }
        downAt[volumeUp] = timeMs
        return Verdict.Pass
    }
}
