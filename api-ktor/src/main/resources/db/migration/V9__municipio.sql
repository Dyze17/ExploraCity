-- ADR-16 · La ciudad o pueblo de cada lugar: «Toda la ciudad» (9) muestra solo los del municipio donde está la persona.
-- Los da el Geocoder del teléfono al poner el pin (17): municipality es el municipio («Calarcá») y region, el
-- departamento («Quindío»), que separa los que se llaman igual (hay una Armenia en el Quindío y otra en Antioquia).
-- NULL = la ciudad de la configuración: así quedan los lugares de antes y los que se publican sin dirección.
ALTER TABLE places ADD COLUMN municipality text;
ALTER TABLE places ADD COLUMN region text;

-- El filtro compara sin mayúsculas ni tildes, como la búsqueda (V5).
CREATE INDEX places_municipality ON places (search_text(municipality));
