// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.geo.Spherical
import org.openactivity.metrics.ElevationAccumulator
import org.openactivity.metrics.ElevationMetrics
import org.openactivity.metrics.ElevationSource
import org.openactivity.metrics.SplitAccumulator
import org.openactivity.model.Distance
import org.openactivity.model.Lap
import org.openactivity.model.LapTrigger
import org.openactivity.model.LatLng
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The recording engine: a deterministic state machine that turns sensor and user events into live
 * [RecordingState] and, on FINISH, a [FinishedActivity].
 *
 * Pipeline for each location fix:
 * 1. **Ordering** – fixes that are not strictly newer than the previous fix are dropped.
 * 2. **Acceptance** – fixes with poor accuracy are ignored; fixes implying an impossible speed for the
 *    sport (beyond accuracy slack) are rejected as jumps, unless they persist (then a new segment starts).
 * 3. **Smoothing** – a light constant-velocity Kalman filter with outlier gating ([PositionFilter]).
 * 4. **Motion** – smoothed speed, stationary detection and auto-pause with hysteresis ([MotionDetector]).
 * 5. **Distance** – the fused (Doppler-aided) speed is integrated between fixes, or smoothed positions are
 *    summed when the receiver gives no speed; nothing is added while paused or stationary. After a GPS gap
 *    the distance is bridged with a straight line from the last position, never with a guess.
 *
 * All time comes from [EngineInput.monotonicNanos]; wall-clock values only label the start time. The engine
 * has no clock, randomness or I/O of its own: the same inputs always produce the same outputs, and
 * [snapshot]/[restore] round-trip the complete state for crash recovery.
 *
 * Not thread-safe: feed it from one thread (the recording service's event loop).
 */
public class RecordingEngine(public val config: RecordingConfig) {
    private val profile = config.profile
    private val autoPauseActive = config.autoPauseEnabled && profile.autoPause != null

    private var status = RecordingStatus.IDLE
    private var offsetNanos = 0L
    private var clockNanos: Long? = null
    private var startNanos = 0L
    private var finishNanos = 0L
    private var wallAnchorNanos: Long? = null
    private var wallAnchorMillis = 0L
    private var timerNanos = 0L
    private var movingNanos = 0L
    private var openPauseKind: PauseKind? = null
    private var openPauseStart = 0L
    private var lastTimerStart = 0L
    private var maxSpeed = 0.0
    private val pauses = ArrayList<PauseInterval>()
    private var autoPauseCount = 0
    private var distance = 0.0
    private var anchor: LatLng? = null
    private var bridgePending = false
    private var previousFusedSpeed = 0.0
    private var dopplerTrusted = true
    private var dopplerCheckAnchor: LatLng? = null
    private var dopplerCheckStart = 0L
    private var dopplerCheckDistance = 0.0
    private var segment = 0
    private var lastFixNanos: Long? = null
    private var lastAccepted: AcceptedFix? = null
    private var rejectsInARow = 0
    private var lastAccuracy: Double? = null
    private var lastAccuracyNanos: Long? = null
    private val gaps = ArrayList<GpsGap>()
    private var mockSeen = false
    private var interrupted = false
    private val filter = PositionFilter(profile.processNoiseAccel)
    private val motion = MotionDetector(profile)
    private val gpsElevation = ElevationAccumulator(ElevationSource.GPS.hysteresisMeters)
    private val baroElevation = ElevationAccumulator(ElevationSource.BAROMETER.hysteresisMeters)
    private var hasBarometer = false
    private var verticalAccuracyAvg = Double.NaN
    private var carriedGain = 0.0
    private var carriedLoss = 0.0
    private var baroAltitude = Double.NaN
    private var baroCalibrationSum = 0.0
    private var baroCalibrationCount = 0
    private var stepSpeed = 0.0
    private var baroMin = Double.NaN
    private var baroMax = Double.NaN
    private var gpsAltMin = Double.NaN
    private var gpsAltMax = Double.NaN
    private var stillMovingNanos = 0L
    private var lastGpsAltitude = Double.NaN
    private var lastGpsAltitudeNanos = 0L
    private val splits = SplitAccumulator(config.unitSystem.splitLengthMeters)
    private val laps = ArrayList<Lap>()
    private var lapStartNanos = 0L
    private var lapStartDistance = 0.0
    private var lapStartMovingNanos = 0L
    private var lapStartGain = 0.0
    private val points = ArrayList<TrackPoint>()
    private val heartRate = SensorChannel()
    private val power = SensorChannel()
    private val cadence = SensorChannel()

    /** The finished activity once FINISH has been processed, else null. */
    public var finishedActivity: FinishedActivity? = null
        private set

    /** The current live state. */
    public val state: RecordingState get() = buildState()

    /** Processes one input and returns the resulting state. Inputs after FINISH or DISCARD are ignored. */
    public fun process(input: EngineInput): RecordingState {
        if (status == RecordingStatus.FINISHED || status == RecordingStatus.DISCARDED) return state
        val t = input.monotonicNanos + offsetNanos
        when (input) {
            is EngineInput.Location -> onLocation(input, t)
            is EngineInput.Tick -> advance(t)
            is EngineInput.Pressure -> {
                advance(t)
                onPressure(input.hectopascals)
            }
            is EngineInput.HeartRate -> heartRate.onReading(input.bpm.toDouble(), advance(t), isRecording)
            is EngineInput.Power -> power.onReading(input.watts.toDouble(), advance(t), isRecording)
            is EngineInput.Cadence -> cadence.onReading(input.perMinute, advance(t), isRecording)
            is EngineInput.UserAction -> {
                input.wallClockMillis?.let { anchorWallClock(t, it) }
                onAction(input.action, advance(t))
            }
        }
        return state
    }

    /** Processes [inputs] in order and returns the final state. */
    public fun processAll(inputs: Iterable<EngineInput>): RecordingState {
        for (i in inputs) process(i)
        return state
    }

    private val isRecording: Boolean get() = status == RecordingStatus.RECORDING

    // ---------------------------------------------------------------- clock

    /** Moves the engine clock forward to [t] (never backwards) and returns the effective time. */
    private fun advance(t: Long): Long {
        val c = clockNanos
        if (c == null) {
            clockNanos = t
            return t
        }
        if (t <= c) return c
        val dt = t - c
        if (isRecording) {
            timerNanos += dt
            if (countsAsMoving(t)) {
                movingNanos += dt
                stillMovingNanos += dt
            }
            heartRate.accumulate(t, dt)
            power.accumulate(t, dt)
            cadence.accumulate(t, dt)
        }
        clockNanos = t
        return t
    }

    private fun countsAsMoving(t: Long): Boolean {
        if (autoPauseActive) return true
        val fix = lastAccepted ?: return false
        if ((t - fix.nanos) / 1e9 > profile.movingRule.gapCapSeconds) return false
        val threshold = profile.movingRule.thresholdMps
        // Either the motion detector sees movement, or the distance actually grew at moving speed. The
        // second matters when the receiver reports a Doppler speed of 0 while the position moves.
        return (!motion.isStationary && motion.smoothedSpeed >= threshold) || stepSpeed >= threshold
    }

    private fun anchorWallClock(t: Long, wallMillis: Long) {
        if (wallAnchorNanos == null) {
            wallAnchorNanos = t
            wallAnchorMillis = wallMillis
        }
    }

    private fun wallAt(t: Long): Long {
        val anchorT = wallAnchorNanos ?: return 0L
        return wallAnchorMillis + Math.floorDiv(t - anchorT, 1_000_000L)
    }

    // ------------------------------------------------------------- location

    private fun onLocation(loc: EngineInput.Location, t: Long) {
        // A fix taken before START (the provider's cached last location, delivered when a subscription
        // opens) or long before the engine clock is stale: it says where the athlete was, not where they
        // are, and anchoring the track on it adds a phantom jump to the distance.
        if (status.isActive && (t < startNanos || clockNanos?.let { it - t > STALE_FIX_NANOS } == true)) return
        val previousFix = lastFixNanos
        if (previousFix != null && t <= previousFix) return
        lastFixNanos = t
        anchorWallClock(t, loc.wallClockMillis)
        advance(t)
        lastAccuracy = loc.horizontalAccuracyMeters
        lastAccuracyNanos = t
        if (!status.isActive) return
        if (!loc.latitude.isFinite() || !loc.longitude.isFinite() ||
            loc.latitude !in -90.0..90.0 || loc.longitude !in -180.0..180.0
        ) return
        if (loc.isMock) mockSeen = true
        val reported = loc.horizontalAccuracyMeters
        if (reported != null && (!reported.isFinite() || reported > config.maxAccuracyMeters)) return
        val accuracy = reported?.coerceAtLeast(MIN_ACCURACY) ?: UNKNOWN_ACCURACY
        val raw = LatLng(loc.latitude, loc.longitude)

        val prev = lastAccepted
        if (prev != null && !isPlausible(prev, raw, t, accuracy)) {
            rejectsInARow++
            if (rejectsInARow < MAX_REJECTS_IN_A_ROW) return
            // The athlete really is somewhere else (e.g. the last accepted fix was the outlier):
            // start a new segment rather than adding a straight line.
            breakSegment()
        }
        rejectsInARow = 0
        val gapNanos = prev?.let { t - it.nanos } ?: 0L
        val afterGap = prev != null && gapNanos / 1e9 > config.gapResetSeconds
        val doppler = loc.speedMps?.takeIf { dopplerTrusted && it.isFinite() && it >= 0.0 }
        val course = loc.bearingDegrees.takeIf { doppler != null }
        if (!filter.isInitialized || afterGap) {
            filter.reset(raw, t, accuracy, doppler, course)
        } else if (!filter.update(raw, t, accuracy, doppler, course)) {
            return
        }
        lastAccepted = AcceptedFix(raw.latitude, raw.longitude, t, accuracy)
        val speed = doppler ?: filter.speed
        motion.onSpeed(speed, t)
        if (isRecording && !motion.isStationary) {
            maxSpeed = maxOf(maxSpeed, motion.smoothedSpeed.coerceAtMost(profile.maxSpeedMps))
        }
        loc.altitudeMeters?.takeIf { it.isFinite() }?.let { onGpsAltitude(it, loc.verticalAccuracyMeters, t) }

        if (autoPauseActive && (status == RecordingStatus.RECORDING || status == RecordingStatus.AUTO_PAUSED)) {
            val resumeAllowed = (reported ?: 0.0) <= config.autoResumeMaxAccuracyMeters
            val event = motion.autoPauseDecision(t, status == RecordingStatus.AUTO_PAUSED, resumeAllowed)
            when (event.decision) {
                AutoPauseDecision.PAUSE -> beginPause(PauseKind.AUTO, t, event.effectiveNanos)
                AutoPauseDecision.RESUME -> endPause(t, event.effectiveNanos)
                AutoPauseDecision.NONE -> Unit
            }
        }

        val position = filter.position
        val fusedSpeed = filter.speed
        if (isRecording) {
            val before = distance
            accumulateDistance(position, fusedSpeed, t, prev, afterGap, accuracy, velocityMode = doppler != null)
            val dt = gapNanos / 1e9
            if (prev != null && !afterGap && dt > 0.0) {
                val instant = (distance - before) / dt
                stepSpeed += STEP_SPEED_ALPHA * (instant - stepSpeed)
            } else {
                stepSpeed = 0.0
            }
        } else {
            bridgePending = true
            stepSpeed = 0.0
        }
        previousFusedSpeed = fusedSpeed
        val altitude = currentAltitude()
        points += TrackPoint(
            offsetNanos = t - startNanos,
            epochMillis = wallAt(t),
            latitude = position.latitude,
            longitude = position.longitude,
            rawLatitude = raw.latitude,
            rawLongitude = raw.longitude,
            accuracyMeters = reported,
            altitudeMeters = altitude,
            speedMps = if (isRecording && !motion.isStationary) motion.smoothedSpeed else 0.0,
            distanceMeters = distance,
            paused = !isRecording,
            segment = segment,
            heartRateBpm = heartRate.current(t)?.toInt(),
            cadence = cadence.current(t),
            powerWatts = power.current(t)?.toInt(),
        )
        splits.add((t - startNanos) / 1e9, distance, movingNanos / 1e9, altitude ?: Double.NaN)
    }

    /**
     * Adds the distance covered since the previous accepted fix.
     *
     * - The first fix of a segment only sets the anchor.
     * - After a GPS gap, or when movement resumes after a pause or a stationary spell, the straight line
     *   from the anchor (the last counted position) is added.
     * - While stationary nothing is added, so position jitter cannot accumulate.
     * - Otherwise, when the receiver reports Doppler speed, the step is the fused speed integrated over the
     *   interval. Unlike summing noisy position differences this does not grow with position noise, which
     *   matters most at walking pace. Without Doppler speed the smoothed positions are summed in steps of
     *   at least [RecordingConfig.minStepMeters] (or half the accuracy).
     *
     * A 30 s consistency check compares the integrated distance with the straight-line displacement; if the
     * displacement is clearly larger, the receiver's speed is not trusted for the rest of the activity and
     * the shortfall is added. The same happens when a "stationary" athlete drifts far from the anchor.
     */
    private fun accumulateDistance(
        position: LatLng,
        fusedSpeed: Double,
        t: Long,
        prev: AcceptedFix?,
        afterGap: Boolean,
        accuracy: Double,
        velocityMode: Boolean,
    ) {
        val a = anchor
        when {
            a == null -> {
                anchor = position
                bridgePending = false
                resetDopplerCheck(position, t)
            }
            afterGap -> {
                val step = Spherical.distance(a, position)
                distance += step
                stillMovingNanos = 0L
                anchor = position
                bridgePending = false
                recordGap(requireNotNull(prev).nanos, t - prev.nanos, step)
                resetDopplerCheck(position, t)
            }
            motion.isStationary -> {
                bridgePending = true
                // "Standing still" while drifting far beyond the accuracy means the receiver's speed is
                // wrong (some chipsets report 0): stop trusting it so movement is detected from positions.
                if (velocityMode && Spherical.distance(a, position) > STATIONARY_ESCAPE_M + 3 * accuracy) {
                    dopplerTrusted = false
                    motion.reset()
                }
            }
            bridgePending || !velocityMode || !dopplerTrusted || prev == null -> {
                val step = Spherical.distance(a, position)
                if (step >= max(config.minStepMeters, STEP_ACCURACY_FACTOR * accuracy)) {
                    // Movement resumed after standing still: the athlete covered this straight line over
                    // the last few fixes, not in this one interval. Spread it back over them.
                    if (bridgePending) backfillBridge(a, position, step, t)
                    distance += step
                    stillMovingNanos = 0L
                    anchor = position
                    bridgePending = false
                    resetDopplerCheck(position, t)
                }
            }
            else -> {
                val step = (previousFusedSpeed + fusedSpeed) / 2 * ((t - prev.nanos) / 1e9)
                distance += step
                if (step > 0.0) stillMovingNanos = 0L
                anchor = position
                dopplerCheckDistance += step
                if ((t - dopplerCheckStart) / 1e9 >= DOPPLER_CHECK_SECONDS) {
                    val displacement = dopplerCheckAnchor?.let { Spherical.distance(it, position) } ?: 0.0
                    if (displacement > DOPPLER_CHECK_RATIO * dopplerCheckDistance + 2 * accuracy + DOPPLER_CHECK_SLACK_M) {
                        dopplerTrusted = false
                        distance += displacement - dopplerCheckDistance
                    }
                    resetDopplerCheck(position, t)
                }
            }
        }
    }

    /**
     * Spreads a bridged [step] (from [from] to [to]) over the track points recorded since distance last
     * grew, by each point's progress along the bridge, and credits the moving time those intervals now
     * imply. Without this the whole stretch lands in the latest interval: it counts as distance but not as
     * moving time, which makes the pace too fast (a run started from a standstill with a receiver reporting
     * a Doppler speed of 0, for example). Points before the athlete set off keep their distance, so time
     * spent standing still stays non-moving.
     */
    private fun backfillBridge(from: LatLng, to: LatLng, step: Double, t: Long) {
        if (!isRecording || step <= 0.0) return
        val base = distance
        var s = points.size
        while (s > 0) {
            val p = points[s - 1]
            if (p.paused || p.segment != segment || p.distanceMeters != base) break
            s--
        }
        if (s >= points.size) return
        val cosLat = kotlin.math.cos(Math.toRadians(from.latitude))
        fun east(p: LatLng) = Math.toRadians(p.longitude - from.longitude) * cosLat * Spherical.EARTH_RADIUS_M
        fun north(p: LatLng) = Math.toRadians(p.latitude - from.latitude) * Spherical.EARTH_RADIUS_M
        val ux = east(to)
        val uy = north(to)
        val len = kotlin.math.hypot(ux, uy)
        if (len <= 0.0) return
        var progress = 0.0
        val threshold = profile.movingRule.thresholdMps
        var credit = 0L
        var prevNanos = points[s].offsetNanos + startNanos
        var prevDistance = base
        for (i in s until points.size) {
            val p = points[i]
            val pos = LatLng(p.latitude, p.longitude)
            progress = maxOf(progress, ((east(pos) * ux + north(pos) * uy) / len).coerceIn(0.0, len))
            val d = base + step * progress / len
            val at = p.offsetNanos + startNanos
            val dt = at - prevNanos
            val speed = if (dt > 0) (d - prevDistance) / (dt / 1e9) else 0.0
            if (dt > 0 && speed >= threshold) credit += dt
            points[i] = p.copy(distanceMeters = d, speedMps = if (dt > 0 && speed >= threshold) speed else p.speedMps)
            prevNanos = at
            prevDistance = d
        }
        val lastDt = t - prevNanos
        if (lastDt > 0 && (base + step - prevDistance) / (lastDt / 1e9) >= threshold) credit += lastDt
        val extra = credit - stillMovingNanos
        if (extra > 0) movingNanos += extra
    }

    private fun resetDopplerCheck(position: LatLng, t: Long) {
        dopplerCheckAnchor = position
        dopplerCheckStart = t
        dopplerCheckDistance = 0.0
    }

    private fun isPlausible(prev: AcceptedFix, p: LatLng, t: Long, accuracy: Double): Boolean {
        val dt = (t - prev.nanos) / 1e9
        val d = Spherical.distance(prev.latitude, prev.longitude, p.latitude, p.longitude)
        return d <= profile.maxSpeedMps * dt + prev.accuracy + accuracy
    }

    private fun recordGap(fromNanos: Long, durationNanos: Long, bridged: Double) {
        gaps += GpsGap(fromNanos - startNanos, durationNanos, bridged)
    }

    private fun breakSegment() {
        anchor = null
        filter.clear()
        lastAccepted = null
        segment++
    }

    // ------------------------------------------------------------ elevation

    /**
     * GPS altitude wanders slowly by several metres, so the gain hysteresis widens with the reported
     * vertical accuracy: [GPS_HYSTERESIS_PER_VACC] times its running average, never below the 10 m default.
     * On flat ground this keeps phantom climbing from GPS altitude drift under about 30 m per hour.
     */
    private fun onGpsAltitude(altitude: Double, verticalAccuracy: Double?, t: Long) {
        if (verticalAccuracy != null && verticalAccuracy.isFinite() && verticalAccuracy > 0.0) {
            verticalAccuracyAvg = if (verticalAccuracyAvg.isNaN()) {
                verticalAccuracy
            } else {
                verticalAccuracyAvg + VACC_ALPHA * (verticalAccuracy - verticalAccuracyAvg)
            }
        }
        // A jump far beyond what the athlete could climb since the last altitude is a discontinuity in the
        // receiver's altitude (a stale value from before the first real fix, a reference change), never
        // climbing: start a fresh baseline so it is not counted as gain or loss.
        val previous = lastGpsAltitude
        val jumped = previous.isFinite() && status.isActive &&
            kotlin.math.abs(altitude - previous) > ALTITUDE_STEP_BASE_M + ALTITUDE_STEP_RATE_MPS * ((t - lastGpsAltitudeNanos) / 1e9)
        lastGpsAltitude = altitude
        lastGpsAltitudeNanos = t
        if (jumped) {
            gpsElevation.rebase()
            // Until now the altitude never moved: it was a stale value, so the points recorded so far get the
            // corrected altitude instead of a phantom step at the start.
            val flatSoFar = gpsAltMin.isNaN() || gpsAltMax - gpsAltMin < STALE_ALTITUDE_FLAT_M
            if (flatSoFar && !baroUsable) {
                for (i in points.indices) {
                    if (points[i].altitudeMeters != null) points[i] = points[i].copy(altitudeMeters = altitude)
                }
                val st = splits.snapshot()
                if (st.completed.isEmpty() && st.hasSample) {
                    splits.restore(st.copy(lastAltitude = altitude, splitStartAltitude = altitude))
                }
                gpsAltMin = Double.NaN
                gpsAltMax = Double.NaN
            }
        }
        if (status.isActive) {
            gpsAltMin = if (gpsAltMin.isNaN()) altitude else minOf(gpsAltMin, altitude)
            gpsAltMax = if (gpsAltMax.isNaN()) altitude else maxOf(gpsAltMax, altitude)
        }
        // GPS altitude is always tracked, so a barometer that turns out to be stuck can fall back to it.
        // While manually paused (a lift, a car) altitude is tracked for display but never counted.
        if (status == RecordingStatus.PAUSED) gpsElevation.rebase()
        val h = if (verticalAccuracyAvg.isNaN()) {
            gpsElevation.hysteresisMeters
        } else {
            maxOf(gpsElevation.hysteresisMeters, GPS_HYSTERESIS_PER_VACC * verticalAccuracyAvg)
        }
        gpsElevation.add(altitude, h)
        if (hasBarometer && baroCalibrationCount < BARO_CALIBRATION_FIXES && baroAltitude.isFinite()) {
            baroCalibrationSum += altitude - baroAltitude
            baroCalibrationCount++
        }
    }

    private fun onPressure(hPa: Double) {
        if (!hPa.isFinite() || hPa <= 0.0) return
        hasBarometer = true
        baroAltitude = ElevationMetrics.pressureToAltitude(hPa)
        if (!status.isActive) return
        val wasUsable = baroUsable
        baroMin = if (baroMin.isNaN()) baroAltitude else minOf(baroMin, baroAltitude)
        baroMax = if (baroMax.isNaN()) baroAltitude else maxOf(baroMax, baroAltitude)
        if (status == RecordingStatus.PAUSED) baroElevation.rebase()
        baroElevation.add(baroAltitude)
        if (!wasUsable && baroUsable) {
            // Hand over from GPS without a jump in the totals.
            carriedGain = gpsElevation.gain - baroElevation.gain
            carriedLoss = gpsElevation.loss - baroElevation.loss
        }
    }

    /** Starts fresh elevation baselines so altitude changed during a pause or interruption is not counted. */
    private fun rebaseElevation() {
        gpsElevation.rebase()
        baroElevation.rebase()
    }

    /**
     * The barometer is used only while it is plausible. A sensor whose reading never changes while GPS
     * altitude clearly does (emulators, some broken or sealed sensors) is ignored in favour of GPS, so the
     * activity still gets an elevation profile instead of a flat line.
     */
    private val baroUsable: Boolean
        get() {
            if (!hasBarometer) return false
            // A barometer reading that has never moved is not trusted: a stuck or simulated sensor would
            // otherwise pin the altitude (and its calibration) to a stale value. Real sensors show noise
            // and small changes within moments, and then take over from GPS.
            val baroRange = if (baroMin.isNaN()) 0.0 else baroMax - baroMin
            return baroRange >= STUCK_BARO_RANGE_M
        }

    private fun currentAltitude(): Double? = when {
        baroUsable && baroAltitude.isFinite() -> {
            val offset = if (baroCalibrationCount > 0) baroCalibrationSum / baroCalibrationCount else 0.0
            baroAltitude + offset
        }
        gpsElevation.smoothed.isFinite() -> gpsElevation.smoothed
        else -> null
    }

    private val elevationGain: Double
        get() = if (baroUsable) carriedGain + baroElevation.gain else gpsElevation.gain

    private val elevationLoss: Double
        get() = if (baroUsable) carriedLoss + baroElevation.loss else gpsElevation.loss

    private val altitudeSource: AltitudeSource
        get() = when {
            baroUsable -> AltitudeSource.BAROMETER
            gpsElevation.smoothed.isFinite() -> AltitudeSource.GPS
            else -> AltitudeSource.NONE
        }

    // --------------------------------------------------------------- actions

    private fun onAction(action: Action, t: Long) {
        when (action) {
            Action.START -> if (status == RecordingStatus.IDLE) {
                status = RecordingStatus.RECORDING
                startNanos = t
                lapStartNanos = t
                lastTimerStart = t
            }
            Action.PAUSE -> when (status) {
                RecordingStatus.RECORDING -> beginPause(PauseKind.MANUAL, t)
                RecordingStatus.AUTO_PAUSED -> {
                    // Manual pause dominates auto-pause: it can only be resumed by the athlete.
                    endPause(t)
                    beginPause(PauseKind.MANUAL, t)
                }
                else -> Unit
            }
            Action.RESUME -> if (status == RecordingStatus.PAUSED) {
                endPause(t)
                breakSegment()
                motion.reset()
                rebaseElevation()
            }
            Action.LAP -> if (status.isActive) closeLap(t, LapTrigger.MANUAL)
            Action.FINISH -> if (status.isActive) finish(t)
            Action.DISCARD -> if (status.isActive || status == RecordingStatus.IDLE) {
                status = RecordingStatus.DISCARDED
                points.clear()
            }
        }
    }

    /**
     * Stops the timer at [t]. An auto-pause may take effect earlier ([effective], when the athlete actually
     * stopped); that span is moved from timer and moving time into the pause. Live timers can therefore
     * step back by a few seconds when auto-pause engages, in exchange for accurate moving time.
     */
    private fun beginPause(kind: PauseKind, t: Long, effective: Long = t) {
        val from = effective.coerceIn(maxOf(lastTimerStart, startNanos), t)
        val back = t - from
        timerNanos -= back
        movingNanos = (movingNanos - back).coerceAtLeast(lapStartMovingNanos)
        openPauseKind = kind
        openPauseStart = from
        if (kind == PauseKind.AUTO) autoPauseCount++
        status = if (kind == PauseKind.MANUAL) RecordingStatus.PAUSED else RecordingStatus.AUTO_PAUSED
    }

    /** Restarts the timer at [t]; an auto-resume may take effect earlier ([effective]). */
    private fun endPause(t: Long, effective: Long = t) {
        val kind = openPauseKind ?: return
        val to = effective.coerceIn(openPauseStart, t)
        val forward = t - to
        timerNanos += forward
        movingNanos += forward
        pauses += PauseInterval(kind, openPauseStart - startNanos, to - startNanos)
        openPauseKind = null
        lastTimerStart = to
        status = RecordingStatus.RECORDING
    }

    private fun closeLap(t: Long, trigger: LapTrigger) {
        laps += Lap(
            index = laps.size,
            startOffset = (lapStartNanos - startNanos).nanoseconds,
            elapsedTime = (t - lapStartNanos).nanoseconds,
            movingTime = (movingNanos - lapStartMovingNanos).nanoseconds,
            distance = Distance(distance - lapStartDistance),
            elevationGain = Distance(elevationGain - lapStartGain),
            trigger = trigger,
        )
        lapStartNanos = t
        lapStartDistance = distance
        lapStartMovingNanos = movingNanos
        lapStartGain = elevationGain
    }

    private fun finish(t: Long) {
        if (openPauseKind != null) endPause(t)
        // A lap pressed exactly at the end does not create an empty final lap.
        if (laps.isEmpty() || t > lapStartNanos) closeLap(t, LapTrigger.SESSION_END)
        finishNanos = t
        status = RecordingStatus.FINISHED
        val flags = buildSet {
            if (mockSeen) add(ActivityFlag.MOCK_LOCATION)
            if (gaps.any { it.bridgedMeters > config.significantGapMeters }) add(ActivityFlag.SIGNIFICANT_GPS_GAP)
            if (interrupted) add(ActivityFlag.RECOVERED_AFTER_INTERRUPTION)
        }
        finishedActivity = ActivityFinisher.build(
            ActivityFinisher.Input(
                config = config,
                autoPauseActive = autoPauseActive,
                startEpochMillis = wallAnchorNanos?.let { wallAt(startNanos) },
                elapsedNanos = finishNanos - startNanos,
                timerNanos = timerNanos,
                distanceMeters = distance,
                maxSpeedMps = maxSpeed,
                elevationGain = elevationGain,
                elevationLoss = elevationLoss,
                altitudeSource = altitudeSource,
                laps = laps.toList(),
                pauses = pauses.toList(),
                autoPauseCount = autoPauseCount,
                gaps = gaps.toList(),
                flags = flags,
                points = points.toList(),
                averageHeartRate = heartRate.average,
                maxHeartRate = heartRate.max,
                averagePower = power.average,
                maxPower = power.max,
                averageCadence = cadence.average,
            ),
        )
    }

    // ----------------------------------------------------------------- state

    private fun buildState(): RecordingState {
        val now = clockNanos ?: 0L
        val fin = finishedActivity
        val elapsedNanos = when {
            fin != null -> fin.elapsedTime.inWholeNanoseconds
            status == RecordingStatus.IDLE || status == RecordingStatus.DISCARDED -> 0L
            else -> now - startNanos
        }
        val moving = fin?.movingTime ?: movingNanos.nanoseconds
        val distanceNow = fin?.distanceMeters ?: distance
        val fixFresh = lastAccuracyNanos?.let { (now - it) / 1e9 <= config.gpsLostSeconds } == true
        val currentSpeed = if (isRecording && fixFresh && !motion.isStationary) motion.smoothedSpeed else 0.0
        val movingSeconds = moving.inWholeNanoseconds / 1e9
        return RecordingState(
            status = status,
            sport = config.sport,
            elapsed = elapsedNanos.nanoseconds,
            timerTime = if (status == RecordingStatus.IDLE || status == RecordingStatus.DISCARDED) Duration.ZERO else timerNanos.nanoseconds,
            movingTime = moving,
            distanceMeters = distanceNow,
            currentSpeedMps = currentSpeed,
            averageSpeedMps = if (movingSeconds > 0.0) distanceNow / movingSeconds else 0.0,
            splits = fin?.splits ?: splits.splits,
            laps = fin?.laps ?: laps.toList(),
            currentLap = LapProgress(
                index = laps.size,
                elapsed = if (status.isActive) lapTimerNanos(now).nanoseconds else Duration.ZERO,
                distanceMeters = distance - lapStartDistance,
            ),
            elevation = ElevationState(currentAltitude(), elevationGain, elevationLoss, altitudeSource),
            gpsQuality = if (fixFresh) GpsQuality.fromAccuracy(lastAccuracy) else GpsQuality.NONE,
            horizontalAccuracyMeters = lastAccuracy,
            heartRateBpm = heartRate.current(now)?.toInt(),
            powerWatts = power.current(now)?.toInt(),
            cadence = cadence.current(now),
            autoPauseCount = autoPauseCount,
            isMockLocation = mockSeen,
        )
    }

    /** Timer time of the current lap at [now]: like the main timer it stands still while paused. */
    private fun lapTimerNanos(now: Long): Long {
        var paused = 0L
        for (p in pauses) {
            val from = maxOf(p.startOffsetNanos + startNanos, lapStartNanos)
            val to = minOf(p.endOffsetNanos + startNanos, now)
            if (to > from) paused += to - from
        }
        if (openPauseKind != null) {
            val from = maxOf(openPauseStart, lapStartNanos)
            if (now > from) paused += now - from
        }
        return (now - lapStartNanos - paused).coerceAtLeast(0L)
    }

    // ------------------------------------------------------ snapshot/restore

    /** Captures the complete engine state. Restoring it with [restore] continues bit-for-bit identically. */
    public fun snapshot(): EngineSnapshot = EngineSnapshot(
        config = config,
        status = status,
        offsetNanos = offsetNanos,
        clockNanos = clockNanos,
        startNanos = startNanos,
        finishNanos = finishNanos,
        wallAnchorNanos = wallAnchorNanos,
        wallAnchorMillis = wallAnchorMillis,
        timerNanos = timerNanos,
        movingNanos = movingNanos,
        openPauseKind = openPauseKind,
        openPauseStart = openPauseStart,
        lastTimerStart = lastTimerStart,
        maxSpeedMps = maxSpeed,
        pauses = pauses.toList(),
        autoPauseCount = autoPauseCount,
        distanceMeters = distance,
        anchor = anchor,
        bridgePending = bridgePending,
        previousFusedSpeed = previousFusedSpeed,
        dopplerTrusted = dopplerTrusted,
        dopplerCheckAnchor = dopplerCheckAnchor,
        dopplerCheckStart = dopplerCheckStart,
        dopplerCheckDistance = dopplerCheckDistance,
        segment = segment,
        lastFixNanos = lastFixNanos,
        lastAccepted = lastAccepted,
        rejectsInARow = rejectsInARow,
        lastAccuracy = lastAccuracy,
        lastAccuracyNanos = lastAccuracyNanos,
        gaps = gaps.toList(),
        mockSeen = mockSeen,
        interrupted = interrupted,
        filter = filter.snapshot(),
        motion = motion.snapshot(),
        gpsElevation = gpsElevation.snapshot(),
        baroElevation = baroElevation.snapshot(),
        hasBarometer = hasBarometer,
        verticalAccuracyAvg = verticalAccuracyAvg,
        carriedGain = carriedGain,
        carriedLoss = carriedLoss,
        baroAltitude = baroAltitude,
        baroCalibrationSum = baroCalibrationSum,
        baroCalibrationCount = baroCalibrationCount,
        stepSpeed = stepSpeed,
        baroMin = baroMin,
        baroMax = baroMax,
        gpsAltMin = gpsAltMin,
        gpsAltMax = gpsAltMax,
        stillMovingNanos = stillMovingNanos,
        lastGpsAltitude = lastGpsAltitude,
        lastGpsAltitudeNanos = lastGpsAltitudeNanos,
        splits = splits.snapshot(),
        laps = laps.toList(),
        lapStartNanos = lapStartNanos,
        lapStartDistance = lapStartDistance,
        lapStartMovingNanos = lapStartMovingNanos,
        lapStartGain = lapStartGain,
        points = points.toList(),
        heartRate = heartRate.snapshot(),
        power = power.snapshot(),
        cadence = cadence.snapshot(),
        finishedActivity = finishedActivity,
    )

    private fun restoreFrom(s: EngineSnapshot) {
        status = s.status
        offsetNanos = s.offsetNanos
        clockNanos = s.clockNanos
        startNanos = s.startNanos
        finishNanos = s.finishNanos
        wallAnchorNanos = s.wallAnchorNanos
        wallAnchorMillis = s.wallAnchorMillis
        timerNanos = s.timerNanos
        movingNanos = s.movingNanos
        openPauseKind = s.openPauseKind
        openPauseStart = s.openPauseStart
        lastTimerStart = s.lastTimerStart
        maxSpeed = s.maxSpeedMps
        pauses += s.pauses
        autoPauseCount = s.autoPauseCount
        distance = s.distanceMeters
        anchor = s.anchor
        bridgePending = s.bridgePending
        previousFusedSpeed = s.previousFusedSpeed
        dopplerTrusted = s.dopplerTrusted
        dopplerCheckAnchor = s.dopplerCheckAnchor
        dopplerCheckStart = s.dopplerCheckStart
        dopplerCheckDistance = s.dopplerCheckDistance
        segment = s.segment
        lastFixNanos = s.lastFixNanos
        lastAccepted = s.lastAccepted
        rejectsInARow = s.rejectsInARow
        lastAccuracy = s.lastAccuracy
        lastAccuracyNanos = s.lastAccuracyNanos
        gaps += s.gaps
        mockSeen = s.mockSeen
        interrupted = s.interrupted
        filter.restore(s.filter)
        motion.restore(s.motion)
        gpsElevation.restore(s.gpsElevation)
        baroElevation.restore(s.baroElevation)
        hasBarometer = s.hasBarometer
        verticalAccuracyAvg = s.verticalAccuracyAvg
        carriedGain = s.carriedGain
        carriedLoss = s.carriedLoss
        baroAltitude = s.baroAltitude
        baroCalibrationSum = s.baroCalibrationSum
        baroCalibrationCount = s.baroCalibrationCount
        stepSpeed = s.stepSpeed
        baroMin = s.baroMin
        baroMax = s.baroMax
        gpsAltMin = s.gpsAltMin
        gpsAltMax = s.gpsAltMax
        stillMovingNanos = s.stillMovingNanos
        lastGpsAltitude = s.lastGpsAltitude
        lastGpsAltitudeNanos = s.lastGpsAltitudeNanos
        splits.restore(s.splits)
        laps += s.laps
        lapStartNanos = s.lapStartNanos
        lapStartDistance = s.lapStartDistance
        lapStartMovingNanos = s.lapStartMovingNanos
        lapStartGain = s.lapStartGain
        points += s.points
        heartRate.restore(s.heartRate)
        power.restore(s.power)
        cadence.restore(s.cadence)
        finishedActivity = s.finishedActivity
    }

    /**
     * Marks a gap in the recording caused by the process dying, from the last processed input to the
     * moment of resumption. The gap counts as elapsed time but not as moving time, adds no distance and
     * starts a new track segment. A monotonic clock that went backwards (the phone rebooted) is detected
     * and the gap length is taken from the wall clock instead; later inputs are shifted to continue the
     * timeline.
     */
    private fun applyInterruption(resumedAtMonotonicNanos: Long, resumedAtWallClockMillis: Long?) {
        val last = clockNanos ?: return
        val resumed = resumedAtMonotonicNanos + offsetNanos
        val gap = if (resumed >= last) {
            resumed - last
        } else {
            val lastWall = if (wallAnchorNanos != null) wallAt(last) else null
            val wallGapMillis = if (lastWall != null && resumedAtWallClockMillis != null) {
                (resumedAtWallClockMillis - lastWall).coerceAtLeast(0L)
            } else {
                0L
            }
            val g = wallGapMillis * 1_000_000L
            offsetNanos = last + g - resumedAtMonotonicNanos
            g
        }
        if (!status.isActive) {
            clockNanos = last + gap
            return
        }
        interrupted = true
        if (status == RecordingStatus.RECORDING && gap > 0) {
            pauses += PauseInterval(PauseKind.INTERRUPTION, last - startNanos, last + gap - startNanos)
        }
        clockNanos = last + gap
        lastFixNanos = clockNanos
        // The timer restarts at resumption: a later back-dated auto-pause must not reach into the gap.
        if (status == RecordingStatus.RECORDING) lastTimerStart = last + gap
        breakSegment()
        motion.reset()
        rebaseElevation()
    }

    public companion object {
        private const val MIN_ACCURACY = 1.0
        private const val UNKNOWN_ACCURACY = 20.0
        private const val STEP_ACCURACY_FACTOR = 0.5
        private const val MAX_REJECTS_IN_A_ROW = 5
        private const val BARO_CALIBRATION_FIXES = 30
        private const val GPS_HYSTERESIS_PER_VACC = 4.0
        private const val DOPPLER_CHECK_SECONDS = 30.0
        private const val DOPPLER_CHECK_RATIO = 1.5
        private const val DOPPLER_CHECK_SLACK_M = 10.0
        private const val STATIONARY_ESCAPE_M = 30.0
        private const val VACC_ALPHA = 0.05
        private const val STEP_SPEED_ALPHA = 0.5
        private const val STALE_FIX_NANOS = 10_000_000_000L
        private const val STUCK_BARO_RANGE_M = 0.3
        private const val ALTITUDE_STEP_BASE_M = 5.0
        private const val ALTITUDE_STEP_RATE_MPS = 1.0
        private const val STALE_ALTITUDE_FLAT_M = 0.5

        /**
         * Rebuilds an engine from [snapshot].
         *
         * Pass [interruption] when restoring after the recording process died, so the lost interval is
         * recorded as a gap (see [Interruption]); omit it for an in-process restore, which then continues
         * exactly as if the snapshot had never been taken.
         */
        public fun restore(snapshot: EngineSnapshot, interruption: Interruption? = null): RecordingEngine {
            val engine = RecordingEngine(snapshot.config)
            engine.restoreFrom(snapshot)
            interruption?.let { engine.applyInterruption(it.resumedAtMonotonicNanos, it.resumedAtWallClockMillis) }
            return engine
        }
    }
}

/**
 * When a recording resumed after its process died.
 *
 * @property resumedAtMonotonicNanos monotonic time at resumption (may be smaller than before after a reboot).
 * @property resumedAtWallClockMillis wall-clock time at resumption; used to size the gap after a reboot.
 */
public data class Interruption(val resumedAtMonotonicNanos: Long, val resumedAtWallClockMillis: Long?)

/** The last accepted raw fix, used for the plausibility check. */
public data class AcceptedFix(val latitude: Double, val longitude: Double, val nanos: Long, val accuracy: Double)
