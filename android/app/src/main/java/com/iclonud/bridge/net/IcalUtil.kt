package com.iclonud.bridge.net

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Parseo y construcción mínimos de VEVENT/VCARD, suficientes para esta app. */
object IcalUtil {

    private fun utc() = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private fun local() = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
    private fun dateOnly() = SimpleDateFormat("yyyyMMdd", Locale.US)

    /** Desdobla líneas plegadas (RFC 5545: una línea que empieza con espacio continúa la anterior). */
    fun unfold(raw: String): List<String> {
        val out = ArrayList<String>()
        for (line in raw.replace("\r\n", "\n").split("\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.substring(1)
            } else out.add(line)
        }
        return out
    }

    /** Devuelve (nombreConParámetros, valor) de una línea "PROP;PARAM=x:valor". */
    fun splitLine(line: String): Pair<String, String> {
        val i = line.indexOf(':')
        if (i < 0) return line to ""
        return line.substring(0, i) to line.substring(i + 1)
    }

    fun propName(nameWithParams: String): String =
        nameWithParams.substringBefore(';').uppercase()

    /** Convierte un valor DTSTART/DTEND a epoch millis. Soporta UTC, local flotante y fecha. */
    fun parseDate(nameWithParams: String, value: String): Pair<Long, Boolean> {
        val v = value.trim()
        return try {
            when {
                nameWithParams.uppercase().contains("VALUE=DATE") || v.length == 8 ->
                    dateOnly().parse(v)!!.time to true
                v.endsWith("Z") -> utc().parse(v)!!.time to false
                else -> local().parse(v)!!.time to false
            }
        } catch (e: Exception) {
            Date().time to false
        }
    }

    fun formatUtc(millis: Long): String = utc().format(Date(millis))

    /** Construye un VCALENDAR con un único VEVENT. */
    fun buildVEvent(uid: String, summary: String, startMillis: Long, endMillis: Long, location: String): String {
        val dtstamp = formatUtc(System.currentTimeMillis())
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//iClonud//ES\r\n")
        sb.append("BEGIN:VEVENT\r\n")
        sb.append("UID:").append(uid).append("\r\n")
        sb.append("DTSTAMP:").append(dtstamp).append("\r\n")
        sb.append("DTSTART:").append(formatUtc(startMillis)).append("\r\n")
        sb.append("DTEND:").append(formatUtc(endMillis)).append("\r\n")
        sb.append("SUMMARY:").append(escapeText(summary)).append("\r\n")
        if (location.isNotBlank()) sb.append("LOCATION:").append(escapeText(location)).append("\r\n")
        sb.append("END:VEVENT\r\n")
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    /** Construye un VCARD 3.0 sencillo. */
    fun buildVCard(uid: String, fullName: String, emails: List<String>, phones: List<String>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\n")
        sb.append("VERSION:3.0\r\n")
        sb.append("UID:").append(uid).append("\r\n")
        sb.append("FN:").append(escapeText(fullName)).append("\r\n")
        sb.append("N:").append(escapeText(fullName)).append(";;;;\r\n")
        emails.filter { it.isNotBlank() }.forEach { sb.append("EMAIL;TYPE=INTERNET:").append(it).append("\r\n") }
        phones.filter { it.isNotBlank() }.forEach { sb.append("TEL;TYPE=CELL:").append(it).append("\r\n") }
        sb.append("END:VCARD\r\n")
        return sb.toString()
    }

    private fun escapeText(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    fun unescapeText(s: String): String =
        s.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
