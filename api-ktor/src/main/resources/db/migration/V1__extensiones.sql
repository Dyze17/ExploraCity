-- Extensiones que exige la detección de duplicados (ADR-13):
-- PostGIS para la búsqueda en un radio de 50 m y pg_trgm para comparar títulos parecidos.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
