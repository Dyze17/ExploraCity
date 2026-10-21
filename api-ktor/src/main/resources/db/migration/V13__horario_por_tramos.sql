-- v1.3.0 · El horario por tramos (18): unos días con la misma franja, como «lunes a jueves, 16:00 a 2:00» y «viernes y
-- sábado, 22:00 a 3:00». Si el cierre no es posterior a la apertura, cierra al día siguiente. always_open es el lugar
-- sin puertas ni horario: una plaza, un parque, una cascada.
CREATE TABLE place_hours (
    place_id uuid NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    position smallint NOT NULL,
    days smallint NOT NULL CHECK (days BETWEEN 1 AND 127),
    opens time NOT NULL,
    closes time NOT NULL,
    PRIMARY KEY (place_id, position),
    CHECK (closes <> opens)
);

-- El horario de antes es el primer tramo. Las columnas viejas siguen con el primer tramo: si se vuelve a la versión
-- anterior de la API, el lugar conserva un horario.
INSERT INTO place_hours (place_id, position, days, opens, closes)
SELECT id, 0, hours_days, hours_opens, hours_closes
FROM places
WHERE hours_days IS NOT NULL AND hours_opens IS NOT NULL AND hours_closes IS NOT NULL;

ALTER TABLE places ADD COLUMN always_open boolean NOT NULL DEFAULT false;
