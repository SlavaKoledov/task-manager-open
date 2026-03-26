package com.taskmanager.android.domain

import kotlin.math.roundToInt

data class SubtaskProgressSummary(
    val done: Int,
    val total: Int,
    val percent: Int?,
)

fun getSubtaskProgressSummary(done: Int, total: Int): SubtaskProgressSummary = SubtaskProgressSummary(
    done = done,
    total = total,
    percent = if (total == 0) null else ((done.toFloat() / total.toFloat()) * 100f).roundToInt(),
)
