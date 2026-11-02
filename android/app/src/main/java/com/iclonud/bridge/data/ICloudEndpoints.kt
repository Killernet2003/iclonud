package com.iclonud.bridge.data

/** Puntos de entrada públicos y documentados de iCloud para clientes estándar. */
object ICloudEndpoints {
    // Correo
    const val IMAP_HOST = "imap.mail.me.com"
    const val IMAP_PORT = 993            // SSL/TLS
    const val SMTP_HOST = "smtp.mail.me.com"
    const val SMTP_PORT = 587            // STARTTLS

    // DAV: se usan como punto de arranque; el servidor redirige al nodo del usuario
    // (p. ej. https://pXX-caldav.icloud.com) tras el descubrimiento RFC 6764.
    const val CALDAV_BOOTSTRAP = "https://caldav.icloud.com"
    const val CARDDAV_BOOTSTRAP = "https://contacts.icloud.com"

    /** Cómo crear la contraseña específica de app (se enlaza desde la pantalla de acceso). */
    const val APP_PASSWORD_HELP = "https://support.apple.com/es-lamr/102654"
}
