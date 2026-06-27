package com.personalagent.shared.util

/** Platform-provided wall-clock time in epoch millis (actuals per target). */
expect fun epochMillis(): Long

/** The real clock used by the running app. Tests use a fake [Clock] instead. */
val SystemClock: Clock = Clock { epochMillis() }
