package com.example.minimalistlauncher

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * ShakeFlashlightManager
 *
 * - Uses SENSOR_DELAY_NORMAL for lower-power sampling
 * - Registers a CameraManager.TorchCallback to keep torchOn state accurate
 * - Safe start()/stop() that register/unregister sensor and torch callbacks
 * - Requires android.permission.CAMERA in AndroidManifest and runtime permission check before toggling torch
 */
class ShakeFlashlightManager(
    private val context: Context,
    private val onTorchToggled: ((isOn: Boolean) -> Unit)? = null
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeFlashlight"
        // tuning: 2.7-3.5 is common; increase to reduce false positives
        private const val SHAKE_THRESHOLD_G = 2.8f
        private const val SHAKE_DEBOUNCE_MS = 900L     // ignore shakes within 900ms
        private const val VIBRATION_MS = 40L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    // last shake time to debounce
    @Volatile private var lastShakeTs = 0L

    // cached camera id that has flash
    private var cameraWithFlashId: String? = null

    // current torch state (kept in sync by TorchCallback)
    @Volatile private var torchOn: Boolean = false

    private val scope = CoroutineScope(Dispatchers.Default)
    private var listenJob: Job? = null

    // Torch callback: updates torchOn when system torch state changes externally
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            torchOn = enabled
            onTorchToggled?.invoke(torchOn)
        }
    }

    @MainThread
    fun start() {
        if (accelerometer == null) return
        if (cameraWithFlashId == null) cameraWithFlashId = findCameraWithFlash()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.registerTorchCallback(torchCallback, null)
            }
        } catch (t: Throwable) { /* ignore */ }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL) // low power
        scope.launch { syncTorchStateCached() }
    }

    @MainThread
    fun stop() {
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) cameraManager.unregisterTorchCallback(torchCallback) } catch (_: Exception) {}
        listenJob?.cancel(); listenJob = null
    }

    // Find first camera that reports flash availability
    private fun findCameraWithFlash(): String? {
        return try {
            for (id in cameraManager.cameraIdList) {
                val map = cameraManager.getCameraCharacteristics(id)
                val has = map.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (has) return id
            }
            null
        } catch (t: Throwable) {
            Log.w(TAG, "findCameraWithFlash failed: ${t.message}")
            null
        }
    }

    // Optionally check permission and set torch state externally
    fun isTorchOn(): Boolean = torchOn

    /**
     * Forces torch state (will check CAMERA permission)
     * Returns true if the call attempted to change (or set) state.
     */
    fun setTorchState(forceOn: Boolean): Boolean {
        val camId = cameraWithFlashId ?: findCameraWithFlash()
        if (camId == null) {
            Log.w(TAG, "No camera with flash available")
            return false
        }
        // Check runtime permission before toggling
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing CAMERA permission; cannot set torch")
            return false
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(camId, forceOn)
                // torchCallback will update torchOn and notify
                return true
            }
        } catch (e: CameraAccessException) {
            Log.w(TAG, "CameraAccessException in setTorchState: ${e.message}")
        } catch (t: Throwable) {
            Log.w(TAG, "Error toggling torch: ${t.message}")
        }
        return false
    }

    // Toggle torch using cached camera id, will check permission
    private fun toggleTorch() {
        val cameraId = cameraWithFlashId ?: findCameraWithFlash()
        if (cameraId == null) {
            Log.w(TAG, "No camera with flash found")
            return
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing CAMERA permission; toggle skipped")
            return
        }
        try {
            val newState = !torchOn
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, newState)
                // torchCallback should fire and update torchOn; but set optimistically too
                torchOn = newState
                onTorchToggled?.invoke(torchOn)
                Log.d(TAG, "toggleTorch -> $torchOn (requested)")
            }
        } catch (e: CameraAccessException) {
            Log.w(TAG, "CameraAccessException toggling torch: ${e.message}")
        } catch (t: Throwable) {
            Log.w(TAG, "Error toggling torch: ${t.message}")
        }
    }

    // best-effort read; register a CameraManager.TorchCallback already covers most cases.
    private fun syncTorchStateCached() {
        try {
            // There's no direct getter on all devices; rely on the callback.
            // Optionally you could try a small probe: attempt to set to current cached value and revert,
            // but that would be intrusive. We keep it passive here.
        } catch (_: Throwable) {}
    }

    // vibration feedback
    private fun pulseFeedback() {
        try {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(VIBRATION_MS)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        // debounce temporally to avoid frequent toggles
        if (now - lastShakeTs < SHAKE_DEBOUNCE_MS) return

        val ax = event.values.getOrNull(0) ?: 0f
        val ay = event.values.getOrNull(1) ?: 0f
        val az = event.values.getOrNull(2) ?: 0f

        // calculate g-force
        val gX = ax / SensorManager.GRAVITY_EARTH
        val gY = ay / SensorManager.GRAVITY_EARTH
        val gZ = az / SensorManager.GRAVITY_EARTH

        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce > SHAKE_THRESHOLD_G) {
            // record and toggle torch on a background job to avoid any heavy work on sensor thread
            lastShakeTs = now
            scope.launch {
                pulseFeedback()
                toggleTorch()
            }
        }
    }


}
