-- Registro de publicação de eventos do Spring Modulith (spring-modulith-starter-jpa), ADR 0008.
-- Cada evento entre módulos é gravado na mesma transação que o originou e marcado como concluído
-- quando o consumidor termina; o que ficar incompleto é reenviado ao reiniciar a aplicação.
CREATE TABLE event_publication (
    id               UUID                     NOT NULL PRIMARY KEY,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
