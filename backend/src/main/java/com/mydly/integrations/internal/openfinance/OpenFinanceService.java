package com.mydly.integrations.internal.openfinance;

import com.mydly.integrations.internal.ImportService;
import com.mydly.integrations.internal.openfinance.OpenFinanceException.Kind;
import com.mydly.integrations.internal.openfinance.PluggyModels.Account;
import com.mydly.integrations.internal.openfinance.PluggyModels.Item;
import com.mydly.integrations.internal.openfinance.PluggyModels.Transaction;
import com.mydly.preferences.UserCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Open Finance pelo Pluggy, só no sandbox (ADR 0005 e ADR 0015): conectar um banco de teste,
 * importar as transações dele e desconectar.
 *
 * <p>As transações passam pela mesma importação e reconciliação do extrato (ADR 0014), com
 * {@code source = OPEN_FINANCE}: o que já foi lançado no app não conta duas vezes, e sincronizar de
 * novo não duplica nada. As chamadas ao Pluggy ficam fora da transação do banco de dados.
 */
@Service
public class OpenFinanceService {

    /** A cada sincronização, volta uma semana antes da anterior: o banco pode lançar com atraso. */
    static final int OVERLAP_DAYS = 7;

    private final OpenFinanceProperties properties;
    private final PluggyClient pluggy;
    private final OpenFinanceConnectionRepository connections;
    private final ImportService imports;
    private final UserCalendar calendar;
    private final Clock clock;

    OpenFinanceService(OpenFinanceProperties properties, PluggyClient pluggy, OpenFinanceConnectionRepository connections,
                       ImportService imports, UserCalendar calendar, Clock clock) {
        this.properties = properties;
        this.pluggy = pluggy;
        this.connections = connections;
        this.imports = imports;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** O resultado de uma sincronização; {@code summary} é vazio quando o item ainda não estava pronto. */
    public record Sync(OpenFinanceConnection connection, Optional<ImportService.Summary> summary) {
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** O token de 30 minutos para o widget, ligado ao usuário: só ele pode registrar o item criado. */
    public String connectToken(UUID userId) {
        requireEnabled();
        return call(() -> pluggy.connectToken(userId.toString()));
    }

    /**
     * Liga ao usuário o item que o widget criou e importa as transações. Um item que não é de sandbox
     * é recusado e apagado no Pluggy; um item criado para outro usuário é recusado.
     */
    public Sync connect(UUID userId, String itemId) {
        requireEnabled();
        Item item = call(() -> pluggy.item(itemId))
                .orElseThrow(() -> new OpenFinanceException(Kind.NOT_FOUND, "Conexão não encontrada no Pluggy."));
        if (item.clientUserId() != null && !item.clientUserId().equals(userId.toString())) {
            throw new OpenFinanceException(Kind.REJECTED, "Esta conexão foi criada para outra conta.");
        }
        if (item.connector() == null || !Boolean.TRUE.equals(item.connector().isSandbox())) {
            call(() -> {
                pluggy.deleteItem(itemId);
                return null;
            });
            throw new OpenFinanceException(Kind.REJECTED,
                    "O MyDly só aceita bancos de teste (sandbox). A conexão foi desfeita.");
        }
        Optional<OpenFinanceConnection> existing = connections.findByItem(itemId);
        if (existing.isPresent()) {
            if (!existing.get().userId().equals(userId)) {
                throw new OpenFinanceException(Kind.CONFLICT, "Esta conexão já está ligada a outra conta.");
            }
            return sync(userId, existing.get().id());
        }
        OpenFinanceConnection created = new OpenFinanceConnection(UUID.randomUUID(), userId, itemId,
                item.connector().name(), item.status(), null);
        if (!connections.insertIfAbsent(created)) {
            throw new OpenFinanceException(Kind.CONFLICT, "Esta conexão já está ligada a outra conta.");
        }
        return sync(userId, created.id());
    }

    /**
     * Busca as transações das contas do item e importa. Na primeira vez volta {@code historyDays};
     * depois, uma semana antes da última sincronização.
     */
    public Sync sync(UUID userId, UUID connectionId) {
        requireEnabled();
        OpenFinanceConnection connection = connections.find(userId, connectionId)
                .orElseThrow(() -> new OpenFinanceException(Kind.NOT_FOUND, "Conexão não encontrada."));
        Optional<Item> item = call(() -> pluggy.item(connection.itemId()));
        if (item.isEmpty()) {
            connections.updateStatus(connection.id(), "NOT_FOUND", null, null);
            return new Sync(connections.find(userId, connectionId).orElseThrow(), Optional.empty());
        }
        String institution = item.get().connector() == null ? null : item.get().connector().name();
        if (!"UPDATED".equals(item.get().status())) {
            connections.updateStatus(connection.id(), item.get().status(), institution, null);
            return new Sync(connections.find(userId, connectionId).orElseThrow(), Optional.empty());
        }
        LocalDate today = calendar.today();
        LocalDate from = connection.lastSyncedAt() == null
                ? today.minusDays(properties.historyDays())
                : LocalDate.ofInstant(connection.lastSyncedAt(), calendar.zone()).minusDays(OVERLAP_DAYS);
        List<Transaction> transactions = call(() -> {
            List<Transaction> all = new ArrayList<>();
            for (Account account : pluggy.accounts(connection.itemId())) {
                all.addAll(pluggy.transactions(account.id(), from, today));
            }
            return all;
        });
        ImportService.Summary summary = imports.importFrom(userId, new OpenFinanceSource(transactions, calendar.zone()));
        connections.updateStatus(connection.id(), item.get().status(), institution, clock.instant());
        return new Sync(connections.find(userId, connectionId).orElseThrow(), Optional.of(summary));
    }

    /** As conexões do usuário. */
    public List<OpenFinanceConnection> list(UUID userId) {
        return connections.list(userId);
    }

    /**
     * Desfaz a conexão: o item é apagado no Pluggy e a conexão sai do MyDly. As transações já
     * importadas ficam, como as de um extrato.
     */
    public void disconnect(UUID userId, UUID connectionId) {
        requireEnabled();
        OpenFinanceConnection connection = connections.find(userId, connectionId)
                .orElseThrow(() -> new OpenFinanceException(Kind.NOT_FOUND, "Conexão não encontrada."));
        call(() -> {
            pluggy.deleteItem(connection.itemId());
            return null;
        });
        connections.delete(connection.id());
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new OpenFinanceException(Kind.DISABLED, "O Open Finance não está configurado neste servidor.");
        }
    }

    /** Uma chamada ao Pluggy; falha de rede ou resposta de erro vira uma mensagem para o usuário. */
    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException failure) {
            throw new OpenFinanceException("O Pluggy não respondeu. Tente de novo em instantes.");
        }
    }
}
