-- Esquema de ExploraCity (ADR-04). Los nombres de tablas y columnas van en inglés, como el código; los valores de
-- los enums son los de la app (domain/model). Las fechas son timestamptz; las cuentas de días usan la zona de la ciudad.

-- Personas. El correo se compara sin mayúsculas. El rol MODERATOR lo da la lista de moderadores de la configuración
-- (ADR-06: cuentas precargadas), nunca la app.
CREATE TABLE users (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email           text        NOT NULL,
    -- «Cambiar correo»: el correo nuevo que espera confirmación por enlace; hasta entonces se entra con email.
    pending_email   text,
    password_hash   text        NOT NULL,
    name            text        NOT NULL CHECK (char_length(name) BETWEEN 2 AND 40),
    bio             text CHECK (char_length(bio) <= 150),
    residency       text        NOT NULL CHECK (residency IN ('RESIDENT', 'VISITOR')),
    -- Foto de perfil en Cloudinary (ADR-07): la dirección y su id para borrarla.
    photo_url       text,
    photo_public_id text,
    role            text        NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'MODERATOR')),
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_unique ON users (lower(email));

-- Sesiones (ADR-06, D1): el token de renovación solo se guarda como hash y se reemplaza en cada uso. Cerrar sesión,
-- cambiar la contraseña o eliminar la cuenta lo revoca.
CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user ON refresh_tokens (user_id);

-- Enlaces del correo: recuperar la contraseña (5, 6 y 6C) y confirmar un correo nuevo. Solo se guarda el hash del
-- token; email es la dirección a la que se envió.
CREATE TABLE account_links (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    purpose    text        NOT NULL CHECK (purpose IN ('PASSWORD_RESET', 'EMAIL_CHANGE')),
    token_hash text        NOT NULL UNIQUE,
    email      text        NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX account_links_user ON account_links (user_id, purpose);

-- Lugares: cada publicación en cualquier estado (PENDING → VERIFIED / REJECTED → FINALIZED; editarla la devuelve a
-- PENDING). El feed y el mapa muestran las verificadas y las finalizadas. Si la cuenta de quien la publicó se elimina,
-- el lugar público se queda sin autor.
CREATE TABLE places (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id          uuid REFERENCES users ON DELETE SET NULL,
    title              text             NOT NULL CHECK (char_length(title) BETWEEN 5 AND 60),
    description        text             NOT NULL CHECK (char_length(description) BETWEEN 30 AND 600),
    category           text             NOT NULL CHECK (category IN ('GASTRONOMY', 'CULTURE', 'NATURE', 'ENTERTAINMENT', 'HISTORY')),
    -- 16 · «Categoría sugerida» o elegida por quien publica, para medir la sugerencia.
    category_origin    text             NOT NULL CHECK (category_origin IN ('SUGGESTED', 'CHOSEN')),
    status             text             NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'FINALIZED')),
    latitude           double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude          double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    -- ADR-13: la misma coordenada como geografía, para buscar en metros con el índice GiST.
    location           geography(Point, 4326) GENERATED ALWAYS AS
                           (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    address            text,
    price              text CHECK (price IN ('FREE', 'LOW', 'MEDIUM', 'HIGH')),
    -- 18 · Horario: días de atención como bits (lunes = 1 … domingo = 64) y una franja. Sin horario, los tres son null.
    hours_days         smallint CHECK (hours_days BETWEEN 1 AND 127),
    hours_opens        time,
    hours_closes       time,
    -- ADR-14 · Quien publica confirmó que es distinto de un lugar parecido: el moderador lo ve marcado.
    possible_duplicate boolean          NOT NULL DEFAULT false,
    duplicate_note     text,
    -- Puntos que ganó quien la publicó con ella (verificada, primera publicación); se descuentan si la elimina.
    points_earned      integer          NOT NULL DEFAULT 0 CHECK (points_earned >= 0),
    -- Entrada a la cola: la primera vez y cada reenvío o edición.
    submitted_at       timestamptz      NOT NULL DEFAULT now(),
    created_at         timestamptz      NOT NULL DEFAULT now(),
    CHECK ((hours_days IS NULL) = (hours_opens IS NULL) AND (hours_opens IS NULL) = (hours_closes IS NULL))
);
CREATE INDEX places_location ON places USING gist (location);
CREATE INDEX places_title_trgm ON places USING gin (title gin_trgm_ops);
CREATE INDEX places_status ON places (status, submitted_at);
CREATE INDEX places_author ON places (author_id);

-- Fotos (ADR-07): solo la dirección y el id de Cloudinary. Se suben antes de enviar la publicación (place_id null) y
-- se asignan al enviarla, en orden; la primera es la portada. Al eliminar la cuenta se borran todas las suyas.
CREATE TABLE photos (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    place_id   uuid REFERENCES places ON DELETE CASCADE,
    position   smallint,
    url        text        NOT NULL,
    public_id  text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((place_id IS NULL) = (position IS NULL))
);
CREATE INDEX photos_place ON photos (place_id, position);
CREATE INDEX photos_owner ON photos (owner_id);

-- 17B · Los lugares parecidos que quien publica dijo que son otro (el moderador los compara en 33A).
CREATE TABLE place_similar (
    place_id   uuid NOT NULL REFERENCES places ON DELETE CASCADE,
    similar_id uuid NOT NULL REFERENCES places ON DELETE CASCADE,
    PRIMARY KEY (place_id, similar_id),
    CHECK (place_id <> similar_id)
);

-- Decisiones de moderación (34, 35 y 36): el historial de cada publicación, del que salen «Resueltas», «Tu trabajo de
-- hoy» y el historial de quien publica.
CREATE TABLE moderation_decisions (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id          uuid        NOT NULL REFERENCES places ON DELETE CASCADE,
    moderator_id      uuid REFERENCES users ON DELETE SET NULL,
    action            text        NOT NULL CHECK (action IN ('VERIFIED', 'REJECTED', 'FINALIZED', 'REOPENED')),
    -- 34 · Nota interna al verificar; 36 · motivo de volver a pendiente. Solo la ven los moderadores.
    note              text,
    -- 35 · Rechazo: motivo, mensaje para quien publicó, si puede corregirla y, con duplicado, el lugar original.
    rejection_reason  text CHECK (rejection_reason IN ('DUPLICATE', 'PHOTO', 'LOCATION', 'INAPPROPRIATE', 'OTHER')),
    rejection_message text,
    can_resubmit      boolean,
    duplicate_of      uuid REFERENCES places ON DELETE SET NULL,
    -- 36 · Motivo de finalizar.
    finalize_reason   text CHECK (finalize_reason IN ('CLOSED', 'EVENT_ENDED', 'MERGED')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CHECK ((action = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CHECK ((action = 'FINALIZED') = (finalize_reason IS NOT NULL))
);
CREATE INDEX moderation_decisions_place ON moderation_decisions (place_id, created_at DESC);
CREATE INDEX moderation_decisions_moderator ON moderation_decisions (moderator_id, created_at);

-- 14 · Voto «Es importante». Al eliminar la cuenta el voto se queda como cifra sin autor.
CREATE TABLE votes (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id   uuid        NOT NULL REFERENCES places ON DELETE CASCADE,
    user_id    uuid REFERENCES users ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
-- Un voto por persona; los anónimos (user_id null) no chocan entre sí.
CREATE UNIQUE INDEX votes_place_user ON votes (place_id, user_id);

-- 14.b · «Marcar como visitado» con la experiencia, todo opcional salvo la marca.
CREATE TABLE visits (
    place_id       uuid        NOT NULL REFERENCES places ON DELETE CASCADE,
    user_id        uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    recommends     boolean,
    text           text CHECK (char_length(text) <= 300),
    show_name      boolean     NOT NULL DEFAULT false,
    points_awarded integer     NOT NULL DEFAULT 0 CHECK (points_awarded >= 0),
    created_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (place_id, user_id)
);

-- 14 · Comentarios. Al eliminar la cuenta aparecen como «Usuario eliminado». client_id lo pone la app para que un
-- reenvío desde la cola sin conexión no lo duplique.
CREATE TABLE comments (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id   uuid        NOT NULL REFERENCES places ON DELETE CASCADE,
    author_id  uuid REFERENCES users ON DELETE SET NULL,
    client_id  uuid,
    text       text        NOT NULL CHECK (char_length(text) BETWEEN 1 AND 300),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX comments_place ON comments (place_id, created_at DESC, id DESC);
CREATE INDEX comments_author ON comments (author_id);
CREATE UNIQUE INDEX comments_client ON comments (author_id, client_id) WHERE client_id IS NOT NULL;

-- 27 · Catálogo de insignias: lo define el servidor (la app solo nombra la unidad del avance).
CREATE TABLE badges (
    id       text PRIMARY KEY,
    position smallint NOT NULL UNIQUE,
    name     text     NOT NULL,
    metric   text     NOT NULL CHECK (metric IN ('PUBLICATIONS', 'VERIFIED_PLACES', 'CATEGORY_PLACES', 'COMMENTS', 'VISITS', 'VOTES_RECEIVED')),
    category text CHECK (category IN ('GASTRONOMY', 'CULTURE', 'NATURE', 'ENTERTAINMENT', 'HISTORY')),
    target   integer  NOT NULL CHECK (target > 0),
    how_to   text     NOT NULL,
    tip      text,
    CHECK ((metric = 'CATEGORY_PLACES') = (category IS NOT NULL))
);

-- Insignias ya conseguidas: el aviso de logro sale una sola vez.
CREATE TABLE user_badges (
    user_id     uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    badge_id    text        NOT NULL REFERENCES badges ON DELETE CASCADE,
    unlocked_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, badge_id)
);

-- 25 · Avisos dentro de la app (ADR-09). La app arma la frase según el tipo; aquí queda lo que la frase necesita, con
-- el título de entonces. El autor y el texto de un comentario salen del comentario, así respetan su eliminación.
CREATE TABLE notifications (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    type              text        NOT NULL CHECK (type IN ('VERIFIED', 'FINALIZED', 'COMMENTED', 'REJECTED', 'DUPLICATE_REJECTED', 'ACHIEVEMENT')),
    place_id          uuid REFERENCES places ON DELETE CASCADE,
    place_title       text,
    -- VERIFIED: puntos ganados.
    points            integer,
    -- REJECTED: la frase del motivo; FINALIZED: el motivo en minúscula.
    reason            text,
    -- COMMENTED
    comment_id        uuid REFERENCES comments ON DELETE CASCADE,
    -- DUPLICATE_REJECTED: el lugar que ya existía.
    existing_place_id uuid REFERENCES places ON DELETE SET NULL,
    existing_title    text,
    -- ACHIEVEMENT: el logro, la insignia siguiente y cuánto falta.
    achievement       text,
    badge_id          text REFERENCES badges ON DELETE SET NULL,
    remaining         integer,
    read_at           timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user ON notifications (user_id, created_at DESC);

-- 31A · Reportes de perfiles.
CREATE TABLE user_reports (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reported_id uuid        NOT NULL REFERENCES users ON DELETE CASCADE,
    reporter_id uuid REFERENCES users ON DELETE SET NULL,
    reason      text        NOT NULL CHECK (reason IN ('IMPERSONATION', 'INAPPROPRIATE_CONTENT', 'SPAM')),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX user_reports_reported ON user_reports (reported_id);

-- Puntos de cada persona (26): se calculan de lo que ganó con sus lugares y sus visitas, así eliminar una publicación
-- descuenta lo suyo sin llevar otra cuenta aparte.
CREATE VIEW user_points AS
SELECT u.id AS user_id,
       (COALESCE((SELECT sum(p.points_earned) FROM places p WHERE p.author_id = u.id), 0)
           + COALESCE((SELECT sum(v.points_awarded) FROM visits v WHERE v.user_id = u.id), 0))::integer AS points
FROM users u;
