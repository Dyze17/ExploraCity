# Contrato de la API

La app Android y `api-ktor` se hablan por HTTPS con JSON. Este documento es el contrato entre las dos partes y crece con cada área.

En `ejemplos/` hay respuestas reales. Las pruebas de `api-ktor` comprueban que la API responde exactamente eso, y las de la app comprueban que lo lee bien. Si una de las dos cambia el formato, falla alguna prueba.

## Convenciones

- **Versión en la ruta:** todo cuelga de `/v1` (ADR-02). Un cambio que rompa la app irá a `/v2`.
- **Nombres en inglés:** rutas, campos y códigos de error van en inglés, como el código. Los valores de los enums son los de la app (`GASTRONOMY`, `PENDING`…).
- **Fechas:** ISO 8601 en UTC (`2026-10-03T15:00:00Z`).
- **Coordenadas:** `{ "latitude": 4.5339, "longitude": -75.6811 }`, en grados.
- **Campos desconocidos:** la app los ignora, así un campo nuevo no la rompe.

## Autenticación (ADR-06)

- Las rutas privadas piden `Authorization: Bearer <token de acceso>`.
- El token de acceso es un JWT que dura **15 minutos**. Lleva la persona en `sub` y su rol en `role` (`USER` o `MODERATOR`).
- Sin token, o con uno vencido o inválido, la respuesta es `401` con `WWW-Authenticate: Bearer realm="exploracity"`. Esa es la señal para que la app pida otro con su token de renovación, que dura 30 días.
- Las rutas de Moderación responden `403` a quien no tiene el rol de moderador.
- Ese rol lo dan solo los correos de `MODERATOR_EMAILS`. La API lo revisa al arrancar, y también quita el rol a quien ya no está en la lista.

## Errores

Todo error llega con el mismo cuerpo, sin trazas. La app traduce el código a su propio texto:

```json
{ "code": "unauthorized" }
```

| Código | HTTP | Cuándo |
|---|---|---|
| `bad_request` | 400 | El JSON está mal formado o no es el esperado |
| `unauthorized` | 401 | Falta el token de acceso, venció o no es válido |
| `forbidden` | 403 | La ruta es de Moderación y la persona no tiene el rol |
| `not_found` | 404 | La ruta no existe |
| `method_not_allowed` | 405 | La ruta no acepta ese método |
| `unsupported_media_type` | 415 | El cuerpo no es JSON |
| `internal_error` | 500 | Fallo inesperado; el detalle queda en el registro del servidor |

Cada área agrega sus propios códigos, por ejemplo `email_taken` (409).

## Endpoints

| Método y ruta | Acceso | Respuesta | Ejemplo |
|---|---|---|---|
| `GET /health` | Pública | `{ "status": "ok" }`, para Cloud Run | — |
| `GET /v1/city` | Pública | La ciudad que atiende la app: nombre, centro del mapa y límites de la búsqueda por dirección (17.b) | [`city.json`](ejemplos/city.json) |
