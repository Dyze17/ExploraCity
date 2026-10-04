-- 20 · client_id lo pone la app al enviar: si la cola sin conexión reenvía una publicación que ya llegó, no se duplica.
ALTER TABLE places ADD COLUMN client_id uuid;
CREATE UNIQUE INDEX places_client ON places (author_id, client_id) WHERE client_id IS NOT NULL;

-- D1 · La primera publicación de una persona gana +20 al enviarla y los guarda en points_earned. Esta marca dice si
-- los tiene: se quitan si un moderador la rechaza sin permitir que se reenvíe.
ALTER TABLE places ADD COLUMN first_publication boolean NOT NULL DEFAULT false;
