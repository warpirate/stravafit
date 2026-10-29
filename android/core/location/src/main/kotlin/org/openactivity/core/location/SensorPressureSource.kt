// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.channels.BufferOverflow
import org.openactivity.recording.EngineInput
import javax.inject.Inject

/**
 * Barometer readings from `TYPE_PRESSURE` (record detail 3.6), throttled to one per second. The engine
 * calibrates them against GPS altitude and uses them for elevation gain.
 */
class SensorPressureSource @Inject constructor(
    @ApplicationContext context: Context,
) : PressureSource {
    private val manager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    override val isAvailable: Boolean get() = sensor != null

    override fun readings(): Flow<EngineInput.Pressure> {
        val sm = manager ?: return emptyFlow()
        val barometer = sensor ?: return emptyFlow()
        return callbackFlow {
            var lastNanos = Long.MIN_VALUE
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // Sensor timestamps are not guaranteed to share the elapsedRealtime base on every
                    // device, so stamp readings with the clock the location fixes use.
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (lastNanos != Long.MIN_VALUE && now - lastNanos < MIN_SPACING_NANOS) return
                    val hpa = event.values.firstOrNull() ?: return
                    lastNanos = now
                    trySend(EngineInput.Pressure(now, hpa.toDouble()))
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, barometer, SAMPLING_PERIOD_US)
            awaitClose { sm.unregisterListener(listener) }
        }.buffer(1, BufferOverflow.DROP_OLDEST)
    }

    private companion object {
        const val SAMPLING_PERIOD_US = 500_000
        const val MIN_SPACING_NANOS = 1_000_000_000L
    }
}
