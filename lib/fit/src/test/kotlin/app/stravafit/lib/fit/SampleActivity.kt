// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import java.time.Instant
import java.time.LocalDateTime

/**
 * A deterministic two-lap run whose values are exactly representable in FIT's fixed-point units,
 * so encode → decode must reproduce it bit for bit. Shared with the Python cross-validation script
 * (`testdata/files/crossval/crossvalidate.py`), which checks the same numbers.
 */
object SampleActivity {
    val START: Instant = Instant.parse("2026-05-10T07:00:00Z")
    const val RECORD_COUNT = 60

    private fun record(i: Int): FitRecord = FitRecord(
        timestamp = START.plusSeconds(i.toLong()),
        // Positions built from whole semicircles survive the degree ↔ semicircle conversion exactly.
        latitude = FitUnits.fromSemicircles(536_870_912L + 1_200L * i), // 45.0° + i·~0.0001°
        longitude = FitUnits.fromSemicircles(83_521_436L + 1_800L * i), // ~7.0°
        altitude = (1_250 + i) / 5.0 - 500.0, // -250 m + i·0.2 m … exercises the offset
        heartRate = 120 + i % 30,
        cadence = 85 + i % 5,
        distance = i * 325 / 100.0, // 3.25 m/s
        speed = (3_250 + 10 * (i % 7)) / 1000.0,
        power = if (i % 10 == 9) null else 250 + i, // gaps are written as invalid values
        temperature = 18 - i / 30,
    )

    val records: List<FitRecord> = List(RECORD_COUNT) { record(it) }

    private val mid: Instant = START.plusSeconds(30)
    private val end: Instant = START.plusSeconds(RECORD_COUNT - 1L)

    val activity = FitActivity(
        fileId = FitFileId(
            manufacturer = FitManufacturer.DEVELOPMENT,
            product = 1,
            serialNumber = 42_424_242L,
            timeCreated = START,
            productName = "lib-fit test",
        ),
        records = records,
        events = listOf(
            FitEvent(START, FitEventCode.TIMER, FitEventType.START, eventGroup = 0),
            FitEvent(end, FitEventCode.TIMER, FitEventType.STOP_ALL, eventGroup = 0),
        ),
        laps = listOf(
            FitLap(
                timestamp = mid.minusSeconds(1), startTime = START, totalElapsedTime = 29.0, totalTimerTime = 29.0,
                totalDistance = 94.25, startLatitude = records[0].latitude, startLongitude = records[0].longitude,
                endLatitude = records[29].latitude, endLongitude = records[29].longitude, totalCalories = 9,
                avgSpeed = 3.25, maxSpeed = 3.31, avgHeartRate = 134, maxHeartRate = 149, avgCadence = 87,
                maxCadence = 89, avgPower = 264, maxPower = 278, totalAscent = 6, totalDescent = 0,
                intensity = FitIntensity.ACTIVE, lapTrigger = FitLapTrigger.DISTANCE, sport = FitSport.RUNNING,
            ),
            FitLap(
                timestamp = end, startTime = mid, totalElapsedTime = 29.5, totalTimerTime = 29.0,
                totalDistance = 94.25, totalCalories = 10, avgSpeed = 3.26, maxSpeed = 3.31,
                avgHeartRate = 134, maxHeartRate = 149, intensity = FitIntensity.COOLDOWN,
                lapTrigger = FitLapTrigger.SESSION_END, sport = FitSport.RUNNING,
            ),
        ),
        sessions = listOf(
            FitSession(
                timestamp = end, startTime = START, sport = FitSport.RUNNING, subSport = FitSubSport.TRAIL,
                totalElapsedTime = 59.0, totalTimerTime = 58.5, totalDistance = 191.75,
                startLatitude = records[0].latitude, startLongitude = records[0].longitude, totalCalories = 19,
                avgSpeed = 3.255, maxSpeed = 3.31, avgHeartRate = 134, maxHeartRate = 149, avgCadence = 87,
                maxCadence = 89, avgPower = 279, maxPower = 308, totalAscent = 12, totalDescent = 0,
                firstLapIndex = 0, numLaps = 2,
            ),
        ),
        activity = FitActivitySummary(
            timestamp = end,
            totalTimerTime = 58.5,
            numSessions = 1,
            localTimestamp = LocalDateTime.parse("2026-05-10T09:00:59"),
        ),
    )
}
