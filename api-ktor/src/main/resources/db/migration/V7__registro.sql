-- 4 · client_id lo pone la app al crear la cuenta. Si la respuesta se perdió (la API terminó después de que la app se
-- rindió), repetir el registro con el mismo client_id y la misma contraseña abre la sesión en vez de responder
-- email_taken. No es único: se busca por correo, que sí lo es.
ALTER TABLE users ADD COLUMN client_id uuid;
