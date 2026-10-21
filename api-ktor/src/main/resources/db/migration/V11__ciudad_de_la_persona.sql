-- v1.3.0 · La ciudad de cada persona, la de su perfil (26, 28 y 31): «Residente · Calarcá». Antes el perfil mostraba
-- la ciudad de la configuración para todos. La app la llena la primera vez que sabe dónde está la persona, si estaba
-- vacía, y se cambia en Editar perfil. town_region es el departamento, como en places (V9). town_set dice que ya se
-- puso una vez, sola o a mano: desde ahí la app no la llena, así que quitarla en Editar perfil también se respeta.
ALTER TABLE users ADD COLUMN town text;
ALTER TABLE users ADD COLUMN town_region text;
ALTER TABLE users ADD COLUMN town_set boolean NOT NULL DEFAULT false;
