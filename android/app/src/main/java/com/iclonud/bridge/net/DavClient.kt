package com.iclonud.bridge.net

import com.iclonud.bridge.data.ICloudEndpoints
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cliente DAV mínimo para iCloud sobre OkHttp.
 * Implementa el descubrimiento de colecciones (RFC 6764 / RFC 4791 / RFC 6352)
 * y las operaciones PROPFIND, REPORT y PUT con autenticación básica.
 *
 * `appPassword` debe ser una contraseña específica de app de Apple.
 */
class DavClient(appleId: String, appPassword: String) {

    private val auth = Credentials.basic(appleId, appPassword)
    private val xml = "application/xml; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class DavResponse(val href: String, val props: Map<String, String>, val resourceTypes: Set<String>)

    /** Representa una colección descubierta (un calendario o una libreta de direcciones). */
    data class Collection(val url: HttpUrl, val displayName: String)

    private fun propfind(url: HttpUrl, depth: String, body: String): List<DavResponse> {
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(xml))
            .header("Authorization", auth)
            .header("Depth", depth)
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code !in 200..299 && resp.code != 207)
                throw DavException("PROPFIND ${resp.code} en $url")
            return parseMultistatus(text)
        }
    }

    fun report(url: HttpUrl, body: String, depth: String = "1"): List<DavResponse> {
        val req = Request.Builder()
            .url(url)
            .method("REPORT", body.toRequestBody(xml))
            .header("Authorization", auth)
            .header("Depth", depth)
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code != 207 && resp.code !in 200..299)
                throw DavException("REPORT ${resp.code} en $url")
            return parseMultistatus(text)
        }
    }

    /** Crea o reemplaza un recurso (.ics o .vcf). `ifNoneMatch` = true para solo-crear. */
    fun put(url: HttpUrl, content: String, contentType: String, ifNoneMatch: Boolean) {
        val b = Request.Builder()
            .url(url)
            .put(content.toRequestBody(contentType.toMediaType()))
            .header("Authorization", auth)
        if (ifNoneMatch) b.header("If-None-Match", "*")
        http.newCall(b.build()).execute().use { resp ->
            if (resp.code !in 200..299)
                throw DavException("PUT ${resp.code} en $url")
        }
    }

    fun delete(url: HttpUrl) {
        val req = Request.Builder().url(url).delete().header("Authorization", auth).build()
        http.newCall(req).execute().use { resp ->
            if (resp.code !in 200..299 && resp.code != 404)
                throw DavException("DELETE ${resp.code} en $url")
        }
    }

    // --- Descubrimiento ---

    /** Prueba la ruta .well-known de RFC 6764 y, si falla, la raíz del servidor. */
    private fun currentUserPrincipal(host: String, wellKnown: String): HttpUrl {
        var ultimoError: DavException? = null
        for (bootstrap in listOf("$host/.well-known/$wellKnown", "$host/")) {
            try {
                return currentUserPrincipal(bootstrap)
            } catch (e: DavException) {
                ultimoError = e
            }
        }
        throw ultimoError ?: DavException("No se encontró el principal del usuario")
    }

    private fun currentUserPrincipal(bootstrap: String): HttpUrl {
        val body = """
            <d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>
        """.trimIndent()
        val res = propfind(bootstrap.toHttpUrl(), "0", body)
        val href = res.firstNotNullOfOrNull { it.props["current-user-principal"] }
            ?: throw DavException("No se encontró el principal del usuario")
        return resolve(bootstrap.toHttpUrl(), href)
    }

    private fun homeSet(principal: HttpUrl, homeProp: String): HttpUrl {
        val ns = if (homeProp == "calendar-home-set") "urn:ietf:params:xml:ns:caldav"
        else "urn:ietf:params:xml:ns:carddav"
        val body = """
            <d:propfind xmlns:d="DAV:" xmlns:x="$ns"><d:prop><x:$homeProp/></d:prop></d:propfind>
        """.trimIndent()
        val res = propfind(principal, "0", body)
        val href = res.firstNotNullOfOrNull { it.props[homeProp] }
            ?: throw DavException("No se encontró $homeProp")
        return resolve(principal, href)
    }

    /** Lista las colecciones hijas del home que son del tipo pedido. */
    private fun collectionsUnder(home: HttpUrl, wantType: String): List<Collection> {
        val body = """
            <d:propfind xmlns:d="DAV:"><d:prop>
              <d:resourcetype/><d:displayname/>
            </d:prop></d:propfind>
        """.trimIndent()
        return propfind(home, "1", body)
            .filter { wantType in it.resourceTypes }
            .map { Collection(resolve(home, it.href), it.props["displayname"].orEmpty()) }
    }

    /** Descubre todos los calendarios del usuario. */
    fun discoverCalendars(): List<Collection> {
        val principal = currentUserPrincipal(ICloudEndpoints.CALDAV_BOOTSTRAP, "caldav")
        val home = homeSet(principal, "calendar-home-set")
        return collectionsUnder(home, "calendar")
    }

    /** Descubre todas las libretas de direcciones del usuario. */
    fun discoverAddressBooks(): List<Collection> {
        val principal = currentUserPrincipal(ICloudEndpoints.CARDDAV_BOOTSTRAP, "carddav")
        val home = homeSet(principal, "addressbook-home-set")
        return collectionsUnder(home, "addressbook")
    }

    // --- Utilidades XML ---

    private fun resolve(base: HttpUrl, href: String): HttpUrl =
        base.resolve(href) ?: href.toHttpUrl()

    private fun parseMultistatus(text: String): List<DavResponse> {
        if (text.isBlank()) return emptyList()
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(text.byteInputStream())
        val out = ArrayList<DavResponse>()
        val responses = doc.getElementsByTagNameNS("DAV:", "response")
        for (i in 0 until responses.length) {
            val r = responses.item(i) as Element
            val href = firstText(r, "DAV:", "href") ?: continue
            val props = HashMap<String, String>()
            val types = HashSet<String>()
            // resourcetype: guardamos los nombres locales de sus hijos (calendar, addressbook, collection…)
            val rt = r.getElementsByTagNameNS("DAV:", "resourcetype")
            if (rt.length > 0) {
                val kids = (rt.item(0) as Element).childNodes
                for (k in 0 until kids.length) {
                    val n = kids.item(k)
                    if (n.nodeType == Node.ELEMENT_NODE) types.add(n.localName)
                }
            }
            collectProp(r, "current-user-principal", props)
            collectProp(r, "calendar-home-set", props)
            collectProp(r, "addressbook-home-set", props)
            firstText(r, "DAV:", "displayname")?.let { props["displayname"] = it }
            // datos crudos (calendar-data / address-data) para REPORT
            firstText(r, "urn:ietf:params:xml:ns:caldav", "calendar-data")?.let { props["calendar-data"] = it }
            firstText(r, "urn:ietf:params:xml:ns:carddav", "address-data")?.let { props["address-data"] = it }
            firstText(r, "DAV:", "getetag")?.let { props["getetag"] = it }
            out.add(DavResponse(href, props, types))
        }
        return out
    }

    /** Props que contienen un <href> interno (principal, home-set). */
    private fun collectProp(response: Element, local: String, into: MutableMap<String, String>) {
        val nodes = response.elementsByLocalName(local)
        if (nodes.isEmpty()) return
        val inner = (nodes[0]).getElementsByTagNameNS("DAV:", "href")
        if (inner.length > 0) into[local] = inner.item(0).textContent.trim()
    }

    private fun firstText(scope: Element, ns: String, local: String): String? {
        val n = scope.getElementsByTagNameNS(ns, local)
        return if (n.length > 0) n.item(0).textContent.trim() else null
    }

    private fun Element.elementsByLocalName(local: String): List<Element> {
        val all = getElementsByTagName("*")
        val res = ArrayList<Element>()
        for (i in 0 until all.length) {
            val e = all.item(i) as Element
            if (e.localName == local) res.add(e)
        }
        return res
    }
}

class DavException(message: String) : Exception(message)
