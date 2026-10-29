// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

/**
 * Who can see an activity.
 *
 * @property apiName stable identifier used in storage and the network API (matches the database enum
 *   `activity_visibility`).
 */
public enum class Visibility(public val apiName: String) {
    /** Anyone, including signed-out viewers of public pages. */
    EVERYONE("everyone"),

    /** Approved followers only. */
    FOLLOWERS("followers"),

    /** Only the owner. */
    ONLY_ME("only_me"),
    ;

    public companion object {
        /** The privacy-first default for a new activity. */
        public val DEFAULT: Visibility = FOLLOWERS

        /**
         * Looks up a value by [apiName]; null for unknown names. Also accepts `followers_only`, the spelling
         * used by third-party activity APIs, so imported data maps onto [FOLLOWERS].
         */
        public fun fromApiName(name: String): Visibility? =
            if (name == LEGACY_FOLLOWERS) FOLLOWERS else entries.firstOrNull { it.apiName == name }

        private const val LEGACY_FOLLOWERS = "followers_only"
    }
}
