# iClonud · iCloud en Android por estándares abiertos

App de Android (Kotlin + Jetpack Compose) que accede a una cuenta de iCloud usando los
protocolos **abiertos y soportados por Apple** para clientes externos:

| Servicio | Protocolo | Servidor de iCloud |
|---|---|---|
| Correo (leer y enviar) | IMAP + SMTP | `imap.mail.me.com:993` · `smtp.mail.me.com:587` |
| Calendario (leer, crear, borrar) | CalDAV | `caldav.icloud.com` (descubrimiento RFC 6764) |
| Contactos (leer, crear, borrar) | CardDAV | `contacts.icloud.com` (descubrimiento RFC 6764) |

El acceso se hace **con el Apple ID y una contraseña específica de app**, que es el método
oficial de Apple para apps de terceros. Ver más abajo.

## Cómo compilar el APK

1. Instala **Android Studio** (incluye el JDK y el SDK).
2. `File → Open…` y elige esta carpeta `iclonud`. Android Studio descarga Gradle y las
   dependencias solo (si pide el wrapper, acepta «Use Gradle wrapper»).
3. Conecta un teléfono con depuración USB, o usa un emulador, y pulsa **Run ▶**.
   Para un APK instalable: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
   El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

Por línea de comandos, con el SDK instalado: `./gradlew assembleDebug`.

## La contraseña específica de app (imprescindible)

Apple **no** permite que un cliente externo use la contraseña normal del Apple ID. Hay que crear una:

1. Entra en **appleid.apple.com** → *Iniciar sesión y seguridad* → *Contraseñas específicas de app*.
2. Crea una nueva (requiere tener activada la verificación en dos pasos).
3. Apple te da algo como `abcd-efgh-ijkl-mnop`. Esa es la que se escribe en la app
   (los espacios/guiones se pueden incluir; la app los limpia).

La contraseña se guarda **cifrada en el dispositivo** (EncryptedSharedPreferences sobre el
Android Keystore) y solo viaja por TLS hacia los servidores de Apple. No se envía a ningún
otro servidor; esta app no tiene backend propio.

## Estructura

```
app/src/main/java/com/iclonud/bridge/
  data/        Credentials (cifrado), ICloudEndpoints
  net/         DavClient (PROPFIND/REPORT/PUT + descubrimiento), CalDav/CardDav/Mail repos, IcalUtil
  model/       MailMessage, CalEvent, Contact
  vm/          ViewModels (Login, Mail, Calendar, Contacts)
  ui/          Compose: Login, Home (pestañas), Mail, Calendar, Contacts
```

## Alcance y límites honestos

- **Sí** cubre: acceso con el usuario de iCloud, y lectura/escritura de Correo, Calendario y Contactos.
- **No** se puede cubrir con estándares abiertos (Apple no ofrece API pública para ello):
  **Fotos, iCloud Drive, Notas, Recordatorios, Find My**. iCloud sí expone CalDAV/CardDAV/IMAP,
  pero esos cinco servicios no tienen protocolo abierto. Para verlos en Android, la vía es
  **iCloud.com en el navegador**. (Notas y Recordatorios a veces aparecen como colecciones CalDAV
  limitadas, pero no de forma fiable; por eso no se incluyen.)
- **Find My** y un **launcher** del sistema no tienen relación con estos protocolos y no forman
  parte de esta app.
- Es un cliente directo (sin sincronización en segundo plano ni caché offline). Cada pestaña
  consulta a iCloud al abrirse y con el botón de actualizar.

## Aviso

Proyecto educativo. No está afiliado a Apple. «iCloud», «Apple» y «Find My» son marcas de Apple Inc.
