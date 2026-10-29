// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.distribution

/**
 * Describes the build flavour (ADR-011). Each flavour binds its own instance in its
 * `DistributionModule`, so flavour-specific behaviour is chosen by DI rather than by `if` checks.
 *
 * @property id "gms" (Play) or "foss" (F-Droid).
 * @property locationBackend which location provider the recording stack will use.
 * @property pushBackend which push transport the notifications module will use.
 */
data class Distribution(
    val id: String,
    val displayName: String,
    val locationBackend: String,
    val pushBackend: String,
)
