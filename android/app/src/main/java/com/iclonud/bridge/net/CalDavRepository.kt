package com.iclonud.bridge.net

import com.iclonud.bridge.model.CalEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID

/** Lectura y escritura de eventos de calendario en iCloud vía CalDAV. */
class CalDavRepository(private val dav: DavClient) {

    @Volatile private var calendar: HttpUrl? = null

    /** Elige el primer calendario escribible y lo cachea. */
    private fun ensureCalendar(): HttpUrl {
        calendar?.let { return it }
        val cals = dav.discoverCalendars()
        if (cals.isEmpty()) throw DavException("No hay calendarios en la cuenta")
        return cals.first().url.also { calendar = it }
    }

    suspend fun listEvents(fromMillis: Long, toMillis: Long): List<CalEvent> = withContext(Dispatchers.IO) {
        val cal = ensureCalendar()
        val start = IcalUtil.formatUtc(fromMillis)
        val end = IcalUtil.formatUtc(toMillis)
        val body = """
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/><c:calendar-data/></d:prop>
              <c:filter><c:comp-filter name="VCALENDAR">
                <c:comp-filter name="VEVENT">
                  <c:time-range start="$start" end="$end"/>
                </c:comp-filter>
              </c:comp-filter></c:filter>
            </c:calendar-query>
        """.trimIndent()
        dav.report(cal, body).mapNotNull { r ->
            val data = r.props["calendar-data"] ?: return@mapNotNull null
            parseEvent(r.href, data)
        }.sortedBy { it.startMillis }
    }

    suspend fun createEvent(summary: String, startMillis: Long, endMillis: Long, location: String) =
        withContext(Dispatchers.IO) {
            val cal = ensureCalendar()
            val uid = UUID.randomUUID().toString()
            val ics = IcalUtil.buildVEvent(uid, summary, startMillis, endMillis, location)
            val target = cal.resolve("$uid.ics")!!
            dav.put(target, ics, "text/calendar; charset=utf-8", ifNoneMatch = true)
        }

    suspend fun deleteEvent(href: String) = withContext(Dispatchers.IO) {
        dav.delete(absolute(href))
    }

    private fun absolute(href: String): HttpUrl {
        val base = calendar ?: ensureCalendar()
        return base.resolve(href) ?: href.toHttpUrl()
    }

    private fun parseEvent(href: String, data: String): CalEvent? {
        var uid = ""; var summary = "(sin título)"; var location = ""
        var start = 0L; var end = 0L; var allDay = false; var inEvent = false
        for (line in IcalUtil.unfold(data)) {
            val (name, value) = IcalUtil.splitLine(line)
            when {
                line == "BEGIN:VEVENT" -> inEvent = true
                line == "END:VEVENT" -> inEvent = false
                !inEvent -> {}
                IcalUtil.propName(name) == "UID" -> uid = value.trim()
                IcalUtil.propName(name) == "SUMMARY" -> summary = IcalUtil.unescapeText(value.trim())
                IcalUtil.propName(name) == "LOCATION" -> location = IcalUtil.unescapeText(value.trim())
                IcalUtil.propName(name) == "DTSTART" -> IcalUtil.parseDate(name, value).let { start = it.first; allDay = it.second }
                IcalUtil.propName(name) == "DTEND" -> end = IcalUtil.parseDate(name, value).first
            }
        }
        if (start == 0L) return null
        if (end == 0L) end = start + 3600_000
        return CalEvent(href, uid, summary, start, end, allDay, location)
    }
}
