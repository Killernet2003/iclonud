package com.iclonud.bridge.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayTime = SimpleDateFormat("d MMM, HH:mm", Locale("es"))
private val dayOnly = SimpleDateFormat("EEE d MMM", Locale("es"))

fun fmtDateTime(millis: Long): String = if (millis <= 0) "" else dayTime.format(Date(millis))
fun fmtDay(millis: Long): String = if (millis <= 0) "" else dayOnly.format(Date(millis))
