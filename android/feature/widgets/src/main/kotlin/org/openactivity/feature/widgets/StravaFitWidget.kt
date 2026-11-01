// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import org.openactivity.core.recordingservice.RecordingIntents
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem

/** Glance entry point; the launcher talks to this receiver. */
class StravaFitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StravaFitWidget()
}

/**
 * The StravaFit home-screen widget: one widget, three sizes (1×1, 4×1, 4×2), whose layout follows the
 * recording state. It is a remote control, not a copy of the app (widgets-spec.md).
 */
class StravaFitWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        entry.updater().start() // The process may have been started by the launcher, not the app.
        val repository = entry.repository()
        provideContent {
            val state by repository.state.collectAsState()
            WidgetContent(state)
        }
    }

    companion object {
        val SMALL = DpSize(57.dp, 57.dp)
        val WIDE = DpSize(250.dp, 57.dp)
        val TALL = DpSize(250.dp, 130.dp)
    }
}

// ---------------------------------------------------------------------------------------------------
// Tokens: core/designsystem theme/Color.kt. Orange only on the one primary action.

private object Palette {
    val surface = DayNight(day = Color(0xFFFFFFFF), night = Color(0xFF1C1C1F))
    val soft = DayNight(day = Color(0xFFF5F5F7), night = Color(0xFF2A2A2F))
    val text = DayNight(day = Color(0xFF242428), night = Color(0xFFF2F2F4))
    val text2 = DayNight(day = Color(0xFF45454E), night = Color(0xFFD2D2D8))
    val muted = DayNight(day = Color(0xFF6D6D78), night = Color(0xFFA3A3AD))
    val accent = DayNight(day = Color(0xFFFC5200), night = Color(0xFFFC5200))
    val accentText = DayNight(day = Color(0xFFCC3D00), night = Color(0xFFFF6A2B))
    val accentArea = DayNight(day = Color(0x1FFC5200), night = Color(0x29FC5200))
    val onAccent = DayNight(day = Color.White, night = Color.White)
    val recording = DayNight(day = Color(0xFFE5322D), night = Color(0xFFE5322D))
    val paused = DayNight(day = Color(0xFFC43B00), night = Color(0xFFC43B00))
    val ok = DayNight(day = Color(0xFF16884A), night = Color(0xFF3CC47A))

    val clockDay = Color(0xFF242428)
    val clockNight = Color(0xFFF2F2F4)
    val clockAccent = Color(0xFFFC5200)
}

private enum class Layout { SMALL, WIDE, TALL }

@Composable
private fun currentLayout(): Layout {
    val size = LocalSize.current
    return when {
        size.width < 180.dp -> Layout.SMALL
        size.height < 110.dp -> Layout.WIDE
        else -> Layout.TALL
    }
}

@Composable
internal fun WidgetContent(state: WidgetState) {
    val layout = currentLayout()
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(Palette.surface)
            .widgetCorners(),
    ) {
        when (state) {
            is WidgetState.Ready -> Ready(state, layout)
            is WidgetState.Countdown -> Countdown(state, layout)
            is WidgetState.Recording -> Recording(state, layout)
            is WidgetState.Done -> Done(state, layout)
            WidgetState.Interrupted -> Interrupted(layout)
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Ready

@Composable
private fun Ready(state: WidgetState.Ready, layout: Layout) {
    val context = LocalContext.current
    val main = state.sports.first()
    when (layout) {
        Layout.SMALL -> Column(
            modifier = GlanceModifier.fillMaxSize().clickable(startAction(context, main)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(sportIcon(main), sportLabel(context, main), 44, Palette.accent, Palette.onAccent)
            Spacer(GlanceModifier.height(6.dp))
            Label(sportLabel(context, main), Palette.text2)
        }
        Layout.WIDE -> Row(
            modifier = GlanceModifier.fillMaxSize().padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Mark()
                Spacer(GlanceModifier.height(4.dp))
                Text(context.getString(R.string.widget_ready_hint), style = body(12.sp, Palette.muted), maxLines = 1)
            }
            state.sports.forEachIndexed { i, sport ->
                if (i > 0) Spacer(GlanceModifier.width(12.dp))
                SportButton(sport, primary = i == 0)
            }
        }
        Layout.TALL -> Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Mark()
                Spacer(GlanceModifier.width(6.dp))
                Text(context.getString(R.string.widget_label), style = body(12.sp, Palette.text, FontWeight.Bold))
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(context.getString(R.string.widget_ready_hint), style = body(16.sp, Palette.text, FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = context.getString(R.string.widget_start_sport, sportLabel(context, main)),
                    icon = sportIcon(main),
                    action = startAction(context, main),
                    primary = true,
                    height = 48,
                    modifier = GlanceModifier.defaultWeight(),
                )
                state.sports.drop(1).forEach { sport ->
                    Spacer(GlanceModifier.width(8.dp))
                    Box(GlanceModifier.clickable(startAction(context, sport))) {
                        RoundIcon(sportIcon(sport), context.getString(R.string.widget_start_sport, sportLabel(context, sport)), 48, Palette.accentArea, Palette.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun SportButton(sport: SportType, primary: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.clickable(startAction(context, sport)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundIcon(
            sportIcon(sport),
            context.getString(R.string.widget_start_sport, sportLabel(context, sport)),
            40,
            if (primary) Palette.accent else Palette.accentArea,
            if (primary) Palette.onAccent else Palette.accent,
        )
        Spacer(GlanceModifier.height(4.dp))
        Label(sportLabel(context, sport), Palette.text2)
    }
}

// ---------------------------------------------------------------------------------------------------
// Countdown

@Composable
private fun Countdown(state: WidgetState.Countdown, layout: Layout) {
    val context = LocalContext.current
    val cancel = actionStartService(RecordingIntents.cancelStart(context), isForegroundService = true)
    val starting = context.getString(R.string.widget_starting, sportLabel(context, state.sport).lowercase())
    when (layout) {
        Layout.SMALL -> Column(
            modifier = GlanceModifier.fillMaxSize().clickable(cancel).semantics { contentDescription = starting },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock(state.endsAtElapsedRealtime, running = true, countDown = true, sizeSp = 28f, accent = true)
            Label(context.getString(R.string.widget_cancel), Palette.muted)
        }
        else -> Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock(state.endsAtElapsedRealtime, running = true, countDown = true, sizeSp = if (layout == Layout.TALL) 56f else 34f, accent = true)
            Spacer(GlanceModifier.width(14.dp))
            Text(starting, style = body(14.sp, Palette.text2, FontWeight.Bold), maxLines = 2, modifier = GlanceModifier.defaultWeight())
            Pill(context.getString(R.string.widget_cancel), R.drawable.ic_widget_close, cancel, primary = false, height = 40)
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Recording

@Composable
private fun Recording(state: WidgetState.Recording, layout: Layout) {
    val context = LocalContext.current
    val pauseOrResume = actionStartService(
        if (state.paused) RecordingIntents.resume(context) else RecordingIntents.pause(context),
        isForegroundService = true,
    )
    val pauseLabel = context.getString(if (state.paused) R.string.widget_resume_run else R.string.widget_pause_run)
    val pauseIcon = if (state.paused) R.drawable.ic_widget_play else R.drawable.ic_widget_pause
    val distance = WidgetFormat.distance(state.distanceMeters, state.units)
    val distanceUnit = context.getString(if (state.units == UnitSystem.METRIC) R.string.widget_unit_km else R.string.widget_unit_mi)
    val speed = WidgetFormat.speedValue(state.currentSpeedMps, state.sport, state.units)
    val speedUnit = speedUnit(context, state.sport, state.units)

    when (layout) {
        Layout.SMALL -> Column(
            modifier = GlanceModifier.fillMaxSize().clickable(pauseOrResume),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(pauseIcon, pauseLabel, 40, Palette.soft, Palette.text)
            Spacer(GlanceModifier.height(6.dp))
            Clock(state.clockBaseElapsedRealtime, running = !state.paused, sizeSp = 13f)
        }
        Layout.WIDE -> Row(
            modifier = GlanceModifier.fillMaxSize().padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                StatusLine(state)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Clock(state.clockBaseElapsedRealtime, running = !state.paused, sizeSp = 22f)
                    Spacer(GlanceModifier.width(12.dp))
                    Value(distance, distanceUnit, 22.sp)
                    Spacer(GlanceModifier.width(12.dp))
                    Value(speed, speedUnit, 22.sp)
                }
            }
            Box(GlanceModifier.clickable(pauseOrResume)) { RoundIcon(pauseIcon, pauseLabel, 40, Palette.soft, Palette.text) }
            Spacer(GlanceModifier.width(8.dp))
            FinishButton(state, compact = true)
        }
        Layout.TALL -> Column(GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(34.dp).padding(horizontal = 16.dp)
                    .background(if (state.paused) Palette.paused else Palette.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLine(state, onStrip = state.paused, modifier = GlanceModifier.defaultWeight())
                val avg = WidgetFormat.speedValue(state.averageSpeedMps, state.sport, state.units)
                Text(
                    context.getString(R.string.widget_avg, avg) + "  ·  " + context.getString(R.string.widget_lap, state.lapNumber),
                    style = body(11.sp, if (state.paused) Palette.onAccent else Palette.muted, FontWeight.Bold),
                    maxLines = 1,
                )
            }
            Row(GlanceModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(GlanceModifier.defaultWeight()) {
                    Caption(context.getString(R.string.widget_time))
                    Clock(state.clockBaseElapsedRealtime, running = !state.paused, sizeSp = 32f)
                }
                Column(GlanceModifier.defaultWeight()) {
                    Caption(context.getString(R.string.widget_distance))
                    Value(distance, distanceUnit, 22.sp)
                }
                Column(GlanceModifier.defaultWeight()) {
                    Caption(context.getString(if (state.sport.displaysPace) R.string.widget_pace else R.string.widget_speed))
                    Value(speed, speedUnit, 22.sp)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    context.getString(if (state.paused) R.string.widget_resume else R.string.widget_pause),
                    pauseIcon, pauseOrResume, primary = false, height = 44, modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                Pill(
                    context.getString(R.string.widget_lap_action), R.drawable.ic_widget_lap,
                    actionStartService(RecordingIntents.lap(context), isForegroundService = true), primary = false, height = 44,
                )
                Spacer(GlanceModifier.width(8.dp))
                FinishButton(state, compact = false, modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

/**
 * Finish needs two taps within [FINISH_CONFIRM_WINDOW]: widgets cannot detect a long press, and a single
 * stray tap must never end a run. The second tap goes straight to the service, which saves.
 */
@Composable
private fun FinishButton(state: WidgetState.Recording, compact: Boolean, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val action: Action =
        if (state.finishArmed) actionStartService(RecordingIntents.finish(context), isForegroundService = true)
        else actionRunCallback<ArmFinishAction>()
    val description = context.getString(if (state.finishArmed) R.string.widget_finish_run_confirm else R.string.widget_finish_run)
    if (compact) {
        Box(GlanceModifier.clickable(action).semantics { contentDescription = description }) {
            RoundIcon(
                if (state.finishArmed) R.drawable.ic_widget_check else R.drawable.ic_widget_stop,
                description, 40, Palette.accent, Palette.onAccent,
            )
        }
    } else {
        Pill(
            context.getString(if (state.finishArmed) R.string.widget_finish_confirm else R.string.widget_finish),
            if (state.finishArmed) R.drawable.ic_widget_check else R.drawable.ic_widget_stop,
            action, primary = true, height = 44, modifier = modifier, description = description,
        )
    }
}

@Composable
private fun StatusLine(state: WidgetState.Recording, onStrip: Boolean = false, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(if (state.paused) Palette.onAccent else Palette.recording)) {}
        Spacer(GlanceModifier.width(7.dp))
        Text(
            (context.getString(if (state.paused) R.string.widget_paused else R.string.widget_recording) + " · " + sportLabel(context, state.sport)).uppercase(),
            style = body(10.sp, if (onStrip) Palette.onAccent else Palette.text2, FontWeight.Bold),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------------
// Done and Interrupted

@Composable
private fun Done(state: WidgetState.Done, layout: Layout) {
    val context = LocalContext.current
    val a = state.activity
    // Phase 1 opens the activity; the one-tap share sheet (sticker) follows with the trampoline activity.
    val open = actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("openactivity://activity/${a.id}"))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    val dismiss = actionRunCallback<DismissDoneAction>(actionParametersOf(DismissDoneAction.ACTIVITY_ID to a.id))
    val distance = WidgetFormat.distance(a.distanceMeters, state.units)
    val unit = context.getString(if (state.units == UnitSystem.METRIC) R.string.widget_unit_km else R.string.widget_unit_mi)
    val time = WidgetFormat.clock(a.movingTime)
    when (layout) {
        Layout.SMALL -> Column(
            modifier = GlanceModifier.fillMaxSize().clickable(open),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(R.drawable.ic_widget_check, context.getString(R.string.widget_saved), 40, Palette.accentArea, Palette.accent)
            Spacer(GlanceModifier.height(6.dp))
            Text("$distance $unit", style = body(12.sp, Palette.text, FontWeight.Bold), maxLines = 1)
        }
        else -> Row(
            modifier = GlanceModifier.fillMaxSize().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_widget_check), null, GlanceModifier.size(13.dp), colorFilter = ColorFilter.tint(Palette.ok))
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        context.getString(R.string.widget_saved) + " · " + a.title,
                        style = body(11.sp, Palette.ok, FontWeight.Bold), maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(if (layout == Layout.TALL) 10.dp else 6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Value(distance, unit, if (layout == Layout.TALL) 28.sp else 20.sp)
                    Spacer(GlanceModifier.width(12.dp))
                    Value(time, "", if (layout == Layout.TALL) 28.sp else 20.sp)
                }
            }
            Pill(context.getString(R.string.widget_open), R.drawable.ic_widget_open, open, primary = true, height = 40)
            Box(GlanceModifier.size(40.dp).clickable(dismiss), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(R.drawable.ic_widget_close), context.getString(R.string.widget_dismiss),
                    GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(Palette.muted),
                )
            }
        }
    }
}

@Composable
private fun Interrupted(layout: Layout) {
    val context = LocalContext.current
    val recover = actionStartService(RecordingIntents.recover(context), isForegroundService = true)
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(recover).padding(horizontal = if (layout == Layout.SMALL) 6.dp else 16.dp),
        horizontalAlignment = if (layout == Layout.SMALL) Alignment.CenterHorizontally else Alignment.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(context.getString(R.string.widget_interrupted), style = body(if (layout == Layout.SMALL) 11.sp else 14.sp, Palette.text, FontWeight.Bold), maxLines = 2)
        Spacer(GlanceModifier.height(4.dp))
        Text(context.getString(R.string.widget_interrupted_action), style = body(12.sp, Palette.accentText, FontWeight.Bold), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------------
// Pieces

/**
 * The clock as a platform `Chronometer` inside the widget: Android ticks it, we only update on pause,
 * resume or a new countdown (widgets-spec.md, H1).
 */
@Composable
private fun Clock(base: Long, running: Boolean, sizeSp: Float, countDown: Boolean = false, accent: Boolean = false) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_clock).apply {
        setChronometer(R.id.widget_clock, base, null, running)
        setChronometerCountDown(R.id.widget_clock, countDown)
        setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        val day = (if (accent) Palette.clockAccent else Palette.clockDay).toArgb()
        val night = (if (accent) Palette.clockAccent else Palette.clockNight).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorInt(R.id.widget_clock, "setTextColor", day, night)
        } else {
            val isNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            setTextColor(R.id.widget_clock, if (isNight) night else day)
        }
    }
    AndroidRemoteViews(views)
}

@Composable
private fun Value(value: String, unit: String, size: TextUnit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = body(size, Palette.text, FontWeight.Bold), maxLines = 1)
        if (unit.isNotEmpty()) {
            Text(" $unit", style = body(11.sp, Palette.muted, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun Caption(text: String) =
    Text(text.uppercase(), style = body(10.sp, Palette.muted, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.padding(bottom = 4.dp))

@Composable
private fun Label(text: String, color: ColorProvider) = Text(text, style = body(11.sp, color, FontWeight.Bold), maxLines = 1)

@Composable
private fun Mark() = Image(ImageProvider(R.drawable.ic_widget_mark), null, GlanceModifier.size(16.dp))

@Composable
private fun RoundIcon(icon: Int, description: String?, sizeDp: Int, background: ColorProvider, tint: ColorProvider) {
    Box(
        modifier = GlanceModifier.size(sizeDp.dp).cornerRadius((sizeDp / 2).dp).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), description, GlanceModifier.size((sizeDp * 0.48f).dp), colorFilter = ColorFilter.tint(tint))
    }
}

@Composable
private fun Pill(
    text: String,
    icon: Int,
    action: Action,
    primary: Boolean,
    height: Int,
    modifier: GlanceModifier = GlanceModifier,
    description: String? = null,
) {
    val fg = if (primary) Palette.onAccent else Palette.text
    Row(
        modifier = modifier.height(height.dp).cornerRadius((height / 2).dp)
            .background(if (primary) Palette.accent else Palette.soft)
            .padding(horizontal = 16.dp)
            .clickable(action)
            .let { if (description != null) it.semantics { contentDescription = description } else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(ImageProvider(icon), null, GlanceModifier.size(15.dp), colorFilter = ColorFilter.tint(fg))
        Spacer(GlanceModifier.width(6.dp))
        Text(text, style = body(13.sp, fg, FontWeight.Bold), maxLines = 1)
    }
}

private fun body(size: TextUnit, color: ColorProvider, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontSize = size, color = color, fontWeight = weight)

private fun startAction(context: Context, sport: SportType): Action =
    actionStartService(RecordingIntents.start(context, sport), isForegroundService = true)

private fun sportIcon(sport: SportType): Int = when {
    sport.isRideLike -> R.drawable.ic_widget_ride
    sport == SportType.WALK || sport == SportType.HIKE -> R.drawable.ic_widget_walk
    else -> R.drawable.ic_widget_run
}

/** The launcher's own widget corner radius on Android 12+ (Glance ignores corner radius below that). */
private fun GlanceModifier.widgetCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(android.R.dimen.system_app_widget_background_radius)
    else cornerRadius(16.dp)

private fun sportLabel(context: Context, sport: SportType): String = when (sport) {
    SportType.RUN -> context.getString(R.string.widget_sport_run)
    SportType.RIDE -> context.getString(R.string.widget_sport_ride)
    SportType.WALK -> context.getString(R.string.widget_sport_walk)
    else -> sport.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun speedUnit(context: Context, sport: SportType, units: UnitSystem): String = context.getString(
    when {
        sport.displaysPace && units == UnitSystem.METRIC -> R.string.widget_unit_per_km
        sport.displaysPace -> R.string.widget_unit_per_mi
        units == UnitSystem.METRIC -> R.string.widget_unit_kmh
        else -> R.string.widget_unit_mph
    },
)

// ---------------------------------------------------------------------------------------------------
// Callbacks

/** First Finish tap: arm the confirm window. The repository disarms it when the window closes. */
class ArmFinishAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).repository().armFinish()
    }
}

/** ✕ on the Done state: back to Ready, and this activity is not offered again. */
class DismissDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ACTIVITY_ID] ?: return
        EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).repository().dismissDone(id)
    }

    companion object {
        val ACTIVITY_ID = ActionParameters.Key<Long>("activityId")
    }
}
