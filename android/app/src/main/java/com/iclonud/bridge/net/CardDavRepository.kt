package com.iclonud.bridge.net

import com.iclonud.bridge.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID

/** Lectura y escritura de contactos en iCloud vía CardDAV. */
class CardDavRepository(private val dav: DavClient) {

    @Volatile private var book: HttpUrl? = null

    private fun ensureBook(): HttpUrl {
        book?.let { return it }
        val books = dav.discoverAddressBooks()
        if (books.isEmpty()) throw DavException("No hay libretas de direcciones en la cuenta")
        return books.first().url.also { book = it }
    }

    suspend fun listContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val b = ensureBook()
        val body = """
            <c:addressbook-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:carddav">
              <d:prop><d:getetag/><c:address-data/></d:prop>
            </c:addressbook-query>
        """.trimIndent()
        dav.report(b, body).mapNotNull { r ->
            val data = r.props["address-data"] ?: return@mapNotNull null
            parseContact(r.href, data)
        }.sortedBy { it.fullName.lowercase() }
    }

    suspend fun createContact(fullName: String, emails: List<String>, phones: List<String>) =
        withContext(Dispatchers.IO) {
            val b = ensureBook()
            val uid = UUID.randomUUID().toString()
            val vcf = IcalUtil.buildVCard(uid, fullName, emails, phones)
            val target = b.resolve("$uid.vcf")!!
            dav.put(target, vcf, "text/vcard; charset=utf-8", ifNoneMatch = true)
        }

    suspend fun deleteContact(href: String) = withContext(Dispatchers.IO) {
        val base = book ?: ensureBook()
        dav.delete(base.resolve(href) ?: href.toHttpUrl())
    }

    private fun parseContact(href: String, data: String): Contact? {
        var uid = ""; var fn = ""
        val emails = ArrayList<String>(); val phones = ArrayList<String>()
        for (line in IcalUtil.unfold(data)) {
            val (name, value) = IcalUtil.splitLine(line)
            when (IcalUtil.propName(name)) {
                "UID" -> uid = value.trim()
                "FN" -> fn = IcalUtil.unescapeText(value.trim())
                "EMAIL" -> if (value.isNotBlank()) emails.add(value.trim())
                "TEL" -> if (value.isNotBlank()) phones.add(value.trim())
            }
        }
        if (fn.isBlank() && emails.isEmpty() && phones.isEmpty()) return null
        return Contact(href, uid, fn.ifBlank { "(sin nombre)" }, emails, phones)
    }
}
