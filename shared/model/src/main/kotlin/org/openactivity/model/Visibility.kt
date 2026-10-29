// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

/**
 * Who can see an activity.
 *
 * @property apiName stable identifier used in storage and the network API.
 */
public enum class Visibility(public val apiName: String) {
    /** Anyone, including signed-out viewers of public pages. */
    EVERYONE("everyone"),

    /** Approved followers only. */
    FOLLOWERS("followers_only"),

    /** Only the owner. */
    ONLY_ME("only_me"),
    ;

    public companion object {
        /** The privacy-first default for a new activity. */
        public val DEFAULT: Visibility = FOLLOWERS

        /** Looks up a value by [apiName]; null for unknown names. */
        public fun fromApiName(name: String): Visibility? = entries.firstOrNull { it.apiName == name }
    }
}
