package com.example.minimalistlauncher

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.max

private const val TAG = "FocusViewModel"
private const val HEARTBEAT_PERSIST_INTERVAL_MS = 30_000L

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val store = DataStoreManager(ctx)

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _durationSec = MutableStateFlow(0)
    val durationSec: StateFlow<Int> = _durationSec

    private val _remainingSec = MutableStateFlow(0)
    val remainingSec: StateFlow<Int> = _remainingSec

    private var endElapsedMs: Long = 0L
    private var tickerJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        viewModelScope.launch {
            // read prefs once
            val prefs = ctx.dataStore.data.first()
            val persistedActive = prefs[Keys.FOCUS_ACTIVE] ?: false
            val persistedPaused = prefs[Keys.FOCUS_PAUSED] ?: false
            val persistedRemaining = prefs[Keys.FOCUS_REMAINING_SEC]
            val persistedEnd = prefs[Keys.FOCUS_END_ELAPSED_MS]
            val persistedDur = prefs[Keys.FOCUS_DURATION_SEC]

            if (persistedActive && persistedPaused && persistedRemaining != null && persistedDur != null) {
                // session was paused -> restore paused remaining
                _durationSec.value = persistedDur
                _remainingSec.value = persistedRemaining
                _isActive.value = true
                _isRunning.value = false
                // clear any monotonic end because paused uses remaining store
                endElapsedMs = 0L
                Log.d(TAG, "Restored paused focus: remaining=${persistedRemaining}s")
            } else if (persistedActive && persistedEnd != null && persistedDur != null) {
                // active and running session: restore using monotonic end
                endElapsedMs = persistedEnd
                _durationSec.value = persistedDur
                val remMs = endElapsedMs - SystemClock.elapsedRealtime()
                val remSec = max(0, (remMs / 1000L).toInt())
                _remainingSec.value = remSec
                _isActive.value = remSec > 0
                _isRunning.value = remSec > 0
                if (remSec > 0) {
                    startTicker()
                    startHeartbeat()
                } else {
                    clearPersistedSessionNoSuspend()
                }
                Log.d(TAG, "Restored running focus: remaining=${remSec}s")
            } else if (persistedActive && prefs[Keys.FOCUS_START_MS] != null && persistedDur != null) {
                // fallback migration from wall-clock start (best-effort)
                try {
                    val wallStart = prefs[Keys.FOCUS_START_MS]!!
                    val durationSec = persistedDur
                    val nowWall = System.currentTimeMillis()
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val elapsedStart = nowElapsed - (nowWall - wallStart)
                    endElapsedMs = elapsedStart + durationSec * 1000L
                    _durationSec.value = durationSec
                    val remMs = endElapsedMs - nowElapsed
                    val remSec = max(0, (remMs / 1000L).toInt())
                    _remainingSec.value = remSec
                    _isActive.value = remSec > 0
                    _isRunning.value = remSec > 0
                    store.writeFocusEndElapsed(endElapsedMs)
                    if (remSec > 0) {
                        startTicker()
                        startHeartbeat()
                    } else clearPersistedSessionNoSuspend()
                    Log.d(TAG, "Migrated old start -> remaining=${remSec}s")
                } catch (t: Throwable) {
                    Log.w(TAG, "Migration failed: ${t.message}")
                    clearPersistedSessionNoSuspend()
                }
            } else {
                clearLocalState()
            }
        }
    }

    fun start(durationSec: Int, type: String = "custom") {
        viewModelScope.launch {
            val now = SystemClock.elapsedRealtime()
            endElapsedMs = now + durationSec * 1000L
            _durationSec.value = durationSec
            _remainingSec.value = durationSec
            _isActive.value = true
            _isRunning.value = true

            // persist running session (end based)
            store.writeFocusSession(System.currentTimeMillis(), durationSec, type)
            store.writeFocusEndElapsed(endElapsedMs)
            // ensure paused flags/remaining cleared
            store.setFocusPaused(false)
            store.setFocusRemainingSeconds(null)

            startTicker()
            startHeartbeat()
        }
    }

    /**
     * Pause must:
     *  - cancel the ticker first to stop updates,
     *  - capture remainingSec (which is stable after ticker stopped),
     *  - persist the paused state + remaining seconds,
     *  - clear any monotonic end so that time does not tick while paused.
     */
    fun pause() {
        viewModelScope.launch {
            if (!_isActive.value) return@launch
            // stop ticking before reading remaining to avoid race
            stopTicker()
            stopHeartbeat()

            _isRunning.value = false
            val rem = _remainingSec.value.coerceAtLeast(0)
            _remainingSec.value = rem
            // persist pause + remaining; clear monotonic end because paused shouldn't tick
            store.setFocusRemainingSeconds(rem)
            store.setFocusPaused(true)
            store.setFocusActive(true) // keep active
            endElapsedMs = 0L
            store.clearFocusEndElapsed()
            Log.d(TAG, "Paused focus, remaining=${rem}s")
        }
    }

    /**
     * Resume reads the stored remaining (prefer local) and creates a new monotonic end time.
     */
    fun resume() {
        viewModelScope.launch {
            if (!_isActive.value) return@launch
            // determine remaining: prefer local _remainingSec, else read persisted remaining
            val rem = _remainingSec.value.takeIf { it > 0 } ?: run {
                val prefs = ctx.dataStore.data.first()
                prefs[Keys.FOCUS_REMAINING_SEC] ?: 0
            }
            if (rem <= 0) {
                // nothing to resume
                _isRunning.value = false
                _isActive.value = false
                clearPersistedSession()
                return@launch
            }

            val now = SystemClock.elapsedRealtime()
            endElapsedMs = now + rem * 1000L
            _remainingSec.value = rem
            _isRunning.value = true
            _isActive.value = true

            // persist running end and clear paused markers
            store.writeFocusEndElapsed(endElapsedMs)
            store.setFocusPaused(false)
            store.setFocusRemainingSeconds(null)
            store.setFocusActive(true)

            startTicker()
            startHeartbeat()
            Log.d(TAG, "Resumed focus, remaining=${rem}s, endElapsed=$endElapsedMs")
        }
    }

    fun stop() {
        viewModelScope.launch {
            clearLocalState()
            clearPersistedSession()
            stopTicker()
            stopHeartbeat()
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            _isRunning.value = true
            while (_isRunning.value && _isActive.value) {
                val now = SystemClock.elapsedRealtime()
                val remMs = max(0L, endElapsedMs - now)
                val remSec = (remMs / 1000L).toInt()
                _remainingSec.value = remSec
                if (remSec <= 0) {
                    _isRunning.value = false
                    _isActive.value = false
                    clearPersistedSession()
                    break
                }
                val delayMs = remMs % 1000L
                delay(if (delayMs == 0L) 1000L else delayMs)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun startHeartbeat() {
        if (HEARTBEAT_PERSIST_INTERVAL_MS <= 0) return
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            while (_isActive.value && _isRunning.value) {
                delay(HEARTBEAT_PERSIST_INTERVAL_MS)
                try {
                    if (endElapsedMs > 0L) store.writeFocusEndElapsed(endElapsedMs)
                } catch (_: Throwable) { }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun clearLocalState() {
        _isActive.value = false
        _isRunning.value = false
        _durationSec.value = 0
        _remainingSec.value = 0
        endElapsedMs = 0L
    }

    private suspend fun clearPersistedSession() {
        try {
            store.clearFocusSession()
            store.clearFocusEndElapsed()
            store.setFocusPaused(false)
            store.setFocusRemainingSeconds(null)
        } catch (t: Throwable) {
            Log.w(TAG, "clearPersistedSession failed: ${t.message}")
        }
    }

    private fun clearPersistedSessionNoSuspend() {
        viewModelScope.launch { clearPersistedSession() }
    }

    override fun onCleared() {
        super.onCleared()
        stopTicker()
        stopHeartbeat()
    }
}
