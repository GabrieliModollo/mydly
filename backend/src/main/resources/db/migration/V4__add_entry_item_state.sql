-- Estado de cada item (ADR 0008): lista alinhada com parsed_json, com
-- {"state": PENDING | ACCEPTED_AUTO | ACCEPTED_BY_USER | DISMISSED, "revision": n, "parserRejected": bool}.
-- O parsed_json continua intacto.
ALTER TABLE entry ADD COLUMN item_state JSONB;

-- Itens já gravados: a resolução do usuário decide; sem ela, a confiança do parser.
UPDATE entry e
SET item_state = (
    SELECT jsonb_agg(jsonb_build_object(
               'state', CASE
                            WHEN r.value IS NOT NULL AND r.value <> 'null'::jsonb THEN
                                CASE WHEN r.value ->> 'action' = 'DISMISS' THEN 'DISMISSED' ELSE 'ACCEPTED_BY_USER' END
                            WHEN (p.value ->> 'confidence')::numeric >= 0.50 THEN 'ACCEPTED_AUTO'
                            ELSE 'PENDING'
                        END,
               'revision', 1,
               'parserRejected', false)
           ORDER BY p.position)
    FROM jsonb_array_elements(e.parsed_json) WITH ORDINALITY AS p(value, position)
    LEFT JOIN jsonb_array_elements(COALESCE(e.resolution, '[]'::jsonb)) WITH ORDINALITY AS r(value, position)
           ON r.position = p.position)
WHERE e.parsed_json IS NOT NULL;

-- PENDING deixa de existir: o lançamento ou espera o usuário, ou está todo aceito ou descartado.
UPDATE entry e
SET status = CASE
                 WHEN EXISTS (SELECT 1 FROM jsonb_array_elements(e.item_state) s WHERE s ->> 'state' = 'PENDING')
                     THEN 'NEEDS_INPUT'
                 ELSE 'CONFIRMED'
             END;

ALTER TABLE entry DROP CONSTRAINT chk_entry_status;
ALTER TABLE entry ALTER COLUMN status SET DEFAULT 'NEEDS_INPUT';
ALTER TABLE entry ADD CONSTRAINT chk_entry_status CHECK (status IN ('CONFIRMED', 'NEEDS_INPUT'));

-- Tarefas de uma vez só, como publicar os eventos dos lançamentos que já existiam (backfill).
CREATE TABLE entry_one_time_task (
    name    TEXT        PRIMARY KEY,
    done_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
