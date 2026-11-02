-- v1.4.0 · Fotos que una persona agrega a un lugar público con menos de 5, aunque no lo haya publicado (C1). Esperan a
-- un moderador antes de verse (D1): mientras tanto la foto no tiene lugar (photos.place_id NULL) y aquí se anota a cuál
-- va. Al aprobarla pasa al final de las fotos del lugar y sale de aquí; rechazada, se borra con su archivo.
CREATE TABLE pending_photos (
    photo_id   uuid PRIMARY KEY REFERENCES photos ON DELETE CASCADE,
    place_id   uuid        NOT NULL REFERENCES places ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX pending_photos_place ON pending_photos (place_id);

-- Los avisos a quien agregó la foto: aprobada o rechazada con el motivo.
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'VERIFIED', 'FINALIZED', 'COMMENTED', 'REJECTED', 'DUPLICATE_REJECTED', 'ACHIEVEMENT', 'PHOTO_APPROVED', 'PHOTO_REJECTED'
));
