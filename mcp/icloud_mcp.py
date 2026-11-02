# /// script
# requires-python = ">=3.10"
# dependencies = ["mcp>=1.2,<2", "httpx>=0.27", "icalendar>=5"]
# ///
"""
Servidor MCP de iCloud (correo, calendario y contactos) para Claude Code.

Usa solo protocolos abiertos y documentados por Apple:
  - Correo: IMAP (imap.mail.me.com:993) y SMTP (smtp.mail.me.com:587, STARTTLS)
  - Calendario: CalDAV (caldav.icloud.com)
  - Contactos: CardDAV (contacts.icloud.com)

Credenciales: Apple ID + contraseña específica de app (appleid.apple.com),
leídas de ~/.config/iclonud-mcp/credenciales.env (permisos 600). Nunca se
devuelven en las respuestas de las herramientas.
"""
from __future__ import annotations

import email
import email.header
import email.utils
import imaplib
import os
import re
import smtplib
import ssl
import time
import uuid
import xml.etree.ElementTree as ET
from datetime import date, datetime, timedelta
from email.message import EmailMessage
from pathlib import Path
from urllib.parse import urljoin
from zoneinfo import ZoneInfo

import httpx
from icalendar import Calendar, Event
from mcp.server.fastmcp import FastMCP

IMAP_HOST, IMAP_PORT = "imap.mail.me.com", 993
SMTP_HOST, SMTP_PORT = "smtp.mail.me.com", 587
CALDAV_BOOTSTRAP = "https://caldav.icloud.com"
CARDDAV_BOOTSTRAP = "https://contacts.icloud.com"
ZONA = ZoneInfo(os.environ.get("ICLOUD_ZONA", "America/Bogota"))
ARCHIVO_CRED = Path(
    os.environ.get("ICLOUD_CREDENCIALES", "~/.config/iclonud-mcp/credenciales.env")
).expanduser()
MAX_CUERPO = 20000

NS = {"d": "DAV:", "c": "urn:ietf:params:xml:ns:caldav", "a": "urn:ietf:params:xml:ns:carddav"}

mcp = FastMCP("icloud")


# --- Credenciales ---

def _credenciales() -> tuple[str, str]:
    valores = {}
    if ARCHIVO_CRED.exists():
        for linea in ARCHIVO_CRED.read_text(encoding="utf-8").splitlines():
            linea = linea.strip()
            if linea and not linea.startswith("#") and "=" in linea:
                k, v = linea.split("=", 1)
                valores[k.strip()] = v.strip().strip('"').strip("'")
    apple_id = os.environ.get("ICLOUD_APPLE_ID") or valores.get("ICLOUD_APPLE_ID", "")
    clave = os.environ.get("ICLOUD_APP_PASSWORD") or valores.get("ICLOUD_APP_PASSWORD", "")
    if not apple_id or not clave:
        raise RuntimeError(
            f"Faltan credenciales de iCloud. Escribe ICLOUD_APPLE_ID e ICLOUD_APP_PASSWORD "
            f"(contraseña específica de app) en {ARCHIVO_CRED}."
        )
    return apple_id, clave


# --- Correo (IMAP/SMTP) ---

def _imap() -> imaplib.IMAP4_SSL:
    apple_id, clave = _credenciales()
    m = imaplib.IMAP4_SSL(IMAP_HOST, IMAP_PORT, ssl_context=ssl.create_default_context(), timeout=45)
    m.login(apple_id, clave)
    return m


def _buzon(nombre: str) -> str:
    """Pone comillas al nombre del buzón si lleva espacios (p. ej. "Sent Messages")."""
    return f'"{nombre}"' if " " in nombre and not nombre.startswith('"') else nombre


def _decodificar(valor: str | None) -> str:
    if not valor:
        return ""
    partes = []
    for texto, cod in email.header.decode_header(valor):
        if isinstance(texto, bytes):
            partes.append(texto.decode(cod or "utf-8", errors="replace"))
        else:
            partes.append(texto)
    return "".join(partes).strip()


def _fecha_imap(iso: str) -> str:
    d = date.fromisoformat(iso)
    meses = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
    return f"{d.day:02d}-{meses[d.month - 1]}-{d.year}"


def _cuerpo_texto(msg: email.message.Message) -> tuple[str, list[str]]:
    """Devuelve el texto plano del mensaje (o el HTML sin etiquetas) y los nombres de adjuntos."""
    texto, html, adjuntos = None, None, []
    for parte in msg.walk() if msg.is_multipart() else [msg]:
        if parte.get_content_maintype() == "multipart":
            continue
        nombre = parte.get_filename()
        if nombre or parte.get_content_disposition() == "attachment":
            adjuntos.append(_decodificar(nombre) or "(sin nombre)")
            continue
        tipo = parte.get_content_type()
        carga = parte.get_payload(decode=True) or b""
        contenido = carga.decode(parte.get_content_charset() or "utf-8", errors="replace")
        if tipo == "text/plain" and texto is None:
            texto = contenido
        elif tipo == "text/html" and html is None:
            html = contenido
    if texto is None and html is not None:
        texto = re.sub(r"(?is)<(script|style).*?</\1>", "", html)
        texto = re.sub(r"(?i)<br\s*/?>|</p>|</div>|</tr>", "\n", texto)
        texto = re.sub(r"<[^>]+>", "", texto)
        texto = re.sub(r"&nbsp;", " ", texto)
        texto = re.sub(r"\n\s*\n+", "\n\n", texto)
    texto = (texto or "").strip()
    if len(texto) > MAX_CUERPO:
        texto = texto[:MAX_CUERPO] + "\n[… cuerpo recortado …]"
    return texto, adjuntos


@mcp.tool()
def correo_buzones() -> list[str]:
    """Lista los buzones (carpetas) del correo de iCloud, p. ej. INBOX, "Sent Messages", Drafts."""
    m = _imap()
    try:
        _, datos = m.list()
        buzones = []
        for linea in datos or []:
            linea = linea.decode(errors="replace")
            coincide = re.search(r'\) "?[^"]*"? (.+)$', linea)
            if coincide:
                buzones.append(coincide.group(1).strip('"'))
        return buzones
    finally:
        m.logout()


@mcp.tool()
def correo_buscar(
    buzon: str = "INBOX",
    texto: str | None = None,
    remitente: str | None = None,
    asunto: str | None = None,
    no_leidos: bool = False,
    desde_fecha: str | None = None,
    hasta_fecha: str | None = None,
    limite: int = 20,
) -> list[dict]:
    """Busca correos en un buzón de iCloud. Devuelve los más recientes primero.

    desde_fecha / hasta_fecha en formato AAAA-MM-DD (hasta_fecha es exclusiva).
    Cada resultado trae uid, fecha, remitente, asunto y si está leído; usa
    correo_leer con el uid para ver el contenido.
    """
    criterios: list[str] = []
    if texto:
        criterios += ["TEXT", f'"{texto.replace(chr(34), "")}"']
    if remitente:
        criterios += ["FROM", f'"{remitente.replace(chr(34), "")}"']
    if asunto:
        criterios += ["SUBJECT", f'"{asunto.replace(chr(34), "")}"']
    if no_leidos:
        criterios.append("UNSEEN")
    if desde_fecha:
        criterios += ["SINCE", _fecha_imap(desde_fecha)]
    if hasta_fecha:
        criterios += ["BEFORE", _fecha_imap(hasta_fecha)]
    m = _imap()
    try:
        m.select(_buzon(buzon), readonly=True)
        m._encoding = "utf-8"
        estado, datos = m.uid("SEARCH", "CHARSET", "UTF-8", *(criterios or ["ALL"]))
        if estado != "OK":
            raise RuntimeError(f"La búsqueda IMAP falló: {datos}")
        uids = (datos[0] or b"").split()[-max(1, min(limite, 100)):][::-1]
        resultados = []
        for uid in uids:
            _, partes = m.uid(
                "FETCH", uid, "(FLAGS BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE)])"
            )
            cabecera, banderas = b"", b""
            for p in partes:
                if isinstance(p, tuple):
                    banderas, cabecera = p[0], p[1]
            msg = email.message_from_bytes(cabecera)
            resultados.append({
                "uid": uid.decode(),
                "fecha": msg.get("Date", ""),
                "remitente": _decodificar(msg.get("From")),
                "asunto": _decodificar(msg.get("Subject")),
                "leido": b"\\Seen" in banderas,
            })
        return resultados
    finally:
        m.logout()


@mcp.tool()
def correo_leer(uid: str, buzon: str = "INBOX", marcar_leido: bool = False) -> dict:
    """Lee un correo por su uid (de correo_buscar). No lo marca como leído salvo que se pida."""
    m = _imap()
    try:
        m.select(_buzon(buzon), readonly=not marcar_leido)
        consulta = "(BODY[])" if marcar_leido else "(BODY.PEEK[])"
        _, partes = m.uid("FETCH", uid, consulta)
        crudo = next((p[1] for p in partes if isinstance(p, tuple)), None)
        if crudo is None:
            raise RuntimeError(f"No existe el correo con uid {uid} en {buzon}.")
        msg = email.message_from_bytes(crudo)
        cuerpo, adjuntos = _cuerpo_texto(msg)
        return {
            "uid": uid,
            "fecha": msg.get("Date", ""),
            "remitente": _decodificar(msg.get("From")),
            "para": _decodificar(msg.get("To")),
            "cc": _decodificar(msg.get("Cc")),
            "asunto": _decodificar(msg.get("Subject")),
            "cuerpo": cuerpo,
            "adjuntos": adjuntos,
        }
    finally:
        m.logout()


@mcp.tool()
def correo_marcar(uids: list[str], leido: bool = True, buzon: str = "INBOX") -> str:
    """Marca uno o varios correos como leídos (leido=True) o no leídos (leido=False)."""
    m = _imap()
    try:
        m.select(_buzon(buzon))
        accion = "+FLAGS" if leido else "-FLAGS"
        m.uid("STORE", ",".join(uids), accion, "(\\Seen)")
        return f"{len(uids)} correo(s) marcados como {'leídos' if leido else 'no leídos'} en {buzon}."
    finally:
        m.logout()


def _mensaje(para: list[str], asunto: str, cuerpo: str, cc: list[str] | None) -> EmailMessage:
    apple_id, _ = _credenciales()
    msg = EmailMessage()
    msg["From"] = apple_id
    msg["To"] = ", ".join(para)
    if cc:
        msg["Cc"] = ", ".join(cc)
    msg["Subject"] = asunto
    msg["Date"] = email.utils.formatdate(localtime=True)
    msg["Message-ID"] = email.utils.make_msgid(domain="icloud.com")
    msg.set_content(cuerpo)
    return msg


@mcp.tool()
def correo_crear_borrador(para: list[str], asunto: str, cuerpo: str, cc: list[str] | None = None) -> str:
    """Guarda un borrador en la carpeta Drafts de iCloud, sin enviarlo."""
    msg = _mensaje(para, asunto, cuerpo, cc)
    m = _imap()
    try:
        estado, datos = m.append("Drafts", "(\\Draft)", imaplib.Time2Internaldate(time.time()), msg.as_bytes())
        if estado != "OK":
            raise RuntimeError(f"No se pudo guardar el borrador: {datos}")
        return f"Borrador «{asunto}» guardado en Drafts."
    finally:
        m.logout()


@mcp.tool()
def correo_enviar(para: list[str], asunto: str, cuerpo: str, cc: list[str] | None = None) -> str:
    """ENVÍA un correo desde la cuenta de iCloud por SMTP.

    Acción irreversible: confirma antes con el usuario los destinatarios, el
    asunto y el texto exacto. Si hay dudas, usa correo_crear_borrador.
    """
    apple_id, clave = _credenciales()
    msg = _mensaje(para, asunto, cuerpo, cc)
    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=45) as s:
        s.starttls(context=ssl.create_default_context())
        s.login(apple_id, clave)
        s.send_message(msg)
    # iCloud no guarda solo la copia enviada por SMTP: se agrega a "Sent Messages".
    try:
        m = _imap()
        try:
            m.append('"Sent Messages"', "(\\Seen)", imaplib.Time2Internaldate(time.time()), msg.as_bytes())
        finally:
            m.logout()
    except Exception:
        pass
    return f"Correo «{asunto}» enviado a {', '.join(para)}."


# --- DAV (calendario y contactos) ---

_cache_colecciones: dict[str, list[dict]] = {}


def _dav(metodo: str, url: str, cuerpo: str | None = None, profundidad: str | None = None,
         tipo: str = "application/xml; charset=utf-8", extra: dict | None = None) -> httpx.Response:
    apple_id, clave = _credenciales()
    cabeceras = {"Content-Type": tipo}
    if profundidad is not None:
        cabeceras["Depth"] = profundidad
    if extra:
        cabeceras.update(extra)
    r = httpx.request(metodo, url, content=cuerpo, headers=cabeceras, auth=(apple_id, clave),
                      timeout=45, follow_redirects=True)
    if r.status_code == 401:
        raise RuntimeError("iCloud rechazó las credenciales (401). Revisa la contraseña específica de app.")
    if r.status_code >= 400:
        raise RuntimeError(f"{metodo} {r.status_code} en {url}")
    return r


def _respuestas(r: httpx.Response) -> list[tuple[str, ET.Element]]:
    raiz = ET.fromstring(r.content)
    salida = []
    for resp in raiz.findall("d:response", NS):
        href = resp.findtext("d:href", default="", namespaces=NS)
        salida.append((urljoin(str(r.url), href), resp))
    return salida


def _principal(host: str, well_known: str) -> str:
    """Busca el principal por la ruta .well-known (RFC 6764) y, si falla, por la raíz."""
    ultimo_error: Exception | None = None
    for bootstrap in (f"{host}/.well-known/{well_known}", f"{host}/"):
        try:
            r = _dav("PROPFIND", bootstrap, '<d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>', "0")
            href = ET.fromstring(r.content).find(".//d:current-user-principal/d:href", NS)
            if href is not None:
                return urljoin(str(r.url), href.text)
            ultimo_error = RuntimeError("No se encontró el principal del usuario en iCloud.")
        except RuntimeError as e:
            if "401" in str(e):
                raise
            ultimo_error = e
    raise ultimo_error or RuntimeError("No se encontró el principal del usuario en iCloud.")


def _home(host: str, well_known: str, prop: str, ns: str) -> str:
    principal = _principal(host, well_known)
    r = _dav("PROPFIND", principal, f'<d:propfind xmlns:d="DAV:" xmlns:x="{ns}"><d:prop><x:{prop}/></d:prop></d:propfind>', "0")
    href = ET.fromstring(r.content).find(f".//{{{ns}}}{prop}/d:href", NS)
    if href is None:
        raise RuntimeError(f"No se encontró {prop} en iCloud.")
    return urljoin(str(r.url), href.text)


def _colecciones(tipo: str) -> list[dict]:
    if tipo in _cache_colecciones:
        return _cache_colecciones[tipo]
    if tipo == "calendar":
        home = _home(CALDAV_BOOTSTRAP, "caldav", "calendar-home-set", NS["c"])
        etiqueta = f"{{{NS['c']}}}calendar"
    else:
        home = _home(CARDDAV_BOOTSTRAP, "carddav", "addressbook-home-set", NS["a"])
        etiqueta = f"{{{NS['a']}}}addressbook"
    r = _dav("PROPFIND", home, '<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop>'
             '<d:resourcetype/><d:displayname/><c:supported-calendar-component-set/></d:prop></d:propfind>', "1")
    colecciones = []
    for url, resp in _respuestas(r):
        rt = resp.find(".//d:resourcetype", NS)
        if rt is None or rt.find(etiqueta) is None:
            continue
        if tipo == "calendar":
            comps = {c.get("name") for c in resp.iter(f"{{{NS['c']}}}comp")}
            if comps and "VEVENT" not in comps:
                continue  # listas de recordatorios (VTODO)
        nombre = resp.findtext(".//d:displayname", default="", namespaces=NS) or url.rstrip("/").rsplit("/", 1)[-1]
        colecciones.append({"nombre": nombre, "url": url})
    _cache_colecciones[tipo] = colecciones
    return colecciones


def _calendario(nombre: str | None) -> list[dict]:
    todos = _colecciones("calendar")
    if not nombre:
        return todos
    elegidos = [c for c in todos if c["nombre"].lower() == nombre.lower()]
    if not elegidos:
        raise RuntimeError(f"No hay un calendario llamado «{nombre}». Disponibles: {', '.join(c['nombre'] for c in todos)}")
    return elegidos


def _a_local(valor) -> str:
    if isinstance(valor, datetime):
        if valor.tzinfo is None:
            return valor.isoformat(timespec="minutes")
        return valor.astimezone(ZONA).isoformat(timespec="minutes")
    return valor.isoformat()  # fecha de evento de todo el día


def _instante(texto: str) -> datetime:
    d = datetime.fromisoformat(texto)
    return d if d.tzinfo else d.replace(tzinfo=ZONA)


@mcp.tool()
def calendario_listar() -> list[str]:
    """Lista los nombres de los calendarios de iCloud."""
    return [c["nombre"] for c in _colecciones("calendar")]


@mcp.tool()
def calendario_eventos(desde: str, hasta: str, calendario: str | None = None) -> list[dict]:
    """Eventos entre dos fechas (ISO, p. ej. 2026-11-01 o 2026-11-01T08:00), en hora de Colombia.

    Si no se indica calendario, busca en todos. Los eventos repetitivos se
    expanden a sus ocurrencias dentro del rango.
    """
    inicio, fin = _instante(desde), _instante(hasta)
    if fin <= inicio:
        raise RuntimeError("«hasta» debe ser posterior a «desde».")
    rango = (inicio.astimezone(ZoneInfo("UTC")).strftime("%Y%m%dT%H%M%SZ"),
             fin.astimezone(ZoneInfo("UTC")).strftime("%Y%m%dT%H%M%SZ"))
    consulta = f"""<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop><d:getetag/><c:calendar-data><c:expand start="{rango[0]}" end="{rango[1]}"/></c:calendar-data></d:prop>
  <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT">
    <c:time-range start="{rango[0]}" end="{rango[1]}"/>
  </c:comp-filter></c:comp-filter></c:filter>
</c:calendar-query>"""
    eventos = []
    for cal in _calendario(calendario):
        r = _dav("REPORT", cal["url"], consulta, "1")
        for url, resp in _respuestas(r):
            datos = resp.findtext(".//c:calendar-data", default="", namespaces=NS)
            if not datos:
                continue
            for ev in Calendar.from_ical(datos).walk("VEVENT"):
                ini = ev.decoded("DTSTART")
                fin_ev = ev.decoded("DTEND") if ev.get("DTEND") else None
                eventos.append({
                    "calendario": cal["nombre"],
                    "titulo": str(ev.get("SUMMARY", "")),
                    "inicio": _a_local(ini),
                    "fin": _a_local(fin_ev) if fin_ev else None,
                    "todo_el_dia": not isinstance(ini, datetime),
                    "ubicacion": str(ev.get("LOCATION", "")),
                    "descripcion": str(ev.get("DESCRIPTION", ""))[:2000],
                    "url": url,
                })
    eventos.sort(key=lambda e: e["inicio"])
    return eventos


@mcp.tool()
def calendario_crear_evento(
    calendario: str,
    titulo: str,
    inicio: str,
    fin: str | None = None,
    todo_el_dia: bool = False,
    ubicacion: str | None = None,
    descripcion: str | None = None,
) -> str:
    """Crea un evento en un calendario de iCloud.

    inicio / fin en ISO (2026-11-05T10:00); sin zona se asume hora de Colombia.
    Con todo_el_dia=True usa solo la fecha (2026-11-05). Si falta fin, dura 1 hora
    (o 1 día si es de todo el día).
    """
    cal = _calendario(calendario)[0]
    ev = Event()
    uid = f"{uuid.uuid4()}"
    ev.add("UID", uid)
    ev.add("SUMMARY", titulo)
    ev.add("DTSTAMP", datetime.now(ZoneInfo("UTC")))
    if todo_el_dia:
        d_ini = date.fromisoformat(inicio[:10])
        d_fin = date.fromisoformat(fin[:10]) + timedelta(days=1) if fin else d_ini + timedelta(days=1)
        ev.add("DTSTART", d_ini)
        ev.add("DTEND", d_fin)
    else:
        t_ini = _instante(inicio)
        t_fin = _instante(fin) if fin else t_ini + timedelta(hours=1)
        if t_fin <= t_ini:
            raise RuntimeError("El fin debe ser posterior al inicio.")
        ev.add("DTSTART", t_ini.astimezone(ZoneInfo("UTC")))
        ev.add("DTEND", t_fin.astimezone(ZoneInfo("UTC")))
    if ubicacion:
        ev.add("LOCATION", ubicacion)
    if descripcion:
        ev.add("DESCRIPTION", descripcion)
    vcal = Calendar()
    vcal.add("PRODID", "-//iClonud//MCP//ES")
    vcal.add("VERSION", "2.0")
    vcal.add_component(ev)
    destino = urljoin(cal["url"].rstrip("/") + "/", f"{uid}.ics")
    _dav("PUT", destino, vcal.to_ical(), tipo="text/calendar; charset=utf-8", extra={"If-None-Match": "*"})
    return f"Evento «{titulo}» creado en el calendario {cal['nombre']}."


# --- Contactos (CardDAV) ---

def _vcard(texto: str) -> dict:
    texto = re.sub(r"\r?\n[ \t]", "", texto)  # desplegar líneas continuadas
    contacto = {"nombre": "", "organizacion": "", "correos": [], "telefonos": []}
    for linea in texto.splitlines():
        if ":" not in linea:
            continue
        clave, valor = linea.split(":", 1)
        clave = clave.split(";")[0].split(".")[-1].upper()
        valor = valor.replace("\\,", ",").replace("\\;", ";").strip()
        if clave == "FN":
            contacto["nombre"] = valor
        elif clave == "ORG":
            contacto["organizacion"] = valor.strip(";")
        elif clave == "EMAIL" and valor:
            contacto["correos"].append(valor)
        elif clave == "TEL" and valor:
            contacto["telefonos"].append(valor)
    return contacto


@mcp.tool()
def contactos_buscar(texto: str, limite: int = 20) -> list[dict]:
    """Busca contactos de iCloud cuyo nombre, organización, correo o teléfono contenga el texto."""
    consulta = ('<a:addressbook-query xmlns:d="DAV:" xmlns:a="urn:ietf:params:xml:ns:carddav">'
                '<d:prop><d:getetag/><a:address-data/></d:prop></a:addressbook-query>')
    buscado = texto.lower()
    solo_digitos = re.sub(r"\D", "", texto)
    encontrados = []
    for libreta in _colecciones("addressbook"):
        r = _dav("REPORT", libreta["url"], consulta, "1")
        for _, resp in _respuestas(r):
            datos = resp.findtext(".//a:address-data", default="", namespaces=NS)
            if not datos:
                continue
            c = _vcard(datos)
            campos = " ".join([c["nombre"], c["organizacion"], *c["correos"]]).lower()
            telefonos = "".join(re.sub(r"\D", "", t) for t in c["telefonos"])
            if buscado in campos or (len(solo_digitos) >= 4 and solo_digitos in telefonos):
                encontrados.append(c)
                if len(encontrados) >= limite:
                    return encontrados
    return encontrados


@mcp.tool()
def icloud_estado() -> dict:
    """Comprueba la conexión con iCloud (correo, calendario y contactos) sin mostrar credenciales."""
    estado = {}
    try:
        m = _imap()
        m.logout()
        estado["correo"] = "conectado"
    except Exception as e:  # noqa: BLE001
        estado["correo"] = f"error: {e}"
    for tipo, clave in (("calendar", "calendario"), ("addressbook", "contactos")):
        try:
            estado[clave] = f"conectado ({len(_colecciones(tipo))} colecciones)"
        except Exception as e:  # noqa: BLE001
            estado[clave] = f"error: {e}"
    return estado


if __name__ == "__main__":
    mcp.run()
