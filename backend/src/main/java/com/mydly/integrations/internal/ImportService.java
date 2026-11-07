package com.mydly.integrations.internal;

import com.mydly.entries.Direction;
import com.mydly.finance.ExternalTransactions;
import com.mydly.finance.ExternalTransactions.ManualTransaction;
import com.mydly.integrations.ExternalTransaction;
import com.mydly.integrations.TransactionSource;
import com.mydly.integrations.internal.ImportedTransaction.Status;
import com.mydly.preferences.UserCalendar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importa transações de qualquer {@link TransactionSource} e as reconcilia com os lançamentos
 * manuais (ADR 0005 e ADR 0014).
 *
 * <p>Uma transação nova que casa com um lançamento manual fica ligada a ele e não conta de novo. A
 * que não casa entra nos números pela API pública de {@code finance}, na mesma transação do banco.
 * Reimportar a mesma transação não faz nada: o id externo é único por usuário e origem.
 *
 * <p>Por privacidade (LGPD), nada aqui registra em log descrições nem valores.
 */
@Service
public class ImportService {

    /** As transações do banco entram nos números ao meio-dia do dia delas, no fuso do usuário. */
    private static final LocalTime BANK_TIME = LocalTime.NOON;

    private final ImportedTransactionRepository repository;
    private final ExternalTransactions finance;
    private final ZoneId zone;

    ImportService(ImportedTransactionRepository repository, ExternalTransactions finance, UserCalendar calendar) {
        this.repository = repository;
        this.finance = finance;
        this.zone = calendar.zone();
    }

    /** O resultado de uma importação. */
    public record Summary(int read, int added, int matched, int duplicates, List<ImportedTransaction> transactions) {
    }

    /** Importa tudo o que a fonte trouxer, numa transação só: ou o arquivo inteiro entra, ou nada entra. */
    @Transactional
    public Summary importFrom(UUID userId, TransactionSource source) {
        List<ExternalTransaction> read = source.fetch().stream()
                .sorted(Comparator.comparing(ExternalTransaction::date))
                .toList();
        Set<String> taken = repository.matchedItems(userId);
        int added = 0;
        int matched = 0;
        int duplicates = 0;
        List<ImportedTransaction> imported = new ArrayList<>();
        for (ExternalTransaction line : read) {
            Optional<ManualTransaction> manual = manualMatch(userId, line.direction(), line.amount(), line.date(), taken,
                    null);
            ImportedTransaction transaction = new ImportedTransaction(UUID.randomUUID(), userId, source.origin(),
                    line.externalId(), line.date(), line.amount(), line.direction(), line.description(),
                    manual.isPresent() ? Status.MATCHED : Status.IMPORTED,
                    manual.map(ManualTransaction::entryId).orElse(null),
                    manual.map(ManualTransaction::itemIndex).orElse(null), 1);
            if (!repository.insertIfAbsent(transaction)) {
                duplicates++;
                continue;
            }
            imported.add(transaction);
            if (manual.isPresent()) {
                taken.add(ImportedTransactionRepository.itemKey(manual.get().entryId(), manual.get().itemIndex()));
                matched++;
            } else {
                applyToFinance(transaction);
                added++;
            }
        }
        return new Summary(read.size(), added, matched, duplicates, imported);
    }

    /** As transações importadas do usuário nos dias pedidos, da mais nova à mais antiga. */
    @Transactional(readOnly = true)
    public List<ImportedTransaction> list(UUID userId, LocalDate from, LocalDate to) {
        return repository.list(userId, from, to);
    }

    /**
     * "Não contar": a transação sai dos números (uma transferência entre contas do próprio usuário, por
     * exemplo). Se estava ligada a um lançamento, o lançamento continua valendo e fica livre.
     */
    @Transactional
    public Optional<ImportedTransaction> ignore(UUID userId, UUID id) {
        return repository.findForUpdate(userId, id).map(transaction -> {
            if (transaction.status() == Status.IGNORED) {
                return transaction;
            }
            ImportedTransaction ignored = transaction.with(Status.IGNORED, null, null);
            save(transaction, ignored);
            return ignored;
        });
    }

    /** "Contar de novo": a transação volta, ligada a um lançamento se achar um, ou sozinha nos números. */
    @Transactional
    public Optional<ImportedTransaction> restore(UUID userId, UUID id) {
        return repository.findForUpdate(userId, id).map(transaction -> {
            if (transaction.status() != Status.IGNORED) {
                return transaction;
            }
            ImportedTransaction restored = settle(transaction, null);
            save(transaction, restored);
            return restored;
        });
    }

    /**
     * Um item de lançamento passou a valer (ou mudou). Se já estava ligado a uma transação externa que
     * não combina mais, a ligação é desfeita; se é um gasto ou uma entrada com valor, procura uma
     * transação importada que ainda conta sozinha e liga as duas, tirando a importada dos números.
     */
    @Transactional
    public void onManualAccepted(UUID userId, UUID entryId, int itemIndex, Direction direction, BigDecimal amount,
                                 Instant occurredAt) {
        LocalDate day = LocalDate.ofInstant(occurredAt, zone);
        boolean money = direction != null && amount != null && amount.signum() > 0;
        Optional<ImportedTransaction> current = repository.findMatchedTo(entryId, itemIndex);
        if (current.isPresent()) {
            ImportedTransaction linked = current.get();
            if (money && linked.direction() == direction && linked.amount().compareTo(amount) == 0
                    && Reconciliation.withinWindow(linked.date(), day)) {
                return;
            }
            unlink(linked, ImportedTransactionRepository.itemKey(entryId, itemIndex));
        }
        if (!money) {
            return;
        }
        List<ImportedTransaction> candidates = repository.unmatched(userId, direction, amount,
                day.minusDays(Reconciliation.BANK_DAYS_BEFORE), day.plusDays(Reconciliation.BANK_DAYS_AFTER));
        Reconciliation.best(day, false, candidates, ImportedTransaction::date).ifPresent(transaction ->
                save(transaction, transaction.with(Status.MATCHED, entryId, itemIndex)));
    }

    /** O item deixou de valer ("Não foi isso"): a transação ligada a ele volta a contar, ou casa com outro. */
    @Transactional
    public void onManualRetracted(UUID entryId, int itemIndex) {
        repository.findMatchedTo(entryId, itemIndex).ifPresent(linked ->
                unlink(linked, ImportedTransactionRepository.itemKey(entryId, itemIndex)));
    }

    private void unlink(ImportedTransaction linked, String excludedItem) {
        save(linked, settle(linked, excludedItem));
    }

    /**
     * Para onde vai uma transação que ficou livre: ligada a outro lançamento livre na janela, se houver
     * um, ou sozinha nos números. A revisão sobe uma vez só.
     *
     * @param excludedItem o item de lançamento que acabou de deixar de valer, que ainda pode aparecer
     *                     ativo em {@code finance} até o evento dele ser aplicado lá
     */
    private ImportedTransaction settle(ImportedTransaction current, String excludedItem) {
        Set<String> taken = repository.matchedItems(current.userId());
        if (current.matchedEntryId() != null) {
            // A ligação atual está sendo desfeita: o item dela não conta como ocupado.
            taken.remove(ImportedTransactionRepository.itemKey(current.matchedEntryId(), current.matchedItemIndex()));
        }
        Optional<ManualTransaction> manual = manualMatch(current.userId(), current.direction(), current.amount(),
                current.date(), taken, excludedItem);
        return manual
                .map(found -> current.with(Status.MATCHED, found.entryId(), found.itemIndex()))
                .orElseGet(() -> current.with(Status.IMPORTED, null, null));
    }

    /** Grava a mudança e, se a transação passou a contar ou deixou de contar, avisa {@code finance}. */
    private void save(ImportedTransaction before, ImportedTransaction after) {
        repository.update(after);
        if (before.counts() != after.counts()) {
            applyToFinance(after);
        }
    }

    private void applyToFinance(ImportedTransaction transaction) {
        finance.apply(new ExternalTransactions.Change(transaction.userId(), transaction.id(), transaction.revision(),
                transaction.counts(), transaction.amount(), transaction.direction(),
                transaction.date().atTime(BANK_TIME).atZone(zone).toInstant(), transaction.source()));
    }

    /** O lançamento manual livre que melhor casa com uma transação do banco nesse dia. */
    private Optional<ManualTransaction> manualMatch(UUID userId, Direction direction, BigDecimal amount, LocalDate bankDay,
                                                    Set<String> taken, String excludedItem) {
        Instant from = Reconciliation.firstManualDay(bankDay).atStartOfDay(zone).toInstant();
        Instant to = Reconciliation.lastManualDay(bankDay).plusDays(1).atStartOfDay(zone).toInstant();
        List<ManualTransaction> free = finance.manual(userId, direction, amount, from, to).stream()
                .filter(candidate -> {
                    String key = ImportedTransactionRepository.itemKey(candidate.entryId(), candidate.itemIndex());
                    return !taken.contains(key) && !key.equals(excludedItem);
                })
                .toList();
        return Reconciliation.best(bankDay, true, free, candidate -> LocalDate.ofInstant(candidate.occurredAt(), zone));
    }
}
