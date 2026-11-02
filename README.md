# iClonud

**Tu correo, calendario y contactos de iCloud en Android y en Claude Code, con protocolos abiertos.**

[![Descargar APK](https://img.shields.io/github/v/release/Killernet2003/iclonud?label=Descargar%20APK&color=0a6cf0)](https://github.com/Killernet2003/iclonud/releases/latest)
[![Licencia MIT](https://img.shields.io/badge/licencia-MIT-green)](LICENSE)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3ddc84)
![MCP](https://img.shields.io/badge/Claude%20Code-MCP-d97757)

Página del proyecto: **https://killernet2003.github.io/iclonud/**

iClonud tiene dos partes que usan los mismos servidores oficiales de iCloud:

| | Qué es | Carpeta |
|---|---|---|
| 📱 **App de Android** | Kotlin + Jetpack Compose. Lee y envía correo, ve y crea eventos, ve y crea contactos. | [`android/`](android/) |
| 🤖 **Servidor MCP** | Le da a Claude Code (o a cualquier cliente MCP) herramientas para tu correo, calendario y contactos de iCloud. | [`mcp/`](mcp/) |

| Servicio | Protocolo | Servidor |
|---|---|---|
| Correo | IMAP + SMTP | `imap.mail.me.com:993` · `smtp.mail.me.com:587` |
| Calendario | CalDAV | `caldav.icloud.com` |
| Contactos | CardDAV | `contacts.icloud.com` |

## Seguridad y privacidad

- Se entra con el **Apple ID y una contraseña específica de app**, el método oficial de Apple
  para clientes externos. Tu contraseña normal del Apple ID nunca se usa.
- No hay servidor intermedio: la app y el MCP hablan **solo con los servidores de Apple**, por TLS.
- En Android la contraseña se guarda cifrada con el Android Keystore. En el MCP se lee de un
  archivo local con permisos `600`.
- La contraseña específica de app se puede revocar cuando quieras en
  [account.apple.com](https://account.apple.com), sin cambiar tu contraseña principal.

## Instalar la app

1. Descarga `iclonud-<versión>.apk` desde [Releases](https://github.com/Killernet2003/iclonud/releases/latest).
2. Ábrelo en el teléfono y permite «instalar apps de origen desconocido» si Android lo pide.
3. Crea una contraseña específica de app (ver abajo) y entra con tu Apple ID.

Huella SHA-256 del certificado de firma, para verificar el APK
(`apksigner verify --print-certs iclonud-*.apk`):

```
EA:0C:FF:5C:92:2A:86:21:08:60:B4:5F:8B:73:B6:E0:7A:8E:FE:F3:8C:84:1A:B9:39:04:2F:44:E8:D8:C1:87
```

## Usar el MCP en Claude Code

Requiere [uv](https://docs.astral.sh/uv/). Las dependencias se instalan solas.

```bash
git clone https://github.com/Killernet2003/iclonud.git
claude mcp add --scope user icloud -- uv run --quiet --script "$PWD/iclonud/mcp/icloud_mcp.py"
```

Luego escribe tus credenciales en `~/.config/iclonud-mcp/credenciales.env` (detalles en
[`mcp/README.md`](mcp/README.md)) y pídele a Claude, por ejemplo: *«resume mis correos sin leer de iCloud»*.

## La contraseña específica de app

1. Entra en [account.apple.com](https://account.apple.com) → *Inicio de sesión y seguridad* →
   *Contraseñas específicas de app*. Requiere la verificación en dos pasos activada.
2. Crea una, por ejemplo «iClonud». Apple te muestra algo como `abcd-efgh-ijkl-mnop`.
3. Úsala en la app o en el archivo de credenciales del MCP.

## Límites honestos

Apple **no ofrece protocolo abierto** para Fotos, iCloud Drive, Notas, Recordatorios ni Buscar.
iClonud no los incluye y no usa APIs privadas ni técnicas de *scraping*. Para esos servicios,
usa [Accesos iCloud](https://killernet2003.github.io/accesos-icloud/), un lanzador web de iCloud.com.

## Compilar desde el código

```bash
cd android
./gradlew assembleDebug        # o ábrelo en Android Studio
```

El APK de publicación se firma con una llave que no está en el repositorio. Sin ella,
`assembleRelease` genera un APK sin firmar.

## Licencia

[MIT](LICENSE). Proyecto independiente: **no está afiliado, patrocinado ni aprobado por Apple Inc.**
iCloud es una marca de Apple Inc.
