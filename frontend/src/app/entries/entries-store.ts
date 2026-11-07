import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, DOCUMENT, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Connectivity } from './connectivity';
import { EntriesApi } from './entries-api';
import { dayInSaoPaulo, summaryFrom } from './entry-format';
import { DaySummaryResponse, EntryItemResponse, EntryResponse, ItemHabit, ResolveItemRequest } from './entry.models';
import { OUTBOX_KEY, OUTBOX_STORAGE, OutboxEntry, readOutbox, writeOutbox } from './outbox-storage';
import { randomId } from './random-id';

/** Gera o id de um lançamento novo. Substituível nos testes. */
export const NEW_ENTRY_ID = new InjectionToken<() => string>('NEW_ENTRY_ID', {
  providedIn: 'root',
  factory: () => () => randomId(),
});

/** Relógio do app. Substituível nos testes. */
export const NOW = new InjectionToken<() => Date>('NOW', {
  providedIn: 'root',
  factory: () => () => new Date(),
});

/** Espera entre uma falha de envio e a próxima tentativa automática. Substituível nos testes. */
export const RETRY_DELAY_MS = new InjectionToken<number>('RETRY_DELAY_MS', {
  providedIn: 'root',
  factory: () => 30_000,
});

/**
 * Situação de um lançamento no app:
 * - sending: enviado, aguardando a API;
 * - synced: gravado e interpretado pela API;
 * - queued: guardado no aparelho, esperando a rede ou o servidor; vai com o mesmo id;
 * - rejected: a API recusou (4xx); reenviar não adianta.
 */
export type SyncState = 'sending' | 'synced' | 'queued' | 'rejected';

export interface TimelineEntry {
  id: string;
  text: string;
  occurredAt: string;
  sync: SyncState;
  /** Resposta da API, presente quando {@code sync} é {@code synced}. */
  entry?: EntryResponse;
}

export type LoadState = 'idle' | 'loading' | 'loaded' | 'error';

/** Desfecho de uma resolução: gravada, lançamento desatualizado (recarregado) ou erro (desfeita). */
export type ResolveResult = 'ok' | 'stale' | 'error';

/**
 * Estado da tela "Hoje": os lançamentos do dia e a fila dos que ainda não chegaram ao servidor.
 *
 * O lançamento aparece na lista assim que é escrito e é substituído pela resposta da API quando ela
 * chega. Sem rede, ele fica guardado no aparelho e é enviado quando a conexão volta, do mais antigo
 * ao mais novo. Como o id e a data são gerados aqui, um reenvio não duplica nada (ADR 0006), e o dia
 * do lançamento é o dia em que foi escrito, não o da sincronização (ADR 0011).
 */
@Injectable({ providedIn: 'root' })
export class EntriesStore {
  private readonly api = inject(EntriesApi);
  private readonly newId = inject(NEW_ENTRY_ID);
  private readonly now = inject(NOW);
  private readonly connectivity = inject(Connectivity);
  private readonly storage = inject(OUTBOX_STORAGE);
  private readonly retryDelay = inject(RETRY_DELAY_MS);

  private readonly entries = signal<TimelineEntry[]>([]);
  /**
   * Ids que esta aba já teve na lista. Ao gravar a fila, os lançamentos guardados que não estão aqui
   * são de outra aba e são mantidos.
   */
  private readonly known = new Set<string>();
  private flushing = false;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;

  readonly loadState = signal<LoadState>('idle');

  /**
   * Lançamentos do mais recente para o mais antigo: os de hoje e os que ainda não foram enviados,
   * mesmo que sejam de outro dia. Um lançamento de ontem sai da lista assim que chega ao servidor.
   */
  readonly timeline = computed(() => {
    const today = dayInSaoPaulo(this.now());
    return this.entries()
      .filter((entry) => entry.sync !== 'synced' || dayInSaoPaulo(new Date(entry.occurredAt)) === today)
      .sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt));
  });

  /** Quantos lançamentos ainda não chegaram ao servidor (guardados ou sendo enviados). */
  readonly waitingCount = computed(
    () => this.entries().filter((entry) => entry.sync === 'queued' || entry.sync === 'sending').length,
  );

  constructor() {
    this.adoptOutbox();
    const stopListening = this.connectivity.whenBackOnline(() => {
      void (this.loadState() === 'error' ? this.load() : this.flush());
    });
    // Outra aba guardou um lançamento: ele aparece aqui também, e qualquer uma das duas pode enviá-lo.
    const view = inject(DOCUMENT).defaultView;
    const onStorage = (event: StorageEvent) => {
      if (event.key === OUTBOX_KEY) {
        this.adoptOutbox();
      }
    };
    view?.addEventListener('storage', onStorage);
    inject(DestroyRef).onDestroy(() => {
      stopListening();
      view?.removeEventListener('storage', onStorage);
      clearTimeout(this.retryTimer);
    });
  }

  /** Resumo do dia vindo da API (finance e routines); {@code null} até a primeira resposta. */
  private readonly daySummary = signal<DaySummaryResponse | null>(null);
  private summaryRequests = 0;

  /** Totais dos cartões: o que vale em finance e routines, sem os itens retirados com "Não foi isso". */
  readonly summary = computed(() => summaryFrom(this.daySummary()));

  /** Progresso das metas diárias de quantidade de hoje ("Hidratação 1,5 / 2 L"). */
  readonly dailyGoals = computed(() => this.daySummary()?.dailyGoals ?? []);

  /** Categorias que chegaram a 80% do limite no período de hoje; vêm só no resumo do dia. */
  readonly budgetAlerts = computed(() => this.daySummary()?.budgetAlerts ?? []);

  /** O hábito do catálogo de cada item de rotina, pela chave {@code entryId:itemIndex}. */
  private readonly habits = computed(
    () => new Map((this.daySummary()?.items ?? []).map((habit) => [`${habit.entryId}:${habit.itemIndex}`, habit])),
  );

  /** O hábito em que um item de rotina caiu, quando o resumo do dia já chegou. */
  habitFor(entryId: string, index: number): ItemHabit | undefined {
    return this.habits().get(`${entryId}:${index}`);
  }

  /**
   * Busca de novo o resumo do dia, sem segurar quem chamou. Os módulos são atualizados antes de a API
   * responder ao lançamento, então basta chamar depois de cada mudança. Numa falha, os cartões ficam
   * com o último resumo.
   */
  async refreshSummary(): Promise<void> {
    const request = ++this.summaryRequests;
    try {
      const summary = await firstValueFrom(this.api.daySummary(dayInSaoPaulo(this.now())));
      // Uma resposta antiga que chega depois de uma mais nova não sobrescreve os cartões.
      if (request === this.summaryRequests) {
        this.daySummary.set(summary);
      }
    } catch {
      // Os cartões mantêm o último valor; a próxima mudança tenta de novo.
    }
  }

  /**
   * Busca os lançamentos de hoje, preservando os que ainda não chegaram ao servidor, e depois envia a
   * fila. Um lançamento guardado que o servidor já tem (a resposta se perdeu) sai da fila.
   */
  async load(): Promise<void> {
    this.loadState.set('loading');
    try {
      const fromServer = await firstValueFrom(this.api.listToday());
      const serverIds = new Set(fromServer.map((entry) => entry.id));
      serverIds.forEach((id) => this.known.add(id));
      this.change((list) => [
        ...fromServer.map(toSynced),
        ...list.filter((entry) => entry.sync !== 'synced' && !serverIds.has(entry.id)),
      ]);
      this.loadState.set('loaded');
      void this.refreshSummary();
    } catch {
      this.loadState.set('error');
    }
    void this.flush();
  }

  /**
   * Registra um lançamento. Texto vazio é ignorado. Sem rede, ele vai direto para a fila do aparelho,
   * sem esperar a falha do envio.
   */
  async submit(text: string): Promise<void> {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    const draft: TimelineEntry = {
      id: this.newId(),
      text: trimmed,
      occurredAt: this.now().toISOString(),
      sync: this.connectivity.online() ? 'sending' : 'queued',
    };
    this.known.add(draft.id);
    this.change((list) => [draft, ...list]);
    if (draft.sync === 'sending') {
      await this.send(draft);
    }
  }

  /** "Enviar agora": tenta um lançamento guardado na hora, com o mesmo id e a mesma data. */
  async retry(id: string): Promise<void> {
    const entry = this.entries().find((candidate) => candidate.id === id);
    if (!entry || entry.sync !== 'queued') {
      return;
    }
    this.patch(id, { sync: 'sending' });
    await this.send(entry);
  }

  /**
   * Envia a fila, um lançamento por vez, do mais antigo ao mais novo. Para na primeira falha de rede
   * ou do servidor: os outros falhariam igual, e a próxima tentativa vem com a rede de volta ou depois
   * de {@link RETRY_DELAY_MS}. Uma recusa (4xx) não para a fila.
   */
  async flush(): Promise<void> {
    if (this.flushing) {
      return;
    }
    this.flushing = true;
    try {
      while (this.connectivity.online()) {
        const next = this.oldestQueued();
        if (!next) {
          return;
        }
        this.patch(next.id, { sync: 'sending' });
        if ((await this.send(next)) === 'queued') {
          return;
        }
      }
    } finally {
      this.flushing = false;
    }
  }

  /** A versão atual de um lançamento já gravado. */
  find(id: string): EntryResponse | undefined {
    return this.entries().find((item) => item.id === id)?.entry;
  }

  /**
   * Resolve um item pendente ou o reabre ({@code REOPEN}: "Não foi isso" ou "Desfazer"). A tela muda na hora (otimista) e é acertada com a
   * resposta da API. Se o lançamento mudou desde a leitura (412) ou o item já foi resolvido (409), o
   * lançamento é recarregado e o resultado é {@code stale}, para a tela reabrir com os dados novos.
   * Em outro erro, a mudança é desfeita.
   */
  async resolve(entryId: string, index: number, request: ResolveItemRequest): Promise<ResolveResult> {
    const current = this.find(entryId);
    if (!current) {
      return 'error';
    }
    this.replaceEntry(applyResolution(current, index, request));
    try {
      this.replaceEntry(await firstValueFrom(this.api.resolve(entryId, index, current.version, request)));
      void this.refreshSummary();
      return 'ok';
    } catch (error) {
      if (error instanceof HttpErrorResponse && (error.status === 412 || error.status === 409)) {
        try {
          this.replaceEntry(await firstValueFrom(this.api.get(entryId)));
        } catch {
          this.replaceEntry(current);
        }
        return 'stale';
      }
      this.replaceEntry(current);
      return 'error';
    }
  }

  private replaceEntry(entry: EntryResponse): void {
    this.change((list) => list.map((item) => (item.id === entry.id ? toSynced(entry) : item)));
  }

  /** Tira da lista (e do aparelho) um lançamento recusado pela API. */
  dismiss(id: string): void {
    this.change((list) => list.filter((entry) => entry.id !== id || entry.sync !== 'rejected'));
  }

  /** Envia um lançamento e diz como ele ficou. Depois de um envio que deu certo, segue com a fila. */
  private async send(entry: TimelineEntry): Promise<SyncState> {
    try {
      const saved = await firstValueFrom(
        this.api.create({ id: entry.id, text: entry.text, occurredAt: entry.occurredAt }),
      );
      this.change((list) => list.map((item) => (item.id === entry.id ? toSynced(saved) : item)));
      void this.refreshSummary();
      void this.flush();
      return 'synced';
    } catch (error) {
      const state = isRetryable(error) ? 'queued' : 'rejected';
      // Se a listagem trouxe a versão do servidor enquanto o envio falhava, ela vale.
      this.change((list) =>
        list.map((item) => (item.id === entry.id && item.sync === 'sending' ? { ...item, sync: state } : item)),
      );
      if (state === 'queued') {
        this.scheduleRetry();
      }
      return state;
    }
  }

  private scheduleRetry(): void {
    if (this.retryTimer !== undefined) {
      return;
    }
    this.retryTimer = setTimeout(() => {
      this.retryTimer = undefined;
      void this.flush();
    }, this.retryDelay);
  }

  private oldestQueued(): TimelineEntry | undefined {
    return this.entries()
      .filter((entry) => entry.sync === 'queued')
      .reduce<TimelineEntry | undefined>(
        (oldest, entry) => (!oldest || Date.parse(entry.occurredAt) < Date.parse(oldest.occurredAt) ? entry : oldest),
        undefined,
      );
  }

  /** Traz para a lista os lançamentos guardados no aparelho que esta aba ainda não conhece. */
  private adoptOutbox(): void {
    const unknown = readOutbox(this.storage).filter((entry) => !this.known.has(entry.id));
    unknown.forEach((entry) => this.known.add(entry.id));
    if (unknown.length > 0) {
      this.entries.update((list) => [...list, ...unknown.map(fromOutbox)]);
    }
  }

  private patch(id: string, changes: Partial<TimelineEntry>): void {
    this.change((list) => list.map((item) => (item.id === id ? { ...item, ...changes } : item)));
  }

  /** Muda a lista e grava no aparelho o que ainda não chegou ao servidor. */
  private change(update: (list: TimelineEntry[]) => TimelineEntry[]): void {
    this.entries.update(update);
    const mine = this.entries()
      .filter((entry) => entry.sync !== 'synced')
      .map(toOutbox);
    const fromOtherTabs = readOutbox(this.storage).filter((entry) => !this.known.has(entry.id));
    writeOutbox(this.storage, [...fromOtherTabs, ...mine]);
  }
}

/**
 * O lançamento como fica depois da resposta do usuário, calculado no app para a tela não esperar a
 * API. A API devolve o mesmo resultado; ele substitui este assim que chega.
 */
export function applyResolution(entry: EntryResponse, index: number, request: ResolveItemRequest): EntryResponse {
  const items = entry.items.map((item, position): EntryItemResponse => {
    if (position !== index) {
      return item;
    }
    if (request.action === 'REOPEN') {
      return {
        ...item,
        needsConfirmation: true,
        resolvedByUser: false,
        dismissed: false,
        state: 'PENDING',
        revision: item.revision + 1,
        canReopen: false,
      };
    }
    const resolved = { ...item, needsConfirmation: false, resolvedByUser: true, revision: item.revision + 1, canReopen: true };
    switch (request.action) {
      case 'DISMISS':
        return { ...resolved, dismissed: true, state: 'DISMISSED' };
      case 'CONFIRM_FINANCE':
        return {
          ...resolved,
          type: 'FINANCE',
          direction: request.direction,
          outcome: null,
          category: request.category,
          amount: request.amount,
          quantity: null,
          unit: null,
          confidence: 1,
          state: 'ACCEPTED_BY_USER',
        };
      case 'CONFIRM_ROUTINE':
        return {
          ...resolved,
          type: 'ROUTINE',
          direction: null,
          outcome: 'DONE',
          category: null,
          amount: null,
          confidence: 1,
          state: 'ACCEPTED_BY_USER',
        };
    }
  });
  const pending = items.some((item) => item.needsConfirmation);
  const status = pending ? 'NEEDS_INPUT' : 'CONFIRMED';
  return { ...entry, items, needsConfirmation: pending, status };
}

function toSynced(entry: EntryResponse): TimelineEntry {
  return { id: entry.id, text: entry.text, occurredAt: entry.occurredAt, sync: 'synced', entry };
}

function toOutbox(entry: TimelineEntry): OutboxEntry {
  return { id: entry.id, text: entry.text, occurredAt: entry.occurredAt, rejected: entry.sync === 'rejected' };
}

/** Um envio interrompido (app fechado no meio) volta como guardado. */
function fromOutbox(entry: OutboxEntry): TimelineEntry {
  return { id: entry.id, text: entry.text, occurredAt: entry.occurredAt, sync: entry.rejected ? 'rejected' : 'queued' };
}

/** Sem resposta (status 0) ou erro do servidor (5xx) vale a pena tentar de novo; 4xx não. */
function isRetryable(error: unknown): boolean {
  return !(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500;
}
