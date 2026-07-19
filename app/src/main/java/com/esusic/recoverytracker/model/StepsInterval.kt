package com.esusic.recoverytracker.model

import java.time.Instant

data class StepsInterval(
    val start: Instant,
    val end: Instant,
    val count: Int
)
