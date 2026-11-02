# Servidor MCP de iCloud

Da a Claude Code (o a cualquier cliente MCP por stdio) acceso a tu **correo, calendario y
contactos de iCloud**, usando solo IMAP/SMTP, CalDAV y CardDAV. Es un único archivo,
[`icloud_mcp.py`](icloud_mcp.py), con sus dependencias declaradas dentro (PEP 723).

## Herramientas

| Herramienta | Qué hace |
|---|---|
| `icloud_estado` | Comprueba la conexión con correo, calendario y contactos |
| `correo_buzones` | Lista las carpetas del correo |
| `correo_buscar` | Busca por texto, remitente, asunto, no leídos y fechas |
| `correo_leer` | Lee un correo (sin marcarlo como leído, salvo que se pida) |
| `correo_marcar` | Marca correos como leídos o no leídos |
| `correo_crear_borrador` | Guarda un borrador en Drafts, sin enviarlo |
| `correo_enviar` | Envía un correo por SMTP (Claude debe confirmarlo contigo antes) |
| `calendario_listar` | Lista los calendarios |
| `calendario_eventos` | Eventos entre dos fechas, con repeticiones expandidas |
| `calendario_crear_evento` | Crea un evento (con hora o de todo el día) |
| `contactos_buscar` | Busca contactos por nombre, empresa, correo o teléfono |

## Instalación

1. Instala [uv](https://docs.astral.sh/uv/getting-started/installation/).
2. Regístralo en Claude Code:

   ```bash
   claude mcp add --scope user icloud -- uv run --quiet --script /ruta/a/iclonud/mcp/icloud_mcp.py
   ```

3. Crea el archivo de credenciales y protégelo:

   ```bash
   mkdir -p ~/.config/iclonud-mcp
   cp credenciales.ejemplo ~/.config/iclonud-mcp/credenciales.env
   chmod 600 ~/.config/iclonud-mcp/credenciales.env
   ```

   Escribe en él tu Apple ID y una **contraseña específica de app**
   (account.apple.com → Inicio de sesión y seguridad → Contraseñas de aplicaciones).

4. Reinicia Claude Code o reconecta el servidor con `/mcp`.

### Configuración opcional

| Variable | Por defecto | Uso |
|---|---|---|
| `ICLOUD_CREDENCIALES` | `~/.config/iclonud-mcp/credenciales.env` | Otra ruta para el archivo |
| `ICLOUD_APPLE_ID`, `ICLOUD_APP_PASSWORD` | — | Credenciales por variables de entorno, en vez del archivo |
| `ICLOUD_ZONA` | `America/Bogota` | Zona horaria para leer y crear eventos |

## Seguridad

- Las credenciales nunca aparecen en las respuestas de las herramientas.
- Solo se conecta a `imap.mail.me.com`, `smtp.mail.me.com`, `caldav.icloud.com` y `contacts.icloud.com`.
- `correo_enviar` es la única acción que sale de tu cuenta hacia otras personas; su descripción
  le pide al modelo confirmar contigo destinatarios y texto antes de usarla.
- Revoca la contraseña específica de app en account.apple.com si dejas de usarlo.
