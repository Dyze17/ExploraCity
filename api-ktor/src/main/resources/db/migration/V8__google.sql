-- ADR-15 · Entrar con Google. google_sub es el identificador de la cuenta de Google (el «sub» de su ID token): no
-- cambia aunque la persona cambie su correo de Google, así que es lo que identifica la cuenta, no el correo.
-- Una cuenta creada con Google no tiene contraseña hasta que pida una con «¿Olvidaste tu contraseña?».
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN google_sub text;
CREATE UNIQUE INDEX users_google_sub ON users (google_sub);
-- Toda cuenta tiene por dónde entrar.
ALTER TABLE users ADD CONSTRAINT users_sign_in CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL);
