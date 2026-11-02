package com.iclonud.bridge.model

/** Un correo tal como lo mostramos en la lista o en el detalle. */
data class MailMessage(
    val uid: Long,
    val from: String,
    val subject: String,
    val dateMillis: Long,
    val seen: Boolean,
    val preview: String = "",
    val body: String = "",
)

/** Un evento de calendario (CalDAV). `href` es la ruta del recurso .ics en el servidor. */
data class CalEvent(
    val href: String,
    val uid: String,
    val summary: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String = "",
)

/** Un contacto (CardDAV). `href` es la ruta del recurso .vcf en el servidor. */
data class Contact(
    val href: String,
    val uid: String,
    val fullName: String,
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
)

/** Resultado genérico de una operación de red para la capa de UI. */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val message: String) : Outcome<Nothing>
}
