package sk.vladko.pill

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import sk.vladko.pill.agent.StateDump
import sk.vladko.pill.engine.PillSession
import java.io.FileDescriptor
import java.io.PrintWriter

/** Creates the session while Android keeps the service connected; forwards window and key events to it. */
class PillAccessibilityService : AccessibilityService() {
    private var session: PillSession? = null

    override fun onServiceConnected() {
        closeSession()
        val s = PillSession(this, pill)
        session = s
        pill.session = s
    }

    override fun onDestroy() {
        closeSession()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        closeSession()
        return super.onUnbind(intent)
    }

    private fun closeSession() {
        session?.close()
        if (pill.session === session) pill.session = null
        session = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        session?.screen?.windowChanged()
        event?.packageName?.toString()?.let { session?.appInFront(it) }
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean = session?.onKey(event) == true

    /** `adb shell dumpsys activity service sk.vladko.pill/.PillAccessibilityService` prints this one JSON line. */
    override fun dump(
        fd: FileDescriptor?,
        writer: PrintWriter,
        args: Array<out String>?,
    ) {
        val s = session ?: return writer.println("{}")
        writer.println(StateDump.json(s.snapshot()).toString())
    }
}
