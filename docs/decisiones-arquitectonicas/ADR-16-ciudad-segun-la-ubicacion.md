# ADR-16: La ciudad que se explora sale de la ubicación

**Estado:** aceptada (octubre de 2026, versión 1.2.0; ampliada en la 1.3.0, abajo). Amplía la decisión F2 del plan de conexión con la API: la ciudad de la configuración (`/v1/city`) sigue siendo la de partida.

## Decisión

«Estás explorando …» (7) muestra el municipio donde está la persona, y «Toda la ciudad» (9) trae solo los lugares de ese municipio.

- **Quién decide el municipio:** el Geocoder de Android, el mismo que ya da la dirección del pin (17).
  - El municipio es `locality` y, si falta, `subAdminArea`.
  - El departamento es `adminArea`. Separa municipios que se llaman igual: hay una Armenia en el Quindío y otra en Antioquia.
  - El Geocoder se consulta siempre en español de Colombia, sea cual sea el idioma de la app, porque lo que devuelve se guarda.
- **Cada lugar guarda su municipio:** la app manda `municipality` y `region` con la publicación (V9).
  - Al editar sin mover el pin, se quedan los que tenía.
  - Los lugares sin municipio cuentan como la ciudad de la configuración: los de antes de la 1.2.0 y los publicados sin dirección.
- **El filtro:** `GET /v1/places?municipality=…&region=…` compara sin mayúsculas ni tildes, con `search_text()` de V5.
  - Sin `municipality` salen todos, como en la 1.1.0.
  - El mapa (8) no lo usa: muestra el área visible.
- **En el teléfono:**
  - Con el permiso de ubicación, la app pregunta el municipio al abrir Explorar y al volver a ella. Guarda el último para abrir sin conexión.
  - Sin permiso, o si el Geocoder no responde, explora la ciudad de `/v1/city`.
  - El permiso se pide una vez, la primera vez que se abre Explorar. Después, tocar el nombre de la ciudad lo pide otra vez. Esto cambia la decisión del 24-09-2026, que lo pedía solo al aplicar «Cercanos».

## Contexto

Hasta la 1.1.0, la ciudad era una sola, fija en la configuración de la API (Armenia), y «Toda la ciudad» no filtraba nada: traía todos los lugares, del más cercano al más lejano. Quien abría la app en Calarcá leía «Estás explorando Armenia».

## Alternativas

- **Solo cambiar el nombre:** la lista seguiría trayendo los lugares de otros municipios bajo «N lugares en Calarcá». Daniel prefirió filtrar.
- **Polígonos de municipios en PostGIS (MGN del DANE):** sería exacto y no dependería del teléfono, pero hay que cargar y mantener la cartografía de los 1.100 municipios. Queda como mejora si los nombres del Geocoder dan problemas.
- **Geocoding API de Google desde el backend:** necesita otra clave con facturación, y el Geocoder del teléfono ya da lo mismo sin costo.
- **Un radio alrededor de la persona:** no corresponde a ninguna ciudad, y para eso ya existe «Cercanos».

## Justificación

- No agrega servicios ni claves: el Geocoder de Android ya se usa en el formulario (opción A de Daniel, 2026-09-26).
- La app de antes sigue funcionando: los campos y el filtro son opcionales.

## Consecuencias

- La calidad del filtro depende de los nombres del Geocoder. Si escribe un municipio de dos maneras («Bogotá» y «Bogotá, D.C.»), son dos municipios distintos.
- Un lugar publicado sin conexión, o donde el Geocoder no respondió, queda en la ciudad de la configuración aunque esté en otro municipio. El moderador puede verlo en la dirección.
- La búsqueda por dirección del formulario (17.b) busca a unos 11 km del pin o, si aún no está, de la persona. Solo sin ninguno de los dos usa los límites de la ciudad de la configuración.

## Cambio en la 1.3.0: elegir la ciudad a mano

Fuera de Armenia, caer en la ciudad de la configuración cuando no hay ubicación mostraba lugares que no correspondían. Desde la 1.3.0:

- Tocar el nombre de la ciudad abre «Elegir ciudad»:
  - «Usar mi ubicación»;
  - una búsqueda de cualquier ciudad o pueblo con el Geocoder del teléfono, en cualquier país;
  - las ciudades que ya tienen lugares (`GET /v1/places/towns`), con cuántos lugares tiene cada una.
- La ciudad elegida se guarda y manda hasta volver a «Usar mi ubicación». Las distancias se miden desde su centro, y el mapa y el pin del formulario empiezan ahí.
- Sin ubicación y sin ciudad elegida, «Toda la ciudad» pide elegir una en vez de mostrar la de `/v1/city`. Si se niega el permiso, la hoja se abre sola.
- La ciudad de la configuración queda solo para los lugares sin municipio y para abrir el mapa cuando no hay nada más.
