// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

/*
 * The subset of the public FIT profile this library reads and writes. Numbers are protocol facts
 * (message numbers, field numbers, enum values); they are kept as Int constants so that values
 * newer than this table still round-trip.
 */

/** Global message numbers. */
public object FitMessageNumber {
    public const val FILE_ID: Int = 0
    public const val SESSION: Int = 18
    public const val LAP: Int = 19
    public const val RECORD: Int = 20
    public const val EVENT: Int = 21
    public const val DEVICE_INFO: Int = 23
    public const val ACTIVITY: Int = 34
    public const val FIELD_DESCRIPTION: Int = 206
    public const val DEVELOPER_DATA_ID: Int = 207
}

/** Field numbers shared by many messages. */
public object FitCommonField {
    public const val TIMESTAMP: Int = 253
    public const val MESSAGE_INDEX: Int = 254
}

/** `file_id` fields. */
public object FileIdField {
    public const val TYPE: Int = 0
    public const val MANUFACTURER: Int = 1
    public const val PRODUCT: Int = 2
    public const val SERIAL_NUMBER: Int = 3
    public const val TIME_CREATED: Int = 4
    public const val PRODUCT_NAME: Int = 8
}

/** `record` fields. */
public object RecordField {
    public const val POSITION_LAT: Int = 0
    public const val POSITION_LONG: Int = 1
    public const val ALTITUDE: Int = 2
    public const val HEART_RATE: Int = 3
    public const val CADENCE: Int = 4
    public const val DISTANCE: Int = 5
    public const val SPEED: Int = 6
    public const val POWER: Int = 7
    public const val TEMPERATURE: Int = 13
    public const val ENHANCED_SPEED: Int = 73
    public const val ENHANCED_ALTITUDE: Int = 78
}

/** `event` fields. */
public object EventField {
    public const val EVENT: Int = 0
    public const val EVENT_TYPE: Int = 1
    public const val DATA: Int = 3
    public const val EVENT_GROUP: Int = 4
}

/** `lap` fields. */
public object LapField {
    public const val EVENT: Int = 0
    public const val EVENT_TYPE: Int = 1
    public const val START_TIME: Int = 2
    public const val START_POSITION_LAT: Int = 3
    public const val START_POSITION_LONG: Int = 4
    public const val END_POSITION_LAT: Int = 5
    public const val END_POSITION_LONG: Int = 6
    public const val TOTAL_ELAPSED_TIME: Int = 7
    public const val TOTAL_TIMER_TIME: Int = 8
    public const val TOTAL_DISTANCE: Int = 9
    public const val TOTAL_CALORIES: Int = 11
    public const val AVG_SPEED: Int = 13
    public const val MAX_SPEED: Int = 14
    public const val AVG_HEART_RATE: Int = 15
    public const val MAX_HEART_RATE: Int = 16
    public const val AVG_CADENCE: Int = 17
    public const val MAX_CADENCE: Int = 18
    public const val AVG_POWER: Int = 19
    public const val MAX_POWER: Int = 20
    public const val TOTAL_ASCENT: Int = 21
    public const val TOTAL_DESCENT: Int = 22
    public const val INTENSITY: Int = 23
    public const val LAP_TRIGGER: Int = 24
    public const val SPORT: Int = 25
    public const val ENHANCED_AVG_SPEED: Int = 110
    public const val ENHANCED_MAX_SPEED: Int = 111
}

/** `session` fields. */
public object SessionField {
    public const val EVENT: Int = 0
    public const val EVENT_TYPE: Int = 1
    public const val START_TIME: Int = 2
    public const val START_POSITION_LAT: Int = 3
    public const val START_POSITION_LONG: Int = 4
    public const val SPORT: Int = 5
    public const val SUB_SPORT: Int = 6
    public const val TOTAL_ELAPSED_TIME: Int = 7
    public const val TOTAL_TIMER_TIME: Int = 8
    public const val TOTAL_DISTANCE: Int = 9
    public const val TOTAL_CALORIES: Int = 11
    public const val AVG_SPEED: Int = 14
    public const val MAX_SPEED: Int = 15
    public const val AVG_HEART_RATE: Int = 16
    public const val MAX_HEART_RATE: Int = 17
    public const val AVG_CADENCE: Int = 18
    public const val MAX_CADENCE: Int = 19
    public const val AVG_POWER: Int = 20
    public const val MAX_POWER: Int = 21
    public const val TOTAL_ASCENT: Int = 22
    public const val TOTAL_DESCENT: Int = 23
    public const val FIRST_LAP_INDEX: Int = 25
    public const val NUM_LAPS: Int = 26
    public const val ENHANCED_AVG_SPEED: Int = 124
    public const val ENHANCED_MAX_SPEED: Int = 125
}

/** `activity` fields. */
public object ActivityField {
    public const val TOTAL_TIMER_TIME: Int = 0
    public const val NUM_SESSIONS: Int = 1
    public const val TYPE: Int = 2
    public const val EVENT: Int = 3
    public const val EVENT_TYPE: Int = 4
    public const val LOCAL_TIMESTAMP: Int = 5
}

/** `file` enum (file_id.type). */
public object FitFileType {
    public const val ACTIVITY: Int = 4
    public const val COURSE: Int = 6
}

/** Selected `manufacturer` values. */
public object FitManufacturer {
    public const val GARMIN: Int = 1
    /** Reserved for development / independent software; the default for files we create. */
    public const val DEVELOPMENT: Int = 255
}

/** `event` enum. */
public object FitEventCode {
    public const val TIMER: Int = 0
    public const val SESSION: Int = 8
    public const val LAP: Int = 9
    public const val ACTIVITY: Int = 26
}

/** `event_type` enum. */
public object FitEventType {
    public const val START: Int = 0
    public const val STOP: Int = 1
    public const val MARKER: Int = 3
    public const val STOP_ALL: Int = 4
    public const val STOP_DISABLE_ALL: Int = 9
}

/** `sport` enum (common values). */
public object FitSport {
    public const val GENERIC: Int = 0
    public const val RUNNING: Int = 1
    public const val CYCLING: Int = 2
    public const val TRANSITION: Int = 3
    public const val FITNESS_EQUIPMENT: Int = 4
    public const val SWIMMING: Int = 5
    public const val TRAINING: Int = 10
    public const val WALKING: Int = 11
    public const val CROSS_COUNTRY_SKIING: Int = 12
    public const val ALPINE_SKIING: Int = 13
    public const val SNOWBOARDING: Int = 14
    public const val ROWING: Int = 15
    public const val MOUNTAINEERING: Int = 16
    public const val HIKING: Int = 17
    public const val MULTISPORT: Int = 18
    public const val PADDLING: Int = 19
    public const val E_BIKING: Int = 21
}

/** `sub_sport` enum (common values). */
public object FitSubSport {
    public const val GENERIC: Int = 0
    public const val TREADMILL: Int = 1
    public const val STREET: Int = 2
    public const val TRAIL: Int = 3
    public const val TRACK: Int = 4
    public const val SPIN: Int = 5
    public const val INDOOR_CYCLING: Int = 6
    public const val ROAD: Int = 7
    public const val MOUNTAIN: Int = 8
    public const val GRAVEL_CYCLING: Int = 46
    public const val LAP_SWIMMING: Int = 17
    public const val OPEN_WATER: Int = 18
    public const val VIRTUAL_ACTIVITY: Int = 58
}

/** `lap_trigger` enum. */
public object FitLapTrigger {
    public const val MANUAL: Int = 0
    public const val TIME: Int = 1
    public const val DISTANCE: Int = 2
    public const val POSITION_START: Int = 3
    public const val POSITION_LAP: Int = 4
    public const val SESSION_END: Int = 7
}

/** `intensity` enum. */
public object FitIntensity {
    public const val ACTIVE: Int = 0
    public const val REST: Int = 1
    public const val WARMUP: Int = 2
    public const val COOLDOWN: Int = 3
}

/** `activity` enum (activity.type). */
public object FitActivityType {
    public const val MANUAL: Int = 0
    public const val AUTO_MULTI_SPORT: Int = 1
}
