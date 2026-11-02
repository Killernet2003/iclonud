package com.iclonud.bridge.net

import com.iclonud.bridge.data.ICloudEndpoints
import com.iclonud.bridge.model.MailMessage
import javax.mail.Address
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * Correo de iCloud por IMAP (lectura) y SMTP (envío), con contraseña específica de app.
 * El Apple ID puede ser el correo @icloud.com de la cuenta.
 */
class MailRepository(private val appleId: String, private val appPassword: String) {

    private fun imapSession(): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", ICloudEndpoints.IMAP_HOST)
            put("mail.imaps.port", ICloudEndpoints.IMAP_PORT.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.trust", ICloudEndpoints.IMAP_HOST)
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "45000")
        }
        return Session.getInstance(props)
    }

    private fun smtpSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.host", ICloudEndpoints.SMTP_HOST)
            put("mail.smtp.port", ICloudEndpoints.SMTP_PORT.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.ssl.trust", ICloudEndpoints.SMTP_HOST)
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "45000")
        }
        return Session.getInstance(props)
    }

    /** Prueba de credenciales: abre IMAP y cierra. Lanza excepción si falla. */
    suspend fun verify(): Unit = withContext(Dispatchers.IO) {
        val store = imapSession().getStore("imaps")
        store.connect(ICloudEndpoints.IMAP_HOST, appleId, appPassword)
        store.close()
    }

    suspend fun inbox(limit: Int = 30): List<MailMessage> = withContext(Dispatchers.IO) {
        val store = imapSession().getStore("imaps")
        store.connect(ICloudEndpoints.IMAP_HOST, appleId, appPassword)
        try {
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val count = folder.messageCount
            if (count == 0) return@withContext emptyList()
            val from = (count - limit + 1).coerceAtLeast(1)
            val msgs = folder.getMessages(from, count)
            val result = msgs.reversed().map { m ->
                MailMessage(
                    uid = m.messageNumber.toLong(),
                    from = addr(m.from),
                    subject = m.subject ?: "(sin asunto)",
                    dateMillis = (m.sentDate ?: m.receivedDate)?.time ?: 0L,
                    seen = m.isSet(Flags.Flag.SEEN),
                    preview = bodyPreview(m, 140),
                )
            }
            folder.close(false)
            result
        } finally {
            store.close()
        }
    }

    /** Cuerpo completo de un mensaje por su número. */
    suspend fun body(messageNumber: Int): String = withContext(Dispatchers.IO) {
        val store = imapSession().getStore("imaps")
        store.connect(ICloudEndpoints.IMAP_HOST, appleId, appPassword)
        try {
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val m = folder.getMessage(messageNumber)
            val text = extractText(m)
            folder.close(false)
            text
        } finally {
            store.close()
        }
    }

    suspend fun send(to: String, subject: String, bodyText: String): Unit = withContext(Dispatchers.IO) {
        val session = smtpSession()
        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(appleId))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            this.subject = subject
            setText(bodyText, "utf-8")
        }
        Transport.send(msg, appleId, appPassword)
    }

    // --- Utilidades ---

    private fun addr(a: Array<Address>?): String {
        val one = a?.firstOrNull() as? InternetAddress ?: return a?.firstOrNull()?.toString() ?: "(desconocido)"
        return one.personal ?: one.address
    }

    private fun bodyPreview(part: Part, max: Int): String =
        extractText(part).replace(Regex("\\s+"), " ").trim().take(max)

    /** Obtiene el texto plano de un mensaje, recorriendo multiparts. */
    private fun extractText(part: Part): String {
        try {
            if (part.isMimeType("text/plain")) return part.content?.toString().orEmpty()
            if (part.isMimeType("text/html")) return stripHtml(part.content?.toString().orEmpty())
            if (part.isMimeType("multipart/*")) {
                val mp = part.content as Multipart
                // preferimos text/plain; si no, el primero legible
                var html = ""
                for (i in 0 until mp.count) {
                    val bp = mp.getBodyPart(i)
                    if (bp.isMimeType("text/plain")) return bp.content.toString()
                    if (bp.isMimeType("multipart/*")) {
                        val t = extractText(bp)
                        if (t.isNotBlank()) return t
                    }
                    if (bp.isMimeType("text/html")) html = stripHtml(bp.content.toString())
                }
                return html
            }
        } catch (e: Exception) {
            return "(no se pudo leer el cuerpo: ${e.message})"
        }
        return ""
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("(?s)<style.*?</style>"), "")
            .replace(Regex("(?s)<script.*?</script>"), "")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("</p>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .trim()
}
