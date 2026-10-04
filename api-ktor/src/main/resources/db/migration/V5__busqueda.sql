-- 7 y 9 · La búsqueda del feed ignora mayúsculas y tildes: «cafe» encuentra «Café Las Acacias».
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() no es inmutable (depende del diccionario) y no sirve en un índice. Esta envoltura fija el diccionario y sí.
CREATE FUNCTION search_text(value text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, value)) $$;

-- Búsqueda por parte del título (LIKE '%…%') con el índice de trigramas.
CREATE INDEX places_title_search ON places USING gin (search_text(title) gin_trgm_ops);
