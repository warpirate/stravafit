// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.data

import javax.inject.Qualifier

/** The Record feature's own DataStore, so it never collides with other modules' bindings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecordDataStore
