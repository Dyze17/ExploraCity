-- v1.2.0 · La app viene en inglés y en español, y traduce lo que antes le llegaba solo en español. Los avisos (25) traen
-- también el código: la frase en español sigue en achievement y reason, para la app de antes.
-- achievement_id es la insignia que se desbloqueó; badge_id sigue siendo la siguiente.
ALTER TABLE notifications ADD COLUMN achievement_id text REFERENCES badges ON DELETE SET NULL;
-- El motivo del rechazo (REJECTED: PHOTO, LOCATION…) o de finalizar (FINALIZED: CLOSED, EVENT_ENDED, MERGED).
ALTER TABLE notifications ADD COLUMN reason_code text;

-- Los avisos de antes: el código sale del texto que se guardó (ModerationTexts).
UPDATE notifications n SET achievement_id = b.id FROM badges b WHERE n.type = 'ACHIEVEMENT' AND n.achievement = b.name;
UPDATE notifications SET reason_code = CASE reason
    WHEN 'el lugar ya está publicado' THEN 'DUPLICATE'
    WHEN 'la foto no permite reconocer el lugar' THEN 'PHOTO'
    WHEN 'la ubicación no corresponde' THEN 'LOCATION'
    WHEN 'contenido inapropiado o publicidad' THEN 'INAPPROPRIATE'
    ELSE 'OTHER'
END
WHERE type = 'REJECTED';
UPDATE notifications SET reason_code = CASE reason
    WHEN 'el lugar cerró de forma permanente' THEN 'CLOSED'
    WHEN 'fue un evento temporal que ya pasó' THEN 'EVENT_ENDED'
    WHEN 'se unificó con otra publicación' THEN 'MERGED'
END
WHERE type = 'FINALIZED';
