// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.time.LocalDate
import java.time.ZoneId

// ---------------------------------------------------------------------------------------------------
// This week

/** Receiver of the "This week" widget. */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

/**
 * "This week": distance, moving time and activity count of the current week, against the weekly goal when
 * one is set. Tapping it opens the You tab. Same sizes as the recording widget (1×1, 4×1, 4×2).
 */
class WeekWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(StravaFitWidget.SMALL, StravaFitWidget.WIDE, StravaFitWidget.TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        entry.updater().start()
        val summaries = entry.summaries()
        provideContent {
            // A fresh flow per session: "this week" is re-evaluated whenever the widget is drawn again.
            val week by remember { summaries.week() }.collectAsState(null)
            WeekContent(week)
        }
    }
}

@Composable
internal fun WeekContent(week: WeekSummary?) {
    val context = LocalContext.current
    val layout = currentLayout()
    val open = actionStartActivity(WidgetLinks.intent(context, WidgetLinks.YOU))
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(Palette.surface).widgetCorners()
            .clickable(open),
    ) {
        if (week == null) return@Box
        val units = week.units
        val distance = WidgetFormat.distance(week.distanceMeters, units)
        val unit = context.getString(if (units == UnitSystem.METRIC) R.string.widget_unit_km else R.string.widget_unit_mi)
        val time = WidgetFormat.duration(week.movingTime)
        val count = context.resources.getQuantityString(R.plurals.widget_activities, week.count, week.count)
        val goal = week.goal
        val goalText = goal?.let { goalLine(context, it, units) }
        when (layout) {
            Layout.SMALL -> Column(
                modifier = GlanceModifier.fillMaxSize().padding(8.dp)
                    .semantics { contentDescription = context.getString(R.string.widget_week_spoken, "$distance $unit", time, count) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(distance, style = body(20.sp, Palette.text, FontWeight.Bold), maxLines = 1)
                Text(unit, style = body(11.sp, Palette.muted, FontWeight.Bold), maxLines = 1)
                if (goal != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    GoalBar(goal, GlanceModifier.fillMaxWidth().height(4.dp))
                }
            }
            Layout.WIDE -> Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(GlanceModifier.defaultWeight()) {
                    WeekHeader(week.weekStart)
                    Spacer(GlanceModifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Value(distance, unit, 20.sp)
                        Spacer(GlanceModifier.width(14.dp))
                        Value(time, "", 20.sp)
                        Spacer(GlanceModifier.width(14.dp))
                        Value(week.count.toString(), context.resources.getQuantityString(R.plurals.widget_activities_unit, week.count), 20.sp)
                    }
                    if (goal != null) {
                        Spacer(GlanceModifier.height(6.dp))
                        GoalBar(goal, GlanceModifier.fillMaxWidth().height(4.dp))
                    }
                }
            }
            Layout.TALL -> Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                WeekHeader(week.weekStart)
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(GlanceModifier.defaultWeight()) {
                        Caption(context.getString(R.string.widget_distance))
                        Value(distance, unit, 24.sp)
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        Caption(context.getString(R.string.widget_time))
                        Value(time, "", 24.sp)
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        Caption(context.getString(R.string.widget_activities_caption))
                        Value(week.count.toString(), "", 24.sp)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (goal != null && goalText != null) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(goalText.first, style = body(12.sp, Palette.text2, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(goalText.second, style = body(12.sp, if (goal.reached) Palette.ok else Palette.muted, FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    GoalBar(goal, GlanceModifier.fillMaxWidth().height(6.dp))
                } else {
                    Text(context.getString(R.string.widget_week_no_goal), style = body(12.sp, Palette.muted), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(weekStart: LocalDate) {
    val context = LocalContext.current
    val range = DateUtils.formatDateRange(
        context,
        weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        weekStart.plusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mark()
        Spacer(GlanceModifier.width(6.dp))
        Text(context.getString(R.string.widget_this_week), style = body(12.sp, Palette.text, FontWeight.Bold), maxLines = 1)
        Text("  ·  $range", style = body(12.sp, Palette.muted), maxLines = 1)
    }
}

@Composable
private fun GoalBar(goal: WeekGoal, modifier: GlanceModifier) {
    LinearProgressIndicator(
        progress = goal.fraction,
        modifier = modifier.cornerRadius(3.dp),
        color = if (goal.reached) Palette.ok else Palette.accent,
        backgroundColor = Palette.soft,
    )
}

/** ("Run goal", "12.4 / 30 km") for the goal row; "Reached" replaces the numbers' tail once beaten. */
internal fun goalLine(context: Context, goal: WeekGoal, units: UnitSystem): Pair<String, String> {
    val name = when {
        goal.sport == null -> context.getString(R.string.widget_goal_all)
        else -> context.getString(R.string.widget_goal_sport, sportLabel(context, goal.sport))
    }
    val numbers = WidgetFormat.goalProgress(goal.metric, goal.current, goal.target, units)
    val unit = when (goal.metric) {
        GoalMetric.DISTANCE -> context.getString(if (units == UnitSystem.METRIC) R.string.widget_unit_km else R.string.widget_unit_mi)
        GoalMetric.ELEVATION -> context.getString(if (units == UnitSystem.METRIC) R.string.widget_unit_m else R.string.widget_unit_ft)
        GoalMetric.TIME -> ""
        GoalMetric.COUNT -> context.resources.getQuantityString(R.plurals.widget_activities_unit, goal.target.toInt())
    }
    val tail = listOf(numbers, unit).filter { it.isNotEmpty() }.joinToString(" ")
    return name to if (goal.reached) context.getString(R.string.widget_goal_reached, tail) else tail
}

// ---------------------------------------------------------------------------------------------------
// Last activity

/** Receiver of the "Last activity" widget. */
class LastActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastActivityWidget()
}

/**
 * "Last activity": title, sport, key stats and a small drawing of the route (start and end hidden as the
 * owner set them). Tapping it opens the activity.
 */
class LastActivityWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(StravaFitWidget.SMALL, StravaFitWidget.WIDE, StravaFitWidget.TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        entry.updater().start()
        val summaries = entry.summaries()
        val density = context.resources.displayMetrics.density
        provideContent {
            val view by remember { withThumbnail(summaries.last(), density) }.collectAsState(LastActivityView.Loading)
            LastActivityContent(view)
        }
    }

    private fun withThumbnail(cards: Flow<LastActivityCard?>, density: Float): Flow<LastActivityView> = cards
        .map { card -> LastActivityView.Shown(card, card?.let { WidgetRouteArt.render(it.route, (THUMB_DP * density).toInt()) }) }
        .flowOn(Dispatchers.Default)

    private companion object {
        const val THUMB_DP = 96
    }
}

/** The "Last activity" widget while its data loads, then with the activity (or none) and its route drawing. */
internal sealed interface LastActivityView {
    data object Loading : LastActivityView

    data class Shown(val card: LastActivityCard?, val thumbnail: Bitmap?) : LastActivityView
}

@Composable
private fun LastActivityContent(view: LastActivityView) {
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().background(Palette.surface).widgetCorners()) {
        if (view is LastActivityView.Shown) LastActivityBody(view.card, view.thumbnail)
    }
}

@Composable
internal fun LastActivityBody(card: LastActivityCard?, thumbnail: Bitmap?) {
    val context = LocalContext.current
    val layout = currentLayout()
    if (card == null) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp)
                .clickable(actionStartActivity(WidgetLinks.intent(context, WidgetLinks.YOU))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mark()
            Spacer(GlanceModifier.height(6.dp))
            Text(context.getString(R.string.widget_no_activity), style = body(12.sp, Palette.muted, FontWeight.Bold), maxLines = 2)
        }
        return
    }
    val open = actionStartActivity(WidgetLinks.intent(context, WidgetLinks.activity(card.id)))
    val units = card.units
    val distance = WidgetFormat.distance(card.distanceMeters, units)
    val unit = context.getString(if (units == UnitSystem.METRIC) R.string.widget_unit_km else R.string.widget_unit_mi)
    val time = WidgetFormat.duration(card.movingTime)
    val speed = WidgetFormat.speedValue(card.averageSpeedMps, card.sport, units)
    val speedUnit = speedUnit(context, card.sport, units)
    val day = dayLabel(
        card.startTime, card.zone, LocalDate.now(card.zone),
        weekdayAndDate = { d ->
            DateUtils.formatDateTime(
                context, d.atStartOfDay(card.zone).toInstant().toEpochMilli(),
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
            )
        },
        todayWord = context.getString(R.string.widget_today),
        yesterdayWord = context.getString(R.string.widget_yesterday),
    )
    val kicker = "$day · ${sportLabel(context, card.sport)}"
    when (layout) {
        Layout.SMALL -> Column(
            modifier = GlanceModifier.fillMaxSize().padding(6.dp).clickable(open)
                .semantics { contentDescription = "${card.title}, $distance $unit, $time" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(thumbnail, card.sport, 40)
            Spacer(GlanceModifier.height(4.dp))
            Text("$distance $unit", style = body(12.sp, Palette.text, FontWeight.Bold), maxLines = 1)
        }
        Layout.WIDE -> Row(
            modifier = GlanceModifier.fillMaxSize().padding(start = 10.dp, end = 16.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(thumbnail, card.sport, 60)
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(kicker.uppercase(), style = body(10.sp, Palette.muted, FontWeight.Bold), maxLines = 1)
                Text(card.title, style = body(14.sp, Palette.text, FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Value(distance, unit, 16.sp)
                    Spacer(GlanceModifier.width(10.dp))
                    Value(time, "", 16.sp)
                    Spacer(GlanceModifier.width(10.dp))
                    Value(speed, speedUnit, 16.sp)
                }
            }
        }
        Layout.TALL -> Row(
            modifier = GlanceModifier.fillMaxSize().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(kicker.uppercase(), style = body(10.sp, Palette.muted, FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(2.dp))
                Text(card.title, style = body(16.sp, Palette.text, FontWeight.Bold), maxLines = 2)
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.Bottom) {
                    Column {
                        Caption(context.getString(R.string.widget_distance))
                        Value(distance, unit, 20.sp)
                    }
                    Spacer(GlanceModifier.width(14.dp))
                    Column {
                        Caption(context.getString(R.string.widget_time))
                        Value(time, "", 20.sp)
                    }
                    Spacer(GlanceModifier.width(14.dp))
                    Column {
                        Caption(context.getString(if (card.sport.displaysPace) R.string.widget_pace else R.string.widget_speed))
                        Value(speed, speedUnit, 20.sp)
                    }
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            Thumbnail(thumbnail, card.sport, 96)
        }
    }
}

/** The route drawing, or the sport icon for activities without GPS. */
@Composable
private fun Thumbnail(bitmap: Bitmap?, sport: SportType, sizeDp: Int) {
    Box(
        GlanceModifier.size(sizeDp.dp).cornerRadius(12.dp).background(Palette.soft),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(ImageProvider(bitmap), null, GlanceModifier.size(sizeDp.dp))
        } else {
            Image(ImageProvider(sportIcon(sport)), null, GlanceModifier.size((sizeDp * 0.42f).dp), colorFilter = ColorFilter.tint(Palette.accent))
        }
    }
}
