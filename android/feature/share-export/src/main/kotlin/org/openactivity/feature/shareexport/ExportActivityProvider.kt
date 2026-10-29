// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport

import org.openactivity.core.export.art.ArtUnits
import org.openactivity.core.export.model.ExportActivity

/**
 * Supplies activities to the share screen. The app implements it on top of its own storage, mapping a
 * stored activity (samples, stats and the owner's privacy settings) into an [ExportActivity].
 */
public interface ExportActivityProvider {
    /** Loads the activity with [activityId], or returns `null` when it does not exist. */
    public suspend fun load(activityId: String): ExportActivity?

    /** Units for the stats printed on images; defaults to metric. */
    public suspend fun units(): ArtUnits = ArtUnits.METRIC
}
