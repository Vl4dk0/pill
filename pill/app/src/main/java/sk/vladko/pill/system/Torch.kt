package sk.vladko.pill.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The camera flash. [on] follows the real state while [watch] is held, also when another app switches it. */
class Torch(
    context: Context,
) {
    private val manager = context.getSystemService(CameraManager::class.java)

    /** The back camera's flash; any flash when there is no back camera with one. Probed on first use. */
    private val cameraId: String? by lazy {
        runCatching {
            val flash =
                manager.cameraIdList.filter {
                    manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ==
                        true
                }
            flash.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
                ?: flash.firstOrNull()
        }.onFailure { Log.w("PILL", "torch probe failed", it) }.getOrNull()
    }

    val available: Boolean get() = cameraId != null

    var on by mutableStateOf(false)
        private set

    fun toggle() {
        val id = cameraId ?: return
        runCatching { manager.setTorchMode(id, !on) }.onFailure { Log.w("PILL", "torch failed", it) }
    }

    /** Follows the flash state until closed. */
    fun watch(): AutoCloseable {
        val callback =
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(
                    id: String,
                    enabled: Boolean,
                ) {
                    if (id == cameraId) on = enabled
                }
            }
        manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        return AutoCloseable { manager.unregisterTorchCallback(callback) }
    }
}
