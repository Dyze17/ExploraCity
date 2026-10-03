-- A1 · Límite de intentos de inicio de sesión. Una fila por correo (en minúscula), exista o no la cuenta: así el bloqueo
-- no revela qué correos tienen cuenta. Los fallos se cuentan en una ventana de 15 minutos que empieza con el primero; al
-- quinto, el correo queda bloqueado 15 minutos, también para la contraseña correcta. Entrar bien o crear una contraseña
-- nueva con el enlace del correo borra la fila.
CREATE TABLE login_attempts (
    email             text PRIMARY KEY,
    failures          integer     NOT NULL CHECK (failures >= 0),
    window_started_at timestamptz NOT NULL,
    locked_until      timestamptz
);

-- D1 · Al renovar la sesión, el token usado apunta al que lo reemplazó. Si vuelve a llegar uno ya reemplazado, alguien
-- copió la sesión y se cierran todas las de la cuenta. Uno revocado sin reemplazo (cerrar sesión, contraseña nueva o
-- cambio desde otro teléfono) solo se rechaza.
ALTER TABLE refresh_tokens ADD COLUMN replaced_by uuid REFERENCES refresh_tokens ON DELETE SET NULL;
