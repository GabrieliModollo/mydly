package com.mydly.integrations.internal;

import com.mydly.entries.EntryItemAccepted;
import com.mydly.entries.EntryItemRetracted;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reconcilia na outra direção (ADR 0014): um lançamento escrito depois da importação liga-se à
 * transação do banco que já estava lá, e um "Não foi isso" desfaz a ligação. Roda depois da
 * transação que publicou o evento, como os consumidores de {@code finance} e {@code routines}
 * (ADR 0008); se falhar, o registro de publicação reenvia, e repetir não muda nada.
 */
@Component
class ManualEntriesListener {

    private final ImportService imports;

    ManualEntriesListener(ImportService imports) {
        this.imports = imports;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(EntryItemAccepted event) {
        ParsedEntry item = event.item();
        boolean finance = item.type() == EntryType.FINANCE;
        imports.onManualAccepted(event.userId(), event.entryId(), event.itemIndex(),
                finance ? item.direction() : null, finance ? item.amount() : null, event.occurredAt());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(EntryItemRetracted event) {
        imports.onManualRetracted(event.entryId(), event.itemIndex());
    }
}
