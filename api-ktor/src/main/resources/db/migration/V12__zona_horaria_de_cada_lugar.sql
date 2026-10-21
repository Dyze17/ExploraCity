-- v1.3.0 · La zona horaria de cada lugar (ADR-17), de sus coordenadas con Timeshape: «Abierto ahora» usa la hora del
-- lugar y no la de la ciudad de la configuración. null mientras no se sabe; esos lugares usan la de la ciudad. Al
-- arrancar, la API llena los que falten, empezando por los de antes de la 1.3.0.
ALTER TABLE places ADD COLUMN time_zone text;
