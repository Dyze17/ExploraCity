# ExploraCity

App Android de **turismo colaborativo**. Turistas y residentes **descubren, publican y validan puntos de interés (POI)** de su ciudad: cafés, museos, senderos, miradores, sitios históricos… Cada lugar que publica la comunidad pasa por un **moderador**, que lo verifica, lo rechaza con un motivo o lo da por finalizado. Así el mapa de la ciudad se mantiene confiable.

Es un proyecto de la Universidad del Quindío. Es un monorepo con la app Android (Kotlin + Jetpack Compose) y su API REST (Ktor).

## Funcionalidad

### Explorar
- **Feed en lista y en mapa** (Google Maps), con marcadores agrupados y una tarjeta inferior para el lugar seleccionado.
- **Búsqueda y filtros**: categorías, «Cercanos» (5 km, con permiso de ubicación) o toda la ciudad, y «Solo verificados».
- **Detalle del lugar**: fotos, descripción, horario, rango de precio, ubicación y estado de verificación.
- **Interacción**: voto «Es importante», «Marcar como visitado» con una experiencia opcional, y comentarios de hasta 300 caracteres. Los comentarios se envían de forma optimista y se pueden reintentar.
- **Perfil público** de otras personas, con la opción de reportarlo.

### Publicar
- **Formulario en 5 pasos**: título y descripción, categoría con **sugerencia de IA**, ubicación en el mapa, horario y rango de precio, y de 1 a 5 fotos.
- **Detección de posibles duplicados**: al confirmar la ubicación se buscan lugares con un nombre parecido en un radio de 50 m. La persona puede abrir el existente o confirmar que es un lugar distinto. En ese caso el moderador ve la marca «posible duplicado».
- **Borrador persistente**: sobrevive a la rotación, al cierre de la app y a la falta de conexión.
- **Mis publicaciones**, con filtros por estado: editar, eliminar, ver el motivo de un rechazo y «Corregir y reenviar».

### Social y reputación
- **Notificaciones** dentro de la app (sin push): comentarios, verificación, rechazo, rechazo por duplicado, logros y publicaciones finalizadas.
- **Perfil propio**, con puntos, **niveles** (Turista, Explorador, Aventurero y Embajador Local) e **insignias** con su progreso.

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
- **Arranque**: el splash decide por dónde se entra. La primera vez va al onboarding de tres paneles, después al inicio de sesión, y con una sesión guardada entra directo al feed. Si la red falla, se puede seguir sin conexión con lo guardado.
- **Inicio de sesión**: valida el correo y la contraseña. Ante un error no revela cuál de los dos datos falló, y sin conexión el botón queda deshabilitado con su explicación.
- **Registro**: pide la autorización de tratamiento de datos (**Ley 1581 de 2012**), que nunca viene marcada. La cuenta se crea aunque falle el correo de bienvenida.
- **Recuperación de contraseña**: llega un enlace por correo que vence a los 30 minutos, y se puede reenviar después de una cuenta regresiva de 60 s. La app nunca revela si un correo tiene cuenta.
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

Las pantallas se construyen primero con **datos de ejemplo**, a través de repositorios `Fake*` y `Sample*` que implementan las mismas interfaces que usará la API.

**Todas las pantallas del diseño están implementadas**, y no queda ninguna provisional.

El inicio de sesión, el registro y la recuperación de contraseña ya funcionan, pero contra un servidor falso (`FakeAuthRepository`). La sesión y su rol se guardan en DataStore. Cualquier cuenta, también una recién creada, ve los datos de ejemplo de Ana Ríos.

Las decisiones del moderador cambian esos datos mientras la app está abierta. Por ejemplo, una publicación verificada aparece en el feed, y a quien la publicó le llega el aviso.

El siguiente paso es conectar la app a `api-ktor` con autenticación JWT. En ese mismo paso se retirarán todos los datos de ejemplo.

| Área | Pantallas (numeración del diseño) | Estado |
|---|---|---|
| Acceso (splash, onboarding, inicio de sesión, registro y recuperación) | 1–6, 6C | ✅ Implementada |
| Explorar | 7–14, 31 | ✅ Implementada |
| Publicar y mis publicaciones | 15–24 | ✅ Implementada |
| Notificaciones, perfil e insignias | 25–27 | ✅ Implementada |
| Editar perfil, ajustes, documentos legales y eliminar cuenta | 28–30, 29A, 4A | ✅ Implementada |
| Cambiar correo (Ajustes › Cuenta) | sin número | ✅ Implementada |
| Moderación y «Resueltas» | 32–37, 33A | ✅ Implementada |
| API (`api-ktor`) | — | ⏳ Esqueleto: solo `/health` y la migración de extensiones |

Las direcciones del mapa ya usan el Geocoder real de Android, detrás de `AddressResolver`, para que el backend pueda reemplazarlo después.

## Arquitectura

La arquitectura sigue el documento de arquitectura del software (modelo C4, v1.1) y sus decisiones arquitectónicas (ADR).

```
.
├── app-android/          App Android (proyecto Gradle, módulo app/)
├── api-ktor/             API REST en Ktor
├── docs/                 Modelo C4, arquitectura y decisiones arquitectónicas
├── .github/              CI (GitHub Actions) y plantilla de pull request
└── CONTRIBUTING.md       Flujo de ramas, commits y pull requests
```

### App Android (`app-android/`)

Paquete `co.edu.uniquindio.exploracity`, organizado por capas:

| Paquete | Contenido |
|---|---|
| `ui/` | Pantallas Compose, componentes del sistema de diseño y tema Material 3 |
| `navigation/` | Navegación con rutas tipadas y una barra inferior según el rol |
| `viewmodel/` | Estado de la UI con `StateFlow` |
| `data/remote` | Cliente de la API (Ktor Client, DTO) |
| `data/local` | Sesión y preferencias en DataStore, caché sin conexión en Room |
| `data/repository` | Repositorios (hoy `Fake*` con datos de ejemplo) |
| `data/sync` | Cola de envío sin conexión con WorkManager |
| `data/connectivity`, `data/location`, `data/photos` | Estado de la red, ubicación y geocodificación, fotos |
| `domain/` | Modelos del dominio |
| `util/` | Formateadores y permisos |

**Stack:** Kotlin 2.4 · Jetpack Compose (BOM 2026.09) · Material 3 · Navigation Compose · Lifecycle/ViewModel · Coroutines · Ktor Client · kotlinx.serialization · DataStore · Room · WorkManager · Maps Compose. Usa `minSdk 28` y `targetSdk`/`compileSdk 37`.

**Pruebas:** JUnit, kotlinx-coroutines-test, Robolectric y Compose UI Test. Entre ellas está `ThemeContrastTest`, que exige el contraste mínimo de cada par de colores del tema.

### API (`api-ktor/`)

Paquetes `routes/`, `service/`, `repository/`, `model/`, `integration/`, `config/` y `plugins/`.

**Stack:** Ktor 3.6 (Netty) · autenticación JWT · Exposed + HikariCP · PostgreSQL con **PostGIS** y **pg_trgm** para detectar duplicados · Flyway · BCrypt. Las integraciones con Cloudinary (fotos), el servicio de correo y OpenRouter (sugerencia de categoría con IA) se hacen con Ktor Client.

La IA y la detección de duplicados se ejecutan en el backend, así que la clave de la IA nunca viaja en la app. El despliegue previsto es una imagen Docker en **Google Cloud Run**.

## Cómo ejecutar

### Requisitos
- **JDK 25**
- **Android Studio 2026.1.3** o posterior (el proyecto usa AGP 9.4) y el **Android SDK 37**
- Una clave de **Google Maps** para ver el mapa. Sin ella, la pantalla del mapa muestra un aviso de desarrollo.

### App Android

Agrega la clave de Maps a `app-android/local.properties`. Ese archivo está fuera de git:

```properties
MAPS_API_KEY=tu_clave
```

Compila, prueba y revisa:

```bash
cd app-android
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

En Windows usa `gradlew.bat`. También puedes abrir `app-android/` en Android Studio y ejecutar la configuración `app`.

#### Herramientas de desarrollo

Estas opciones solo aparecen en las compilaciones de depuración:

- **Cuentas de prueba**: en el inicio de sesión, rellenan la cuenta de Ana o la del moderador. Para esas cuentas vale cualquier contraseña que cumpla las reglas. Con la del moderador aparece la pestaña «Moderación».
- **Correo de prueba**: aparece en «Revisa tu correo» (6.a) y en «Confirma tu correo nuevo». «Abrir el enlace del correo» hace lo que haría el enlace real: abre «Nueva contraseña» (6.b) o confirma el correo nuevo. «Abrir un enlace vencido» lleva a la pantalla de enlace vencido.
- **Muestrario del sistema de diseño**: un acceso desde Ajustes.

Las cuentas creadas en el registro viven en la memoria del servidor falso y se pierden al cerrar la app.

### API

```bash
cd api-ktor
./gradlew run          # http://localhost:8080/health
./gradlew test buildFatJar
```

Con Docker:

```bash
docker build -t exploracity-api api-ktor
docker run -p 8080:8080 exploracity-api
```

## Integración continua

GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) se ejecuta en cada pull request y en cada push a `main`:

- **app-android**: `./gradlew assembleDebug testDebugUnitTest`
- **api-ktor**: `./gradlew test buildFatJar`

`main` está protegida. Todo cambio entra por pull request con el CI en verde.

## Contribuir

El flujo de ramas, el formato de los commits y la lista de revisión antes de abrir un pull request están en [CONTRIBUTING.md](CONTRIBUTING.md).

## Recursos de terceros

Las fuentes Outfit y Manrope (SIL Open Font License 1.1) y los iconos Material Symbols Rounded (Apache 2.0) se detallan en [app-android/THIRD_PARTY_NOTICES.md](app-android/THIRD_PARTY_NOTICES.md).
