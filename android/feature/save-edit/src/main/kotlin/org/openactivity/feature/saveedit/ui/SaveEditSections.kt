// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.activityextras.ActivityPhoto
import org.openactivity.core.activityextras.ui.PhotoThumbnail
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.saveedit.PerceivedExertion
import org.openactivity.feature.saveedit.SaveEditUiState
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import org.openactivity.model.Visibility
import kotlin.math.roundToInt

/** The form fields, title first (save-edit-activity S2). */
@Composable
internal fun ColumnScope.SaveEditForm(state: SaveEditUiState.Editing, actions: SaveEditActions) {
    val form = state.form
    Text(state.facts.summary, style = RidgelineTheme.text.statS, color = RidgelineTheme.colors.textSecondary)
    OutlinedTextField(
        value = form.title,
        onValueChange = actions.onTitle,
        label = { Text("Title") },
        placeholder = { Text(state.facts.defaultTitle) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        supportingText = if (form.title.isBlank()) ({ Text("Left empty, it saves as “${state.facts.defaultTitle}”") }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.description,
        onValueChange = actions.onDescription,
        label = { Text("Description") },
        placeholder = { Text("How did it go?") },
        minLines = 2,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    SettingRow(
        label = "Sport",
        value = DefaultTitles.sportName(form.sport),
        onClick = actions.onOpenSportPicker,
    )
    PhotosRow(state.photos, state.addingPhotos, actions.onAddPhotos, actions.onRemovePhoto)
    ExertionSection(form.perceivedExertion, actions.onExertion)
    AudienceSection(form.visibility, actions.onVisibility)
    PrivacyNote(state.facts.hideStartMeters, state.facts.hideEndMeters, state.facts.hasRoute)
    SwitchRow(
        title = "Mute from feeds",
        body = "Keeps it out of home and club feeds. It stays on your profile and still counts toward your totals and goals.",
        checked = form.muted,
        onChecked = actions.onMuted,
    )
    if (state.isNew) {
        HorizontalDivider(color = RidgelineTheme.colors.hairline)
        TextButton(onClick = actions.onDiscard, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Discard activity", color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.size(Spacing.l))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
    }
}

@Composable
private fun PhotosRow(photos: List<ActivityPhoto>, adding: Boolean, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Photos")
            Spacer(Modifier.weight(1f))
            Text("${photos.size}/${ActivityExtras.MAX_PHOTOS}", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (photos.size < ActivityExtras.MAX_PHOTOS) {
                item(key = "add") {
                    Box(
                        Modifier
                            .size(PHOTO_SIZE)
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, RidgelineTheme.colors.borderStrong, MaterialTheme.shapes.small)
                            .clickable(enabled = !adding, role = Role.Button, onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (adding) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Add photos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            items(photos, key = { it.id }) { photo ->
                Box(Modifier.size(PHOTO_SIZE)) {
                    PhotoThumbnail(
                        photo = photo,
                        contentDescription = "Photo",
                        targetSize = PHOTO_SIZE,
                        placeholder = RidgelineTheme.colors.skeleton,
                        modifier = Modifier.size(PHOTO_SIZE).clip(MaterialTheme.shapes.small),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .clickable(role = Role.Button) { onRemove(photo.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove photo", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Text(
            "Photos stay on this phone. Their location data is removed when they are added.",
            style = RidgelineTheme.text.caption,
            color = RidgelineTheme.colors.textMuted,
        )
    }
}

@Composable
private fun ExertionSection(rating: Int?, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("How did that feel?")
            Spacer(Modifier.weight(1f))
            if (rating != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
        if (rating == null) {
            TextButton(onClick = { onChange(PerceivedExertion.DEFAULT) }) { Text("Rate your effort") }
        } else {
            Text(
                "$rating · ${PerceivedExertion.band(rating)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(PerceivedExertion.cue(rating), style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
            Slider(
                value = rating.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = PerceivedExertion.MIN.toFloat()..PerceivedExertion.MAX.toFloat(),
                steps = PerceivedExertion.MAX - PerceivedExertion.MIN - 1,
                modifier = Modifier.semantics { stateDescription = "$rating of 10, ${PerceivedExertion.band(rating)}" },
            )
            Row {
                Text("Easy", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text("Max effort", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
            }
        }
    }
}

@Composable
private fun AudienceSection(visibility: Visibility, onChange: (Visibility) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle("Who can see this")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            listOf(
                Triple(Visibility.EVERYONE, "Everyone", Icons.Rounded.Public),
                Triple(Visibility.FOLLOWERS, "Followers", Icons.Rounded.Group),
                Triple(Visibility.ONLY_ME, "Only me", Icons.Rounded.Lock),
            ).forEach { (value, label, icon) ->
                RidgelineFilterChip(label = label, selected = visibility == value, onClick = { onChange(value) }, leadingIcon = icon)
            }
        }
        Text(
            when (visibility) {
                Visibility.EVERYONE -> "Anyone can see it once you sign in to a server and share."
                Visibility.FOLLOWERS -> "Only people who follow you, once you sign in to a server."
                Visibility.ONLY_ME -> "Private. Only you can see it, on this phone."
            },
            style = RidgelineTheme.text.caption,
            color = RidgelineTheme.colors.textMuted,
        )
    }
}

@Composable
private fun PrivacyNote(hideStart: Double, hideEnd: Double, hasRoute: Boolean) {
    if (!hasRoute) return
    val text = if (hideStart <= 0.0 && hideEnd <= 0.0) {
        "Your whole route is shown to people who can see this activity. You can hide the start and end in Settings › Privacy."
    } else {
        "The first ${hideStart.roundToInt()} m and last ${hideEnd.roundToInt()} m of your route are hidden from other people. You always see the full route."
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(RidgelineTheme.colors.surfaceSunken)
            .padding(Spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = RidgelineTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary, modifier = Modifier.padding(start = Spacing.m))
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChecked(!checked) }.padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Bottom sheet listing every sport, grouped by family, with the current one ticked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SportPickerSheet(current: SportType, onPick: (SportType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            SPORT_GROUPS.forEach { (title, sports) ->
                item(key = "h-$title") {
                    Text(
                        title.uppercase(),
                        style = RidgelineTheme.text.overline,
                        color = RidgelineTheme.colors.textMuted,
                        modifier = Modifier.padding(start = Spacing.gutter, top = Spacing.l, bottom = Spacing.xs),
                    )
                }
                items(sports, key = { it.name }) { sport ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) { onPick(sport) }
                            .padding(horizontal = Spacing.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(DefaultTitles.sportName(sport), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (sport == current) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private val SPORT_GROUPS: List<Pair<String, List<SportType>>> = listOf(
    "On foot" to SportCategory.FOOT,
    "Cycling" to SportCategory.BIKE,
    "Water" to SportCategory.WATER,
    "Snow" to SportCategory.SNOW,
    "Wheels" to SportCategory.WHEELS,
    "Fitness" to SportCategory.FITNESS,
    "Racket and ball" to SportCategory.RACKET_BALL,
    "Other" to SportCategory.OTHER,
).map { (title, category) -> title to SportType.entries.filter { it.category == category } }
    .filter { it.second.isNotEmpty() }

private val PHOTO_SIZE = 96.dp
