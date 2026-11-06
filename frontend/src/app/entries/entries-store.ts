import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { EntriesApi } from './entries-api';
import { daySummary } from './entry-format';
import { EntryItemResponse, EntryResponse, ResolveItemRequest } from './entry.models';
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

/**
 * Situação de um lançamento no app:
 * - sending: enviado, aguardando a API;
 * - synced: gravado e interpretado pela API;
 * - failed: falha de rede ou do servidor; pode ser reenviado com o mesmo id;
 * - rejected: a API recusou (4xx); reenviar não adianta.
 */
export type SyncState = 'sending' | 'synced' | 'failed' | 'rejected';

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
 * Estado da tela "Hoje": os lançamentos do dia e os que estão sendo enviados.
 *
 * O lançamento aparece na lista assim que é enviado (estado "sending") e é substituído pela
 * resposta da API quando ela chega. Como o id e a data são gerados aqui, um reenvio depois de
 * falha de rede não duplica nada (ADR 0006).
 */
@Injectable({ providedIn: 'root' })
export class EntriesStore {
  private readonly api = inject(EntriesApi);
  private readonly newId = inject(NEW_ENTRY_ID);
  private readonly now = inject(NOW);

  private readonly entries = signal<TimelineEntry[]>([]);

  readonly loadState = signal<LoadState>('idle');

  /** Lançamentos do mais recente para o mais antigo. */
  readonly timeline = computed(() =>
    [...this.entries()].sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt)),
  );

  /** Totais do dia, só com o que a API já interpretou. */
  readonly summary = computed(() =>
    daySummary(this.entries().flatMap((item) => (item.entry ? [item.entry] : []))),
  );

  /** Busca os lançamentos de hoje, preservando os que ainda não chegaram ao servidor. */
  async load(): Promise<void> {
    this.loadState.set('loading');
    try {
      const fromServer = await firstValueFrom(this.api.listToday());
      const serverIds = new Set(fromServer.map((entry) => entry.id));
      const notYetOnServer = this.entries().filter((entry) => entry.sync !== 'synced' && !serverIds.has(entry.id));
      this.entries.set([...fromServer.map(toSynced), ...notYetOnServer]);
      this.loadState.set('loaded');
    } catch {
      this.loadState.set('error');
    }
  }

  /** Registra um lançamento. Texto vazio é ignorado. */
  async submit(text: string): Promise<void> {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    const draft: TimelineEntry = {
      id: this.newId(),
      text: trimmed,
      occurredAt: this.now().toISOString(),
      sync: 'sending',
    };
    this.entries.update((list) => [draft, ...list]);
    await this.send(draft);
  }

  /** Reenvia um lançamento que falhou, com o mesmo id e a mesma data. */
  async retry(id: string): Promise<void> {
    const entry = this.entries().find((candidate) => candidate.id === id);
    if (!entry || entry.sync !== 'failed') {
      return;
    }
    this.patch(id, { sync: 'sending' });
    await this.send(entry);
  }

  /** A versão atual de um lançamento já gravado. */
  find(id: string): EntryResponse | undefined {
    return this.entries().find((item) => item.id === id)?.entry;
  }

  /**
   * Resolve um item que precisava de confirmação. A tela muda na hora (otimista) e é acertada com a
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
    this.entries.update((list) => list.map((item) => (item.id === entry.id ? toSynced(entry) : item)));
  }

  /** Tira da lista um lançamento recusado pela API. */
  dismiss(id: string): void {
    this.entries.update((list) => list.filter((entry) => entry.id !== id || entry.sync !== 'rejected'));
  }

  private async send(entry: TimelineEntry): Promise<void> {
    try {
      const saved = await firstValueFrom(
        this.api.create({ id: entry.id, text: entry.text, occurredAt: entry.occurredAt }),
      );
      this.entries.update((list) => list.map((item) => (item.id === entry.id ? toSynced(saved) : item)));
    } catch (error) {
      this.patch(entry.id, { sync: isRetryable(error) ? 'failed' : 'rejected' });
    }
  }

  private patch(id: string, changes: Partial<TimelineEntry>): void {
    this.entries.update((list) => list.map((item) => (item.id === id ? { ...item, ...changes } : item)));
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
    const resolved = { ...item, needsConfirmation: false, resolvedByUser: true };
    switch (request.action) {
      case 'DISMISS':
        return { ...resolved, dismissed: true };
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
        };
      case 'CONFIRM_ROUTINE':
        return { ...resolved, type: 'ROUTINE', direction: null, outcome: 'DONE', category: null, amount: null, confidence: 1 };
    }
  });
  const pending = items.some((item) => item.needsConfirmation);
  const status = !pending && entry.status === 'NEEDS_INPUT' ? 'PENDING' : entry.status;
  return { ...entry, items, needsConfirmation: pending, status };
}

function toSynced(entry: EntryResponse): TimelineEntry {
  return { id: entry.id, text: entry.text, occurredAt: entry.occurredAt, sync: 'synced', entry };
}

/** Sem resposta (status 0) ou erro do servidor (5xx) vale a pena tentar de novo; 4xx não. */
function isRetryable(error: unknown): boolean {
  return !(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500;
}
