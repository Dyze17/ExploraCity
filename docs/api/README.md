# Contrato de la API

La app Android y `api-ktor` se hablan por HTTPS con JSON. Este documento es el contrato entre las dos partes y crece con cada área.

En `ejemplos/` hay respuestas reales. Las pruebas de `api-ktor` comprueban que la API responde exactamente eso, y las de la app comprueban que lo lee bien. Si una de las dos cambia el formato, falla alguna prueba. Los tokens y los ids que genera la base de datos cambian en cada respuesta: las pruebas solo exigen que estén.

## Convenciones

- **Versión en la ruta:** todo cuelga de `/v1` (ADR-02). Un cambio que rompa la app irá a `/v2`.
- **Nombres en inglés:** rutas, campos y códigos de error van en inglés, como el código. Los valores de los enums son los de la app (`GASTRONOMY`, `PENDING`…).
- **Fechas:** ISO 8601 en UTC (`2026-10-03T15:00:00Z`).
- **Coordenadas:** `{ "latitude": 4.5339, "longitude": -75.6811 }`, en grados.
- **Campos desconocidos:** la app los ignora, así un campo nuevo no la rompe.
- **Campos vacíos:** un campo opcional sin valor no viene en la respuesta (no llega como `null`).
- **Correos:** la API los guarda y los compara sin espacios alrededor y en minúscula.

## Autenticación (ADR-06)

- Las rutas privadas piden `Authorization: Bearer <token de acceso>`.
- El token de acceso es un JWT que dura **15 minutos**. Lleva la persona en `sub` y su rol en `role` (`USER` o `MODERATOR`).
- Sin token, o con uno vencido o inválido, la respuesta es `401` con `WWW-Authenticate: Bearer realm="exploracity"`. Esa es la señal para que la app pida otro con su token de renovación.
- El token de renovación dura **30 días** y sirve una sola vez: cada renovación entrega un par nuevo. Si llega uno que ya se cambió, alguien copió la sesión y se cierran todas las de la cuenta.
- Cerrar sesión, crear una contraseña nueva o eliminar la cuenta revocan los tokens de renovación. En la base de datos solo queda su hash.
- Las rutas de Moderación responden `403` a quien no tiene el rol de moderador.
- Ese rol lo dan solo los correos de `MODERATOR_EMAILS`. La API lo revisa al arrancar, y también quita el rol a quien ya no está en la lista. Una cuenta nueva toma su rol de esa lista, también si se crea con Google.
- Además de correo y contraseña, se puede entrar con Google (ADR-15, [más abajo](#entrar-con-google-adr-15)). La sesión que se abre es la misma.

### Límite de intentos (A1)

- Tras **5 fallos en 15 minutos** con el mismo correo, el inicio de sesión responde `429 too_many_attempts` durante **15 minutos**, aunque la contraseña sea la correcta. El encabezado `Retry-After` dice cuántos segundos faltan.
- Los fallos se cuentan en una ventana que empieza con el primero. Entrar bien o crear una contraseña nueva con el enlace del correo los borra.
- Cuentan también los correos sin cuenta: el bloqueo no revela cuáles existen.

## Errores

Todo error llega con el mismo cuerpo, sin trazas. La app traduce el código a su propio texto:

```json
{ "code": "unauthorized" }
```

Un enlace vencido trae además el correo al que se envió, para pedir otro con el correo ya escrito (6C): [`link-expired.json`](ejemplos/link-expired.json). Al entrar con Google, `link_required` trae el correo y `registration_required` trae el correo y el nombre de la cuenta de Google.

| Código | HTTP | Cuándo |
|---|---|---|
| `bad_request` | 400 | El JSON está mal formado o no es el esperado |
| `invalid_name` | 400 | El nombre no tiene entre 2 y 40 caracteres (4 y 28) |
| `invalid_email` | 400 | El correo no tiene un formato válido |
| `weak_password` | 400 | La contraseña nueva no tiene 8 caracteres con una letra y un número (4 y 6.b) |
| `same_email` | 400 | El correo nuevo es el mismo de la cuenta |
| `bio_too_long` | 400 | «Sobre mí» pasa de 150 caracteres (28) |
| `photo_missing` | 400 | El multipart no trae una foto |
| `cannot_report_self` | 400 | La persona intenta reportar su propio perfil |
| `invalid_query` | 400 | Un criterio de búsqueda no se entiende (categoría, alcance, punto, área, página) |
| `invalid_comment` | 400 | El comentario queda vacío o pasa de 300 caracteres (14) |
| `invalid_experience` | 400 | La experiencia de «Visitado» pasa de 300 caracteres (14.b) |
| `invalid_cursor` | 400 | El cursor de los comentarios no es uno que dio la API |
| `invalid_publication` | 400 | Un dato del formulario está fuera de sus reglas (título, descripción, ubicación, horario, nota de parecidos) |
| `invalid_photos` | 400 | Las fotos no son de 1 a 5, se repiten, no son de la persona o ya están en otra publicación |
| `invalid_decision` | 400 | Una decisión de moderación está incompleta (falta el original de un duplicado, el detalle de «Otro» o el motivo de volver a pendiente) |
| `unauthorized` | 401 | Falta el token de acceso, venció, no es válido o la cuenta ya no existe |
| `invalid_credentials` | 401 | El correo o la contraseña no coinciden al iniciar sesión. No dice cuál de los dos |
| `invalid_credentials` | 403 | La contraseña actual no coincide al cambiar el correo; la sesión sigue valiendo |
| `invalid_google_token` | 401 | El ID token de Google no sirve: firma, emisor, audiencia, vencimiento o correo sin verificar |
| `invalid_refresh_token` | 401 | El token de renovación no existe, venció, se revocó o ya se usó |
| `forbidden` | 403 | La ruta es de Moderación y la persona no tiene el rol |
| `own_publication` | 403 | Un moderador intenta decidir sobre su propia publicación (F1) |
| `not_found` | 404 | La ruta no existe |
| `user_not_found` | 404 | La persona del perfil o del reporte no existe |
| `place_not_found` | 404 | El lugar no existe o no es público (pendiente o rechazado) |
| `notification_not_found` | 404 | El aviso no existe o es de otra persona |
| `publication_not_found` | 404 | La publicación no existe, es de otra persona o ya no está para moderar |
| `registration_required` | 404 | Entrar con Google: la cuenta de Google es nueva. Trae `email` y `name` para el registro en modo Google (C1) |
| `method_not_allowed` | 405 | La ruta no acepta ese método |
| `email_taken` | 409 | Ya hay una cuenta con ese correo. Con Google: el correo ya tiene otra cuenta de Google vinculada |
| `link_required` | 409 | Entrar con Google: el correo ya tiene una cuenta con contraseña. Se vincula con esa contraseña (B1). Trae `email` |
| `google_account_in_use` | 409 | La cuenta de Google ya está vinculada a otra cuenta |
| `password_required` | 409 | Se pide cambiar el correo de una cuenta sin contraseña (solo con Google, D1) |
| `no_pending_email` | 409 | Se pide reenviar el enlace sin un cambio de correo pendiente |
| `cannot_resubmit` | 409 | Se reenvía una publicación que no está rechazada o que el moderador no permitió corregir |
| `not_editable` | 409 | Se edita una publicación rechazada o finalizada (solo se editan pendientes y verificadas) |
| `too_many_photos` | 409 | La publicación ya tiene 5 fotos |
| `already_reviewed` | 409 | Otra persona ya decidió esa pendiente |
| `state_changed` | 409 | La publicación ya no está en el estado que vio el moderador |
| `cannot_reopen` | 409 | Se intenta volver a pendiente un lugar cuya cuenta se eliminó |
| `link_expired` | 410 | El enlace del correo venció, ya se usó o no existe. Trae `email` si se sabe de quién es |
| `photo_too_large` | 413 | La foto pasa de 8 MB |
| `unsupported_media_type` | 415 | El cuerpo no es JSON |
| `unsupported_photo_type` | 415 | La foto no es JPEG, PNG ni WebP; se reconoce por su contenido |
| `too_many_attempts` | 429 | El correo está bloqueado por intentos fallidos; ver `Retry-After` |
| `internal_error` | 500 | Fallo inesperado; el detalle queda en el registro del servidor |
| `photo_upload_failed` | 502 | El almacén de fotos no respondió |
| `suggestion_unavailable` | 503 | La IA no respondió a tiempo: la app sigue con la elección manual (ADR-12) |
| `email_delivery_failed` | 503 | El correo no salió. Al recuperar la contraseña, solo se dice si el correo tiene cuenta (5) |
| `google_sign_in_unavailable` | 503 | La API no tiene `GOOGLE_WEB_CLIENT_ID`: no se puede entrar con Google |

## Endpoints

### Base

| Método y ruta | Acceso | Respuesta | Ejemplo |
|---|---|---|---|
| `GET /health` | Pública | `{ "status": "ok" }`, para Cloud Run | — |
| `GET /v1/city` | Pública | La ciudad que atiende la app: nombre, centro del mapa y límites de la búsqueda por dirección (17.b) | [`city.json`](ejemplos/city.json) |

### Sesión (3 y 4)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/auth/register` | Pública | `{ "name", "email", "password", "residency", "clientId" }` | `201` con la sesión y `welcomeEmailSent`. Si el correo de bienvenida no sale, la cuenta se crea igual y llega `false`. Repetido con el mismo `clientId` y la misma contraseña, `200` con una sesión nueva en esa cuenta y sin `welcomeEmailSent` | [`register.json`](ejemplos/register.json) |
| `POST /v1/auth/login` | Pública | `{ "email", "password" }` | `200` con la sesión | [`session.json`](ejemplos/session.json) |
| `POST /v1/auth/refresh` | Pública | `{ "refreshToken" }` | `200` con una sesión nueva; el token usado deja de servir | [`session.json`](ejemplos/session.json) |
| `POST /v1/auth/logout` | Pública | `{ "refreshToken" }` | `204`, también si el token ya estaba cerrado | — |

La sesión trae los dos tokens, `expiresIn` (segundos del token de acceso), la persona (`userId`, `role`) y su cuenta: `email`, `pendingEmail` con un cambio pedido, y cómo entra (`hasPassword` y `googleLinked`, ADR-15). La app la guarda con la sesión.

`residency` es `RESIDENT` o `VISITOR` («¿Cómo te presentas?»).

`clientId`, opcional, es un UUID que pone la app y repite en cada intento del mismo formulario. Si la respuesta del registro se perdió (la API terminó después de que la app se rindió), repetirlo abre la sesión en vez de responder `409 email_taken`. Con otro `clientId`, sin él o con otra contraseña, el correo ya tiene cuenta: `409 email_taken`.

### Entrar con Google (ADR-15)

La app pide la cuenta con Credential Manager («Sign in with Google») y manda el ID token que recibe de Google. La API comprueba la firma con las claves públicas de Google, el emisor (`accounts.google.com`), la audiencia (el cliente Web, `GOOGLE_WEB_CLIENT_ID`), el vencimiento y que Google haya verificado el correo.

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/auth/google` | Pública | `{ "idToken", "registration": { "name", "residency" } }` | `200` con la sesión si la cuenta de Google ya está vinculada. Si es nueva: sin `registration`, `404 registration_required` con `email` y `name`; con `registration`, `201` con la sesión y `welcomeEmailSent`. Si el correo ya tiene una cuenta con contraseña, `409 link_required` | [`session.json`](ejemplos/session.json), [`register.json`](ejemplos/register.json) |
| `POST /v1/auth/google/link` | Pública | `{ "idToken", "password" }` | `200` con la sesión: la cuenta con contraseña de ese correo queda vinculada a Google. Una contraseña equivocada responde `401 invalid_credentials` y cuenta para el límite de intentos (A1) | [`session.json`](ejemplos/session.json) |

- La cuenta se reconoce por la cuenta de Google (su `sub`), no por el correo: sigue funcionando aunque la persona cambie su correo en la app o en Google.
- **Cuenta nueva (C1):** la app abre el registro (4) en modo Google con el nombre de Google (editable), el correo fijo y sin contraseña. La autorización de la Ley 1581 la pide la app, como en el registro con contraseña. Se crea sin contraseña, con el rol de `MODERATOR_EMAILS` y con el correo de bienvenida.
- **El correo ya tiene cuenta con contraseña (B1):** se vincula solo con esa contraseña, una vez. Si no, quien hubiera registrado ese correo antes con una contraseña conservaría el acceso. Quien no la recuerde, la cambia con «¿Olvidaste tu contraseña?».
- **Sin contraseña (D1):** una cuenta creada con Google no puede iniciar sesión con contraseña ni cambiar el correo (`409 password_required`) hasta que cree una con «¿Olvidaste tu contraseña?». Desde entonces entra con cualquiera de las dos.
- Repetir una petición cuya respuesta se perdió encuentra la cuenta ya creada o vinculada y abre otra sesión.

### Recuperar la contraseña (5, 6 y 6C)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/auth/password/forgot` | Pública | `{ "email" }` | `202` siempre, con o sin cuenta. Si el correo de una cuenta no sale: `503 email_delivery_failed` | — |
| `POST /v1/auth/password/link` | Pública | `{ "token" }` | `200` con el correo y el vencimiento del enlace (6.b) | [`reset-link.json`](ejemplos/reset-link.json) |
| `POST /v1/auth/password/reset` | Pública | `{ "token", "password" }` | `204`. La contraseña cambia, el enlace deja de servir y se cierran todas las sesiones | — |

- El enlace del correo es `exploracity://enlace/restablecer?token=…` en desarrollo. En Cloud Run empieza por `APP_LINK_BASE_URL`, en https (`https://…run.app/enlace/restablecer?token=…`). Vence a los **30 minutos** y sirve una vez.
- `GET /enlace/restablecer` y `GET /enlace/confirmar-correo` (fuera de `/v1`) son la página que se ve si ese enlace se abre en el navegador: un botón «Abrir en ExploraCity» que le pasa el token a la app. Con `ANDROID_CERT_SHA256`, `GET /.well-known/assetlinks.json` declara la app para que Android abra esos enlaces directo (App Links). Ver [`docs/despliegue.md`](../despliegue.md).
- Entre un envío y otro a la misma cuenta hay **60 segundos**. Antes de eso la API responde `202` sin enviar otro: el anterior sigue sirviendo.
- Un enlace vencido o usado responde `410 link_expired` con el correo; uno que no existe, sin correo.

### Cuenta (28, 29, 30 y «Cambiar correo»)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `GET /v1/account` | Token | — | El correo, el correo nuevo pendiente si lo hay, y cómo entra: `hasPassword` y `googleLinked` (ADR-15). Sin contraseña, la app no ofrece «Cambiar correo» (D1) | [`account.json`](ejemplos/account.json) |
| `POST /v1/account/email` | Token | `{ "newEmail", "password" }` | `202` con la cuenta y el correo pendiente. Envía el enlace al correo nuevo; otro pedido reemplaza al anterior | [`account.json`](ejemplos/account.json) |
| `POST /v1/account/email/resend` | Token | — | `202`. Reenvía el enlace; el anterior deja de servir. Antes de 60 s no envía otro | — |
| `POST /v1/account/email/confirm` | Token | `{ "token" }` | `200` con la cuenta: el correo nuevo ya es el de entrar | `{ "email": "ana.nueva@correo.com", "hasPassword": true, "googleLinked": false }` |
| `GET /v1/account/export` | Token | — | «Descargar mis datos»: el archivo JSON con claves en español. El nombre va en `Content-Disposition` (`exploracity-mis-datos-2026-10-03.json`). `cuenta.entraConGoogle` dice si también entra con Google, sin el identificador de Google | [`export.json`](ejemplos/export.json) |
| `DELETE /v1/account` | Token | — | `204`. Elimina la cuenta, todo o nada | — |

- El enlace para confirmar el correo nuevo es `exploracity://enlace/confirmar-correo?token=…`, vence a los **30 minutos** y solo lo confirma la misma cuenta, con la sesión iniciada.
- Al eliminar la cuenta, lo verificado y lo finalizado quedan publicados sin autor, los comentarios quedan como «Usuario eliminado» y los votos como cifras. Se borran las fotos (también del almacén), las publicaciones pendientes y rechazadas, las visitas, los avisos y los datos personales.

### Perfil (26, 28 y 31A)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `GET /v1/profile` | Token | — | El perfil propio: autor con puntos, residencia, ciudad, mes de registro, publicaciones por estado e insignias con su avance | [`profile.json`](ejemplos/profile.json) |
| `PUT /v1/profile` | Token | `{ "name", "bio", "residency" }` | `200` con el perfil. «Sobre mí» vacío se guarda como `null` | [`profile.json`](ejemplos/profile.json) |
| `PUT /v1/profile/photo` | Token | `multipart/form-data` con la foto en una parte de archivo | `200` con el perfil. JPEG, PNG o WebP de hasta 8 MB; la anterior sale del almacén | [`profile.json`](ejemplos/profile.json) |
| `DELETE /v1/profile/photo` | Token | — | `200` con el perfil, sin `photo` | [`profile.json`](ejemplos/profile.json) |
| `POST /v1/users/{id}/reports` | Token | `{ "reason" }` | `204`. Reporte anónimo para la moderación (`IMPERSONATION`, `INAPPROPRIATE_CONTENT` o `SPAM`) | — |

- El avance de cada insignia nunca pasa de su meta. Las verificadas cuentan también si después pasaron a finalizadas.
- Una insignia desbloqueada no se pierde: si la cifra baja después (se quita un voto, por ejemplo), sigue completa.

### Explorar (7, 8, 9, 10, 13 y 31)

| Método y ruta | Acceso | Respuesta | Ejemplo |
|---|---|---|---|
| `GET /v1/places` | Token | Una página del feed (`page` desde 0, `pageSize` de 1 a 50, 20 por omisión), del más cercano al más lejano | [`feed-page.json`](ejemplos/feed-page.json) |
| `GET /v1/places/count` | Token | Cuántos lugares cumplen los criterios: el conteo en vivo de la hoja de filtros (9) | [`count.json`](ejemplos/count.json) |
| `GET /v1/places/map` | Token | Los del área visible (`bounds`), los más cercanos primero, como máximo `limit` (200), y cuántos hay en el área | [`map-area.json`](ejemplos/map-area.json) |
| `GET /v1/places/{id}` | Token | El detalle: fotos, dirección, horario, autor con sus puntos y si quien lo abre ya votó o lo visitó | [`place.json`](ejemplos/place.json) |
| `GET /v1/users/{id}` | Token | El perfil público: sin correo, sus lugares verificados y finalizados con el formato del feed y cuántas insignias tiene | [`public-profile.json`](ejemplos/public-profile.json) |

Los criterios van en la consulta:
- `categories=NATURE,CULTURE`: una o varias categorías; sin ella, todas.
- `scope=NEARBY`: a 5 km o menos del punto. `CITY`, el valor por omisión, es toda la ciudad.
- `verifiedOnly=true`: solo las verificadas. Sin él salen también las finalizadas.
- `q=texto`: parte del título, sin importar mayúsculas ni tildes («cafe» encuentra «Café»).
- `near=4.5339,-75.6811`: la ubicación de la persona. La distancia (`distanceMeters`) y el orden se miden desde ahí; sin ella, desde el centro de la ciudad.
- `bounds=4.50,-75.70,4.56,-75.65`: solo en el mapa, el área visible (sur, oeste, norte, este).

En cada lugar:
- `photo` es la portada, la primera foto.
- `openNow` sale del horario y de la hora de la ciudad; sin horario no viene.
- `summary` es la primera oración de la descripción, para la tarjeta del mapa.
- En el detalle, `photos` trae todas las fotos en orden, y los días del horario van de `MONDAY` a `SUNDAY`. Si la cuenta que publicó el lugar se eliminó, no viene `author` y la app muestra «Usuario eliminado».

### Social (13, 14 y 14.b)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `PUT /v1/places/{id}/vote` | Token | — | El total de votos «Es importante». Votar dos veces no suma | [`vote.json`](ejemplos/vote.json) |
| `DELETE /v1/places/{id}/vote` | Token | — | El total, sin el voto de la persona | [`vote.json`](ejemplos/vote.json) |
| `PUT /v1/places/{id}/visit` | Token | `{ "recommends", "text", "showName" }`, todo opcional | Los puntos que ganó con esta visita | [`visit.json`](ejemplos/visit.json) |
| `GET /v1/places/{id}/comments` | Token | — | Del más reciente al más antiguo (`pageSize` de 1 a 50, 20 por omisión). `nextCursor` pide la página siguiente y no viene en la última | [`comments-page.json`](ejemplos/comments-page.json) |
| `POST /v1/places/{id}/comments` | Token | `{ "text", "clientId" }` | `201` con el comentario. Con un `clientId` que ya llegó responde `200` con el que estaba | [`comment.json`](ejemplos/comment.json) |

- **Puntos (G2):** marcar un lugar como visitado da **+5** la primera vez. Marcarlo otra vez solo cambia la experiencia. Comentar no da puntos.
- **Lo propio no suma (C1):** se puede votar y marcar como visitado un lugar propio. El voto cuenta en el total del lugar, pero no para la insignia «Voz de la comunidad»; la visita da 0 puntos y no cuenta para «Caminante».
- **Comentarios:** tienen de 1 a 300 caracteres, sin los espacios de los extremos. `clientId` es un UUID que pone la app; así, si la cola sin conexión reenvía un comentario, no se duplica. Sin `author`, la cuenta que lo escribió se eliminó («Usuario eliminado»). `mine` dice si lo escribió quien lo lee.
- **Aviso a quien publicó:** cuando otra persona comenta, le llega un aviso `COMMENTED`. No llega por los comentarios propios.

### Avisos (25 y 27)

| Método y ruta | Acceso | Respuesta | Ejemplo |
|---|---|---|---|
| `GET /v1/notifications` | Token | Los últimos 100, del más reciente al más antiguo, y cuántos hay sin leer (`unread`) | [`notifications.json`](ejemplos/notifications.json) |
| `POST /v1/notifications/{id}/read` | Token | `204`. Leer uno ya leído no cambia nada | — |
| `POST /v1/notifications/read-all` | Token | `204` | — |

Cada aviso trae `id`, `type`, `createdAt` y `read`, más los campos que pide su frase:

| `type` | Campos | Lo crea |
|---|---|---|
| `COMMENTED` | `placeId`, `placeTitle`, `authorName` (no viene si la cuenta se eliminó), `excerpt` | Un comentario en un lugar propio |
| `ACHIEVEMENT` | `achievement` (la insignia desbloqueada), `nextBadge` y `remaining` (la bloqueada más cercana y lo que le falta) | Desbloquear una insignia, una sola vez (B1) |
| `VERIFIED` | `placeId`, `placeTitle`, `points` (15 la primera vez, después 0) | Verificar (34) |
| `FINALIZED` | `placeId`, `placeTitle`, `reason` (el motivo en minúscula) | Pasar a finalizada (36) |
| `REJECTED` | `placeId`, `placeTitle`, `reason` (la frase del motivo o, con «Otro», lo que escribió el moderador) | Rechazar (35) |
| `DUPLICATE_REJECTED` | `placeId`, `placeTitle`, `existingPlaceId`, `existingTitle` | Rechazar por duplicado (35) |

Las insignias se revisan después de cada acción que mueve sus cifras: publicar, quedar verificada, comentar, visitar o recibir un voto.

### Publicar (15–24)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/photos` | Token | `multipart/form-data` con la foto | `201` con su id y su dirección. JPEG, PNG o WebP de hasta 8 MB; queda sin publicación hasta el envío | [`photo.json`](ejemplos/photo.json) |
| `POST /v1/places/suggest-category` | Token | `{ "title", "description" }` | La categoría sugerida; sin `category` si no hay una clara. Si la IA falla o tarda, `503 suggestion_unavailable` | [`suggestion.json`](ejemplos/suggestion.json) |
| `GET /v1/places/similar?title&near&excludeId` | Token | — | Hasta 3 lugares a 50 m o menos del pin con un título parecido, del más cercano al más lejano | [`similar-places.json`](ejemplos/similar-places.json) |
| `POST /v1/publications` | Token | La publicación (abajo) | `201` con su id y, si es la primera de la persona, `firstPublicationPoints`. Con un `clientId` que ya llegó, `200` con la que estaba | [`submit.json`](ejemplos/submit.json) |
| `GET /v1/publications` | Token | — | Las propias en cualquier estado, de la más reciente a la más antigua | [`publications.json`](ejemplos/publications.json) |
| `GET /v1/publications/{id}` | Token | — | Una propia. Una rechazada trae el motivo y qué corregir | [`publication-rejected.json`](ejemplos/publication-rejected.json) |
| `PUT /v1/publications/{id}` | Token | Lo editable (abajo) | `200` con la publicación, que vuelve a verificación | Un ítem de [`publications.json`](ejemplos/publications.json) |
| `DELETE /v1/publications/{id}` | Token | — | `204`. La borra con sus fotos (también del almacén), comentarios y votos | — |
| `POST /v1/publications/{id}/photos` | Token | `{ "url" }` | `204`. Una foto que terminó de subir después del envío va al final, hasta 5 | — |

La publicación lleva:
- `title` (5 a 60), `description` (30 a 600), `category`, `categoryOrigin` (`SUGGESTED` o `CHOSEN`) y `location`.
- `address`: la dirección aproximada del pin, opcional.
- `hours`: `{ "days": ["MONDAY", …], "opens": "08:00", "closes": "18:00" }`, con al menos un día y el cierre después de la apertura. Sin él, el lugar no tiene horario.
- `price`, opcional.
- `photos`: las direcciones de 1 a 5 fotos ya subidas, en orden; la primera es la portada.
- `duplicateCheck`: la búsqueda de parecidos que se hizo en el teléfono: `location` (dónde estaba el pin), `similarIds` (los parecidos que la persona dijo que son otro lugar), `note` (hasta 200) y `failed`.
- `clientId` y `resubmitId`, opcionales: con el primero, repetir un envío cuya respuesta se perdió (o uno de la cola) responde el mismo en vez de duplicarlo; el segundo reenvía una rechazada.

Al editar (`PUT`) se manda lo mismo salvo `categoryOrigin`, `clientId` y `resubmitId`. Sin mover el pin y sin `address`, queda la dirección que tenía.

- **Puntos (D1):** la primera publicación de la persona gana **+20** al enviarla. Se pierden si se elimina, o si un moderador la rechaza sin permitir que se reenvíe.
- **Posible duplicado (ADR-14):** con la búsqueda del teléfono para ese mismo pin, los parecidos que la persona dijo que son otro lugar marcan la publicación. Si no hubo búsqueda, falló o el pin se movió después, la API la repite. La advertencia nunca bloquea; el moderador ve la marca.
- **Parecidos (17):** se compara con los lugares públicos y con las pendientes propias, nunca con las pendientes de otras personas.
- **Editar (23):** solo pendientes y verificadas. Una verificada sale del feed y entra a la cola como recién enviada. Las fotos que salen se borran del almacén.
- **Reenviar (24):** solo una rechazada que el moderador permitió corregir. Un duplicado no se reenvía.

### Moderar (32–37)

Solo con el rol de moderador (`403 forbidden` con otro rol). Un moderador nunca ve ni decide sus propias publicaciones (F1): no salen en su cola y decidirlas responde `403 own_publication`.

| Método y ruta | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|
| `GET /v1/moderation/summary` | — | Cuántas esperan y desde hace cuántos días la más antigua (7.c) | [`moderation-summary.json`](ejemplos/moderation-summary.json) |
| `GET /v1/moderation/queue` | — | Las pendientes, de la más antigua a la más reciente, con el historial del autor y, si es posible duplicado, los lugares con que se compara (33A) | [`review-queue.json`](ejemplos/review-queue.json) |
| `GET /v1/moderation/queue/{id}` | — | Una pendiente | Un ítem de [`review-queue.json`](ejemplos/review-queue.json) |
| `POST /v1/moderation/queue/{id}/verify` | `{ "note" }` (hasta 300, interna) | `204`. Entra al feed; el autor recibe el aviso y +15 la primera vez | — |
| `GET /v1/moderation/queue/{id}/duplicate-options` | — | Con qué enlazar el original: los parecidos que trae o, si no, hasta 5 publicados a 500 m o menos | — |
| `POST /v1/moderation/queue/{id}/reject` | `{ "reason", "message", "canResubmit", "originalId" }` | `204`. Con `OTHER`, `message` de 20 a 400; con `DUPLICATE`, `originalId` obligatorio | — |
| `GET /v1/moderation/today` | — | Lo que decidió este moderador desde la medianoche de Armenia (37) | [`moderation-today.json`](ejemplos/moderation-today.json) |
| `GET /v1/moderation/resolved` | — | Lo ya decidido, de lo más reciente a lo más antiguo, con su última decisión | [`resolved.json`](ejemplos/resolved.json) |
| `GET /v1/moderation/resolved/{id}` | — | Una resuelta; `404` si volvió a pendiente | Un ítem de [`resolved.json`](ejemplos/resolved.json) |
| `POST /v1/moderation/resolved/{id}/finalize` | `{ "reason" }` (`CLOSED`, `EVENT_ENDED` o `MERGED`) | `204`. Sigue en el feed con su chip; los puntos se quedan | — |
| `POST /v1/moderation/resolved/{id}/reopen` | `{ "reason" }` (20 a 300, interna) | `204`. Sale del feed y entra a la cola como recién enviada | — |

- **Dos moderadores a la vez:** si otra persona ya decidió, la respuesta es `409 already_reviewed`; si la publicación cambió de estado, `409 state_changed`.
- **Qué corregir (E1):** sale del motivo del rechazo. `PHOTO` pide una foto donde se reconozca el lugar y `LOCATION`, el pin sobre la entrada. Los demás motivos llevan solo el mensaje. Sin mensaje del moderador, va el del motivo.
- **Lo que no existe todavía:** el reporte de un lugar. `reportReason` no viene en la revisión.
- **Lugares sin autor:** una pendiente cuya cuenta se eliminó no sale en la cola, y un lugar sin autor no vuelve a pendiente (`409 cannot_reopen`).

### Desarrollo

Solo existen con `DEV_MAILBOX=true` y sin `SENDGRID_API_KEY`: sin SendGrid los correos no salen y quedan en un buzón en memoria. En producción estas rutas no existen (`404`).

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/dev/mailbox/latest-link` | Pública | `{ "email", "purpose" }` | El token del último enlace que llegó a ese correo y aún sirve; `404 link_not_found` si no hay | [`dev-link.json`](ejemplos/dev-link.json) |
| `POST /v1/dev/mailbox/expired-link` | Pública | `{ "email", "purpose" }` | Un enlace ya vencido para probar 6C; `404 account_not_found` si no hay cuenta | [`dev-link.json`](ejemplos/dev-link.json) |

`purpose` es `PASSWORD_RESET` (el correo es el de la cuenta) o `EMAIL_CHANGE` (el correo es el nuevo, pendiente).
