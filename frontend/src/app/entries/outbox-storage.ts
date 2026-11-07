import { InjectionToken } from '@angular/core';

/** Chave da fila no armazenamento do aparelho. O número muda se o formato mudar. */
export const OUTBOX_KEY = 'mydly.outbox.v1';

/**
 * Lançamento que ainda não chegou ao servidor, guardado no aparelho.
 * {@code rejected} marca um lançamento que a API recusou: ele fica até o usuário removê-lo.
 */
export interface OutboxEntry {
  id: string;
  text: string;
  occurredAt: string;
  rejected: boolean;
}

/**
 * Onde a fila fica guardada. Ler {@code localStorage} pode lançar erro (navegador com dados de site
 * bloqueados); nesse caso não há armazenamento e a fila vale só enquanto o app está aberto.
 */
export const OUTBOX_STORAGE = new InjectionToken<Storage | undefined>('OUTBOX_STORAGE', {
  providedIn: 'root',
  factory: () => {
    try {
      return globalThis.localStorage;
    } catch {
      return undefined;
    }
  },
});

/** Lê a fila. Conteúdo corrompido ou de outro formato é ignorado item a item. */
export function readOutbox(storage: Storage | undefined): OutboxEntry[] {
  try {
    const raw = storage?.getItem(OUTBOX_KEY);
    if (!raw) {
      return [];
    }
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isOutboxEntry) : [];
  } catch {
    return [];
  }
}

/** Grava a fila; vazia, apaga a chave para não deixar rastro no aparelho. */
export function writeOutbox(storage: Storage | undefined, entries: readonly OutboxEntry[]): void {
  try {
    if (entries.length === 0) {
      storage?.removeItem(OUTBOX_KEY);
    } else {
      storage?.setItem(OUTBOX_KEY, JSON.stringify(entries));
    }
  } catch {
    // Armazenamento cheio ou bloqueado: a fila continua valendo na tela aberta.
  }
}

function isOutboxEntry(value: unknown): value is OutboxEntry {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const entry = value as Record<string, unknown>;
  return (
    typeof entry['id'] === 'string' &&
    entry['id'].length > 0 &&
    typeof entry['text'] === 'string' &&
    entry['text'].trim().length > 0 &&
    typeof entry['occurredAt'] === 'string' &&
    !Number.isNaN(Date.parse(entry['occurredAt'])) &&
    typeof entry['rejected'] === 'boolean'
  );
}
