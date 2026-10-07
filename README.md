# ExploraCity

App Android de **turismo colaborativo**. Turistas y residentes **descubren, publican y validan puntos de interés (POI)** de su ciudad: cafés, museos, senderos, miradores, sitios históricos… Cada lugar que publica la comunidad pasa por un **moderador**, que lo verifica, lo rechaza con un motivo o lo da por finalizado. Así el mapa de la ciudad se mantiene confiable.

Es un proyecto de la Universidad del Quindío. Es un monorepo con la app Android (Kotlin + Jetpack Compose) y su API REST (Ktor).

## Funcionalidad

### Explorar
- **Feed en lista y en mapa** (Google Maps), con marcadores agrupados y una tarjeta inferior para el lugar seleccionado. La lista se recarga deslizando hacia abajo.
- **Búsqueda y filtros**: categorías, «Cercanos» (5 km, con permiso de ubicación) o toda la ciudad, y «Solo verificados».
- **Detalle del lugar**: fotos, descripción, horario, rango de precio, ubicación y estado de verificación.
- **Interacción**: voto «Es importante», «Marcar como visitado» con una experiencia opcional, y comentarios de hasta 300 caracteres. Los comentarios se envían de forma optimista y se pueden reintentar.
- **Perfil público** de otras personas, con la opción de reportarlo.
- **Cuentas eliminadas**: sus lugares verificados, sus comentarios y los avisos que generaron siguen a la vista como «Usuario eliminado», sin nivel ni enlace al perfil.

### Publicar
- **Formulario en 5 pasos**: título y descripción, categoría con **sugerencia de IA**, ubicación en el mapa, horario y rango de precio, y de 1 a 5 fotos.
- **Detección de posibles duplicados**: al confirmar la ubicación se buscan lugares con un nombre parecido en un radio de 50 m. La persona puede abrir el existente o confirmar que es un lugar distinto. En ese caso el moderador ve la marca «posible duplicado».
- **Borrador persistente**: sobrevive a la rotación, al cierre de la app y a la falta de conexión.
- **Mis publicaciones**, con filtros por estado: editar, eliminar, ver el motivo de un rechazo y «Corregir y reenviar».

### Social y reputación
- **Notificaciones** dentro de la app (sin push): comentarios, verificación, rechazo, rechazo por duplicado, logros y publicaciones finalizadas.
- **Perfil propio**, con puntos, **niveles** (Recién llegado, Explorador, Aventurero y Embajador Local) e **insignias** con su progreso.

### Moderación
- Una 5.ª pestaña **«Moderación»** aparece solo con el rol Moderador, dentro de la misma app. No hay panel web. Su insignia cuenta las publicaciones pendientes.
- **Cola de pendientes**: va de la más antigua a la más reciente, con su urgencia y un filtro «Posibles duplicados». Sin conexión muestra la última cola guardada, solo para leer.
- **Revisión**: fotos a pantalla completa, todos los datos del lugar, su mapa, el reporte si lo hay y la ficha del autor.
- **Comparar un posible duplicado** con el lugar existente: los dos lado a lado y la distancia entre ellos en el mapa.
- **Decisiones**:
  - **Verificar**, con una nota interna opcional.
  - **Rechazar con motivo**. Un rechazo por duplicado enlaza el original, y «Otro motivo» pide un detalle.
  - **Cambiar el estado** de una publicación ya decidida: pasarla a finalizada o devolverla a pendiente.
- Al terminar una decisión se abre la siguiente pendiente. Cada decisión notifica al autor.
- **Resueltas**: lo ya decidido, con su motivo y su nota interna. Con la cola vacía se muestra un resumen del trabajo del día.

### Acceso y cuenta
- **Arranque**: el splash decide por dónde se entra. La primera vez va al onboarding de tres paneles, después al inicio de sesión, y con una sesión guardada entra directo al feed. Si la red falla, se puede seguir sin conexión con lo guardado. Si la API ya no acepta la sesión guardada, el inicio de sesión avisa «Tu sesión terminó. Vuelve a entrar.».
- **Inicio de sesión**: valida el correo y la contraseña. Ante un error no revela cuál de los dos datos falló, y sin conexión el botón queda deshabilitado con su explicación. Tras 5 intentos fallidos con el mismo correo, pide esperar 15 minutos.
- **Registro**: pide la autorización de tratamiento de datos (**Ley 1581 de 2012**), que nunca viene marcada. La cuenta se crea aunque falle el correo de bienvenida.
- **Recuperación de contraseña**: llega un enlace por correo que abre la app en «Nueva contraseña». Vence a los 30 minutos, y se puede reenviar después de una cuenta regresiva de 60 s. La app nunca revela si un correo tiene cuenta.
- **Editar perfil**: foto, nombre, «Sobre mí» y cómo se presenta la persona («De visita» o «Residente»).
- **Ajustes**: tema claro, oscuro o del sistema; documentos legales; **descargar mis datos** en JSON; y cerrar sesión.
- **Cambiar correo**: pide la contraseña y envía un enlace al correo nuevo. El cambio se aplica al abrir ese enlace, y mientras tanto Ajustes muestra que falta confirmarlo.
- **Eliminar la cuenta**: primero muestra qué se borra y qué se conserva de forma anónima, y luego pide escribir **ELIMINAR** para confirmar.

### Transversal
- **Sin conexión**: el feed guarda en Room lo último que cargó y el detalle de cada lugar, y muestra la antigüedad de esa copia. Las visitas, los votos y los comentarios hechos sin red quedan en una **cola de envío** que WorkManager envía cuando vuelve la conexión.
- **Accesibilidad**: el estado nunca se muestra solo con color; el contraste es de al menos 4,5:1 en ambos temas; todo lo interactivo funciona con TalkBack; y el diseño se adapta con la **fuente al 200 %**.
- Tema claro y oscuro con Material 3. Las tipografías son Outfit y Manrope.

### Categorías y estados

| Categorías | Estados de una publicación |
|---|---|
| Gastronomía · Cultura · Naturaleza · Entretenimiento · Historia | **Pendiente** → **Verificada** o **Rechazada** → **Finalizada**. Editar una publicación la devuelve a Pendiente. |

## Estado del proyecto

**Todas las pantallas del diseño están implementadas**, y la app ya trabaja con la API (`api-ktor`). No le quedan datos de ejemplo: las cuentas, los lugares, los avisos, las publicaciones y la moderación vienen del servidor.

La conexión se hizo por áreas, en cinco partes:
1. La base de la API.
2. Acceso y cuenta.
3. Explorar y social.
4. Publicar y moderar.
5. El paso de la app a la API.

Las cuatro primeras traían la API de su área y el cliente de la app, todavía sin conectar. La quinta conectó la app y retiró los datos de ejemplo. Los `Fake*` y `Sample*` con los que se construyeron las pantallas quedan solo como dobles de prueba, en `src/test`. Las vistas previas de Compose usan `ui/preview/PreviewData.kt`.

La API atiende:
- la sesión y la recuperación de contraseña;
- la cuenta y el perfil propio;
- el feed, el mapa y el detalle de cada lugar;
- los votos, las visitas y los comentarios;
- los avisos y el perfil público;
- la publicación: las fotos, la sugerencia de categoría, los lugares parecidos y las publicaciones propias;
- la moderación: la cola, las decisiones y «Resueltas»;
- la ciudad: su nombre, el centro del mapa y los límites de la búsqueda por dirección. Hoy es Armenia, y se cambia en `api-ktor/src/main/resources/application.conf`.

**Despliegue:** la API se publica en Google Cloud Run, con PostgreSQL en Cloud SQL. [`docs/despliegue.md`](docs/despliegue.md) configura Google Cloud desde cero, y GitHub Actions despliega cada cambio de `api-ktor/` que llega a `main`. Falta la app de producción: un APK firmado que use la dirección de Cloud Run y abra directo los enlaces del correo (App Links). Mientras tanto, un APK de producción apunta a `localhost`.

La API todavía no recibe reportes de lugares, así que la revisión de una pendiente (33) no muestra ninguno.

| Área | Pantallas (numeración del diseño) | Estado |
|---|---|---|
| Acceso (splash, onboarding, inicio de sesión, registro y recuperación) | 1–6, 6C | ✅ Implementada |
| Explorar | 7–14, 31 | ✅ Implementada |
| Publicar y mis publicaciones | 15–24 | ✅ Implementada |
| Notificaciones, perfil e insignias | 25–27 | ✅ Implementada |
| Editar perfil, ajustes, documentos legales y eliminar cuenta | 28–30, 29A, 4A | ✅ Implementada |
| Cambiar correo (Ajustes › Cuenta) | sin número | ✅ Implementada |
| Moderación y «Resueltas» | 32–37, 33A | ✅ Implementada |
| API (`api-ktor`) | — | ✅ Las 4 partes: base, acceso y cuenta, explorar y social, y publicar y moderar ([contrato](docs/api/README.md)) |
| Conexión de la app con la API | — | ✅ Conectada |
| Despliegue en Google Cloud | — | 🚧 La API en Cloud Run ([guía](docs/despliegue.md)). Falta la app de producción |

Las direcciones del mapa ya usan el Geocoder real de Android, detrás de `AddressResolver`, para que el backend pueda reemplazarlo después.

## Arquitectura

La arquitectura sigue el documento de arquitectura del software (modelo C4, v1.1) y sus decisiones arquitectónicas (ADR).

```
.
├── app-android/          App Android (proyecto Gradle, módulo app/)
├── api-ktor/             API REST en Ktor
├── docs/                 Modelo C4, arquitectura, decisiones, contrato de la API y despliegue
├── .github/              CI y despliegue (GitHub Actions) y plantilla de pull request
└── CONTRIBUTING.md       Flujo de ramas, commits y pull requests
```

### App Android (`app-android/`)

Paquete `co.edu.uniquindio.exploracity`, organizado por capas:

| Paquete | Contenido |
|---|---|
| `ui/` | Pantallas Compose, componentes del sistema de diseño y tema Material 3 |
| `navigation/` | Navegación con rutas tipadas y una barra inferior según el rol |
| `viewmodel/` | Estado de la UI con `StateFlow` |
| `data/remote` | Cliente de la API (Ktor Client, DTO) y sesión que renueva el token con un 401 |
| `data/local` | Sesión (rol, tokens y cuenta) y preferencias en DataStore, caché sin conexión en Room |
| `data/repository` | Repositorios `Api*` sobre la API. Los envoltorios `Offline*` guardan una copia para usar sin conexión, y los `OnlineOnly*` ni lo intentan sin red. `CityRepository` guarda la ciudad en el teléfono |
| `data/sync` | Cola de envío sin conexión con WorkManager |
| `data/connectivity`, `data/location`, `data/photos` | Estado de la red; ubicación del teléfono (`FusedLocationProvider`) y geocodificación; fotos |
| `domain/` | Modelos del dominio |
| `util/` | Formateadores y permisos |

**Stack:** Kotlin 2.4 · Jetpack Compose (BOM 2026.09) · Material 3 · Navigation Compose · Lifecycle/ViewModel · Coroutines · Ktor Client (OkHttp) · kotlinx.serialization · DataStore · Room · WorkManager · Maps Compose · Play Services Location. Usa `minSdk 28` y `targetSdk`/`compileSdk 37`.

**Pruebas:** JUnit, kotlinx-coroutines-test, Robolectric y Compose UI Test. Entre ellas está `ThemeContrastTest`, que exige el contraste mínimo de cada par de colores del tema. Los clientes de la API se prueban con el `MockEngine` de Ktor y los ejemplos del contrato (`docs/api/ejemplos`), los mismos que comprueba `api-ktor`.

### API (`api-ktor/`)

Paquetes `routes/`, `service/`, `repository/`, `model/`, `integration/`, `config/` y `plugins/`.

**Stack:** Ktor 3.6 (Netty) · autenticación JWT · Exposed + HikariCP · PostgreSQL con **PostGIS** y **pg_trgm** para detectar duplicados · Flyway · BCrypt. Las integraciones con Cloudinary (fotos), SendGrid (correo) y OpenRouter (sugerencia de categoría con IA) se hacen con Ktor Client.

La IA y la detección de duplicados se ejecutan en el backend, así que la clave de la IA nunca viaja en la app. Se despliega como imagen Docker en **Google Cloud Run**, con PostgreSQL en **Cloud SQL** ([despliegue](docs/despliegue.md)).

**Seguridad de la sesión:**
- Las contraseñas se guardan con BCrypt de costo 12.
- La sesión usa un JWT de acceso de 15 minutos y un token de renovación de 30 días, que cambia en cada uso y del que solo se guarda el hash. Si llega uno que ya se usó, se cierran todas las sesiones de la cuenta.
- Tras 5 intentos fallidos en 15 minutos con el mismo correo, el inicio de sesión queda bloqueado 15 minutos.

**Búsqueda:** el feed, el mapa y el perfil público ordenan por cercanía con PostGIS, desde la ubicación de la persona o, sin ella, desde el centro de la ciudad. La búsqueda por título ignora mayúsculas y tildes (`unaccent`).

**Reputación:**
- Marcar un lugar como visitado da +5 puntos la primera vez. Comentar no da puntos.
- Votar o visitar un lugar propio no suma: ni puntos ni avance de insignias.
- Al desbloquear una insignia llega un aviso de logro, una sola vez, y la insignia no se pierde aunque la cifra baje después.
- La primera publicación da +20 puntos al enviarla. Se pierden si se elimina o si un moderador la rechaza sin permitir que se reenvíe.
- Quedar verificada da +15 puntos, solo la primera vez: si vuelve a pendiente y se verifica otra vez, no se repiten.

**Publicar y moderar:**
- Las fotos se suben antes del envío, y el envío lleva sus direcciones. Repetir un envío que ya llegó (por ejemplo, desde la cola sin conexión) no lo duplica.
- Los lugares parecidos se buscan a 50 m con PostGIS y pg_trgm, entre los lugares públicos y las pendientes propias, nunca entre las pendientes de otras personas. Si la búsqueda del teléfono falló o el pin se movió después, la API la repite.
- Un moderador nunca ve ni decide sus propias publicaciones: las revisa otro. Si dos moderadores deciden a la vez, el segundo recibe un aviso de que ya está decidida.
- Lo que hay que corregir antes de reenviar sale del motivo del rechazo.

**Integraciones:** cada una tiene una versión real y otra de desarrollo. La real se activa cuando su clave está en `api-ktor/.env`.
- Sin SendGrid, los correos no salen: quedan en un buzón en memoria.
- Sin Cloudinary, las fotos van a `api-ktor/media/`, que está fuera de git, y la API las sirve en `/media`.
- Sin OpenRouter, la sugerencia de categoría sale de palabras clave del título y la descripción.

En Cloud Run (`APP_ENV=production`), la API no arranca sin SendGrid ni Cloudinary, ni con `DEV_MAILBOX`. Allí los enlaces del correo son https (`…run.app/enlace/restablecer?token=…`), porque Gmail no deja tocar los `exploracity://`. En el navegador abren una página con el botón «Abrir en ExploraCity». Con la huella de la firma de la app en `ANDROID_CERT_SHA256`, la API publica `/.well-known/assetlinks.json` para que Android los abra directo (App Links).

El esquema lo crean las migraciones de Flyway (`src/main/resources/db/migration`). El contrato con la app, con sus endpoints, códigos de error y ejemplos, está en [`docs/api/`](docs/api/README.md).

**Pruebas:** JUnit con `testApplication` de Ktor y PostGIS real con Testcontainers.

## Cómo ejecutar

### Requisitos
- **JDK 25**
- **Android Studio 2026.1.3** o posterior (el proyecto usa AGP 9.4) y el **Android SDK 37**
- Una clave de **Google Maps** para ver el mapa. Sin ella, la pantalla del mapa muestra un aviso de desarrollo.

### App Android

La app necesita la API en marcha: primero sigue los pasos de [API](#api).

Agrega la clave de Maps a `app-android/local.properties`. Ese archivo está fuera de git:

```properties
MAPS_API_KEY=tu_clave
# Opcional: la dirección de la API. Por omisión, http://localhost:8080/
API_BASE_URL=http://localhost:8080/
```

Con el teléfono por USB, deja `API_BASE_URL` como viene y reenvía el puerto de la API al equipo. En el emulador funciona igual:

```bash
adb reverse tcp:8080 tcp:8080
```

El reenvío se pierde al desconectar el teléfono o al activar el modo avión, y hay que repetirlo. Si prefieres la red local, pon la IP del equipo (`http://192.168.x.x:8080/`) en `API_BASE_URL` y en `PUBLIC_BASE_URL` de la API.

Las compilaciones de depuración pueden usar HTTP, para la API del equipo. La de producción solo acepta HTTPS.

Compila, prueba y revisa:

```bash
cd app-android
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

En Windows usa `gradlew.bat`. También puedes abrir `app-android/` en Android Studio y ejecutar la configuración `app`.

#### Herramientas de desarrollo

Estas opciones solo aparecen en las compilaciones de depuración:

- **Correo de prueba**: aparece en «Revisa tu correo» (6.a) y en «Confirma tu correo nuevo». Lee el buzón de desarrollo de la API, así que necesita `DEV_MAILBOX=true`. «Abrir el enlace del correo» hace lo que haría el enlace real: abre «Nueva contraseña» (6.b) o confirma el correo nuevo. «Abrir un enlace vencido» lleva a la pantalla de enlace vencido.
- **Muestrario del sistema de diseño**: un acceso desde Ajustes.

No hay cuentas de prueba: se crean en el registro de la app, contra la API. Una cuenta con uno de los correos de `MODERATOR_EMAILS` tiene la pestaña «Moderación». Si la lista cambia, el rol se pone al día al reiniciar la API, y la app lo ve al volver a entrar.

Los enlaces de los correos abren la app: `exploracity://enlace/restablecer?token=…` y `exploracity://enlace/confirmar-correo?token=…`. Sin el botón de prueba, también se pueden abrir con `adb`:

```bash
adb shell am start -a android.intent.action.VIEW -d "exploracity://enlace/restablecer?token=..."
```

### API

Necesita **Docker Desktop**, que levanta PostgreSQL 16 con PostGIS.

1. Copia `api-ktor/.env.example` como `api-ktor/.env`, que está fuera de git, y completa la contraseña de la base de datos, la clave del JWT y los correos de moderador. Ningún valor puede llevar `$`, porque Docker Compose lo toma como una variable. Las demás variables son opcionales:
   - `DEV_MAILBOX=true` abre `/v1/dev/mailbox`, para leer los enlaces del buzón de desarrollo. Lo usan los botones de «Correo de prueba» de la app. Nunca va en producción.
   - `SENDGRID_API_KEY` y `MAIL_FROM` envían los correos de verdad.
   - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` y `CLOUDINARY_API_SECRET`, las tres juntas, suben las fotos a Cloudinary.
   - `OPENROUTER_API_KEY` sugiere la categoría con IA en OpenRouter. `OPENROUTER_MODEL` cambia el modelo; sin él, `deepseek/deepseek-chat`.
   - `PUBLIC_BASE_URL` es el comienzo de las direcciones de la carpeta local. Por omisión, `http://localhost:8080`, que también sirve en el teléfono con `adb reverse`. Si la app usa la IP del equipo, pon esa misma (`http://192.168.x.x:8080`).
2. Levanta la base de datos y la API:

```bash
cd api-ktor
docker compose up -d   # PostGIS en localhost:5432
./gradlew run          # http://localhost:8080/health; aplica las migraciones al arrancar
```

Las pruebas crean su propia base de datos con Testcontainers. Sin Docker, las que la necesitan se omiten en el equipo, pero en el CI siempre corren:

```bash
./gradlew test buildFatJar
```

La imagen de la API, con Docker:

```bash
docker build -t exploracity-api api-ktor
docker run -p 8080:8080 exploracity-api
```

## Integración continua

GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) se ejecuta en cada pull request y en cada push a `main`:

- **app-android**: `./gradlew assembleDebug testDebugUnitTest`
- **api-ktor**: `./gradlew test buildFatJar`

[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) despliega la API en Cloud Run con cada push a `main` que cambie `api-ktor/`: pruebas, imagen en Artifact Registry, Cloud Run y una comprobación de `/health`. Entra a Google Cloud con Workload Identity Federation, sin claves guardadas en GitHub, y no corre hasta que el repositorio tenga las variables de [`docs/despliegue.md`](docs/despliegue.md).

`main` está protegida. Todo cambio entra por pull request con el CI en verde.

## Contribuir

El flujo de ramas, el formato de los commits y la lista de revisión antes de abrir un pull request están en [CONTRIBUTING.md](CONTRIBUTING.md).

## Recursos de terceros

Las fuentes Outfit y Manrope (SIL Open Font License 1.1) y los iconos Material Symbols Rounded (Apache 2.0) se detallan en [app-android/THIRD_PARTY_NOTICES.md](app-android/THIRD_PARTY_NOTICES.md).
