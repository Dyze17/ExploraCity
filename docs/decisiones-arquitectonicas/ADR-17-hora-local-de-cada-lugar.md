# ADR-17: La hora local de cada lugar

**Estado:** aceptada (octubre de 2026, versión 1.3.0). Complementa ADR-16: con lugares fuera de Colombia, la hora de la ciudad de la configuración ya no sirve para todos.

## Decisión

«Abierto ahora» (7, 8 y 13) se calcula con la hora del lugar, no con la de la ciudad de la configuración.

- **Cada lugar guarda su zona horaria** (`places.time_zone`, V12), por ejemplo `America/Bogota` o `Europe/Madrid`.
  - La API la calcula desde las coordenadas del pin al publicar y al mover el pin.
  - Sin zona, el lugar usa la de la ciudad de la configuración, como antes.
- **La zona sale de Timeshape** (`net.iakovlev:timeshape`), una librería de Java:
  - trae dentro del jar los límites de las zonas de todo el mundo, también las del mar (timezone-boundary-builder);
  - responde sin red y sin servicios externos.
- **Las zonas se cargan la primera vez que hacen falta**, no en cada arranque:
  - al publicar o mover un pin;
  - al arrancar, solo si hay lugares sin zona.
  - Una vez cargadas se quedan en memoria mientras viva la instancia.
- **Los lugares de antes de la 1.3.0** reciben su zona al arrancar la API, sin demorar el arranque. Si las zonas no cargan, siguen con la hora de la ciudad y se vuelve a intentar en el siguiente arranque.

## Contexto

Desde la 1.3.0 se puede elegir cualquier ciudad (ADR-16) y publicar en ella. Un café en Madrid con horario de 16:00 a 20:00 salía cerrado a las 17:00 de Madrid, porque la API comparaba con la hora de Armenia, que eran las 10:00.

## Alternativas

- **La zona del teléfono de quien publica:** quien publica de vuelta en casa, o un turista con el teléfono en la hora de su país, dejaría una zona equivocada.
- **Una API de zonas (Google Time Zone API y similares):** cobra por consulta, necesita otra clave y falla si el servicio no responde.
- **Desplazamientos fijos (UTC−5):** no siguen el horario de verano de Estados Unidos o Europa.

## Justificación

- La zona depende solo de dónde está el pin, que la API ya valida.
- Las zonas de Java (`ZoneId`) aplican el horario de verano de cada país.
- La app no cambia: `openNow` ya venía calculado por la API.

## Consecuencias

- **Memoria:** las zonas del mundo ocupan unos 210 MB. En Cloud Run, con 1 GiB y la JVM al 75 %, quedan unos 550 MB para el resto. Por eso se cargan solo cuando hacen falta.
- **Tiempo:**
  - cargarlas tarda 1,4 s en el equipo de desarrollo, y algo más con 1 vCPU;
  - la primera publicación tras un arranque en frío espera esa carga;
  - el resto de la API no espera.
- **El jar crece unos 24 MB**, por los datos de Timeshape.
- **Actualizar los datos:** cuando un país cambia sus zonas, basta con subir la versión de Timeshape. Los lugares ya guardados conservan su zona hasta que se mueva el pin.
