-- v1.4.0 · Reportar un lugar (13): el motivo y, si se quiere, qué pasa (hasta 300 caracteres; con «Otro» es
-- obligatorio). Es anónimo: ni quien lo publicó ni los moderadores ven quién reportó. Cada persona tiene a lo sumo un
-- reporte abierto por lugar; reportar otra vez lo cambia.
-- Se cierra cuando un moderador lo descarta en «Reportados», cuando pasa el lugar a finalizada (36) o cuando decide en
-- la revisión (33) el lugar que volvió a pendiente.
CREATE TABLE place_reports (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id    uuid        NOT NULL REFERENCES places ON DELETE CASCADE,
    reporter_id uuid REFERENCES users ON DELETE SET NULL,
    reason      text        NOT NULL CHECK (reason IN (
        'CLOSED', 'WRONG_LOCATION', 'OUTDATED_INFO', 'BAD_PHOTOS', 'INAPPROPRIATE', 'DUPLICATE', 'OTHER'
    )),
    detail      text CHECK (char_length(detail) BETWEEN 1 AND 300),
    created_at  timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    resolved_by uuid REFERENCES users ON DELETE SET NULL,
    resolution  text CHECK (resolution IN ('DISMISSED', 'FINALIZED', 'REVIEWED')),
    CHECK ((resolved_at IS NULL) = (resolution IS NULL)),
    CHECK (reason <> 'OTHER' OR detail IS NOT NULL)
);
CREATE UNIQUE INDEX place_reports_open_by_person ON place_reports (place_id, reporter_id) WHERE resolved_at IS NULL;
CREATE INDEX place_reports_open ON place_reports (place_id) WHERE resolved_at IS NULL;
