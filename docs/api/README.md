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
- Ese rol lo dan solo los correos de `MODERATOR_EMAILS`. La API lo revisa al arrancar, y también quita el rol a quien ya no está en la lista. Una cuenta nueva toma su rol de esa lista.

### Límite de intentos (A1)

- Tras **5 fallos en 15 minutos** con el mismo correo, el inicio de sesión responde `429 too_many_attempts` durante **15 minutos**, aunque la contraseña sea la correcta. El encabezado `Retry-After` dice cuántos segundos faltan.
- Los fallos se cuentan en una ventana que empieza con el primero. Entrar bien o crear una contraseña nueva con el enlace del correo los borra.
- Cuentan también los correos sin cuenta: el bloqueo no revela cuáles existen.

## Errores

Todo error llega con el mismo cuerpo, sin trazas. La app traduce el código a su propio texto:

```json
{ "code": "unauthorized" }
```

Un enlace vencido trae además el correo al que se envió, para pedir otro con el correo ya escrito (6C): [`link-expired.json`](ejemplos/link-expired.json).

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
| `unauthorized` | 401 | Falta el token de acceso, venció, no es válido o la cuenta ya no existe |
| `invalid_credentials` | 401 | El correo o la contraseña no coinciden al iniciar sesión. No dice cuál de los dos |
| `invalid_credentials` | 403 | La contraseña actual no coincide al cambiar el correo; la sesión sigue valiendo |
| `invalid_refresh_token` | 401 | El token de renovación no existe, venció, se revocó o ya se usó |
| `forbidden` | 403 | La ruta es de Moderación y la persona no tiene el rol |
| `not_found` | 404 | La ruta no existe |
| `user_not_found` | 404 | La persona del perfil o del reporte no existe |
| `place_not_found` | 404 | El lugar no existe o no es público (pendiente o rechazado) |
| `notification_not_found` | 404 | El aviso no existe o es de otra persona |
| `method_not_allowed` | 405 | La ruta no acepta ese método |
| `email_taken` | 409 | Ya hay una cuenta con ese correo |
| `no_pending_email` | 409 | Se pide reenviar el enlace sin un cambio de correo pendiente |
| `link_expired` | 410 | El enlace del correo venció, ya se usó o no existe. Trae `email` si se sabe de quién es |
| `photo_too_large` | 413 | La foto pasa de 8 MB |
| `unsupported_media_type` | 415 | El cuerpo no es JSON |
| `unsupported_photo_type` | 415 | La foto no es JPEG, PNG ni WebP; se reconoce por su contenido |
| `too_many_attempts` | 429 | El correo está bloqueado por intentos fallidos; ver `Retry-After` |
| `internal_error` | 500 | Fallo inesperado; el detalle queda en el registro del servidor |
| `photo_upload_failed` | 502 | El almacén de fotos no respondió |
| `email_delivery_failed` | 503 | El correo no salió. Al recuperar la contraseña, solo se dice si el correo tiene cuenta (5) |

## Endpoints

### Base

| Método y ruta | Acceso | Respuesta | Ejemplo |
|---|---|---|---|
| `GET /health` | Pública | `{ "status": "ok" }`, para Cloud Run | — |
| `GET /v1/city` | Pública | La ciudad que atiende la app: nombre, centro del mapa y límites de la búsqueda por dirección (17.b) | [`city.json`](ejemplos/city.json) |

### Sesión (3 y 4)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/auth/register` | Pública | `{ "name", "email", "password", "residency" }` | `201` con la sesión y `welcomeEmailSent`. Si el correo de bienvenida no sale, la cuenta se crea igual y llega `false` | [`register.json`](ejemplos/register.json) |
| `POST /v1/auth/login` | Pública | `{ "email", "password" }` | `200` con la sesión | [`session.json`](ejemplos/session.json) |
| `POST /v1/auth/refresh` | Pública | `{ "refreshToken" }` | `200` con una sesión nueva; el token usado deja de servir | [`session.json`](ejemplos/session.json) |
| `POST /v1/auth/logout` | Pública | `{ "refreshToken" }` | `204`, también si el token ya estaba cerrado | — |

La sesión trae los dos tokens, `expiresIn` (segundos del token de acceso), la persona (`userId`, `role`) y su cuenta (`email` y, con un cambio pedido, `pendingEmail`). La app la guarda con la sesión.

`residency` es `RESIDENT` o `VISITOR` («¿Cómo te presentas?»).

### Recuperar la contraseña (5, 6 y 6C)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/auth/password/forgot` | Pública | `{ "email" }` | `202` siempre, con o sin cuenta. Si el correo de una cuenta no sale: `503 email_delivery_failed` | — |
| `POST /v1/auth/password/link` | Pública | `{ "token" }` | `200` con el correo y el vencimiento del enlace (6.b) | [`reset-link.json`](ejemplos/reset-link.json) |
| `POST /v1/auth/password/reset` | Pública | `{ "token", "password" }` | `204`. La contraseña cambia, el enlace deja de servir y se cierran todas las sesiones | — |

- El enlace del correo es `exploracity://enlace/restablecer?token=…` (o el comienzo de `APP_LINK_BASE_URL`). Vence a los **30 minutos** y sirve una vez.
- Entre un envío y otro a la misma cuenta hay **60 segundos**. Antes de eso la API responde `202` sin enviar otro: el anterior sigue sirviendo.
- Un enlace vencido o usado responde `410 link_expired` con el correo; uno que no existe, sin correo.

### Cuenta (28, 29, 30 y «Cambiar correo»)

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `GET /v1/account` | Token | — | El correo y, si lo hay, el correo nuevo pendiente | [`account.json`](ejemplos/account.json) |
| `POST /v1/account/email` | Token | `{ "newEmail", "password" }` | `202` con la cuenta y el correo pendiente. Envía el enlace al correo nuevo; otro pedido reemplaza al anterior | [`account.json`](ejemplos/account.json) |
| `POST /v1/account/email/resend` | Token | — | `202`. Reenvía el enlace; el anterior deja de servir. Antes de 60 s no envía otro | — |
| `POST /v1/account/email/confirm` | Token | `{ "token" }` | `200` con la cuenta: el correo nuevo ya es el de entrar | `{ "email": "ana.nueva@correo.com" }` |
| `GET /v1/account/export` | Token | — | «Descargar mis datos»: el archivo JSON con claves en español. El nombre va en `Content-Disposition` (`exploracity-mis-datos-2026-10-03.json`) | [`export.json`](ejemplos/export.json) |
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
| `VERIFIED` | `placeId`, `placeTitle`, `points` | Moderación (parte 4) |
| `FINALIZED` | `placeId`, `placeTitle`, `reason` | Moderación (parte 4) |
| `REJECTED` | `placeId`, `placeTitle`, `reason` | Moderación (parte 4) |
| `DUPLICATE_REJECTED` | `placeId`, `placeTitle`, `existingPlaceId`, `existingTitle` | Moderación (parte 4) |

Las insignias se revisan después de cada acción que mueve sus cifras: comentar, visitar o recibir un voto.

### Desarrollo

Solo existen con `DEV_MAILBOX=true` y sin `SENDGRID_API_KEY`: sin SendGrid los correos no salen y quedan en un buzón en memoria. En producción estas rutas no existen (`404`).

| Método y ruta | Acceso | Cuerpo | Respuesta | Ejemplo |
|---|---|---|---|---|
| `POST /v1/dev/mailbox/latest-link` | Pública | `{ "email", "purpose" }` | El token del último enlace que llegó a ese correo y aún sirve; `404 link_not_found` si no hay | [`dev-link.json`](ejemplos/dev-link.json) |
| `POST /v1/dev/mailbox/expired-link` | Pública | `{ "email", "purpose" }` | Un enlace ya vencido para probar 6C; `404 account_not_found` si no hay cuenta | [`dev-link.json`](ejemplos/dev-link.json) |

`purpose` es `PASSWORD_RESET` (el correo es el de la cuenta) o `EMAIL_CHANGE` (el correo es el nuevo, pendiente).
