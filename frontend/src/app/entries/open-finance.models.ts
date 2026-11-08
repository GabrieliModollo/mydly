/** Espelha o Open Finance da API (/api/v1/open-finance, ADR 0015). */
import type { ImportResponse } from './import.models';

export interface OpenFinanceConnection {
  id: string;
  institution: string | null;
  /** Status do item no Pluggy: UPDATED, UPDATING, LOGIN_ERROR, OUTDATED... */
  status: string;
  lastSyncedAt: string | null;
}

export interface OpenFinanceStatus {
  /** false quando o servidor não tem as chaves do Pluggy. */
  enabled: boolean;
  connections: OpenFinanceConnection[];
}

export interface OpenFinanceSync {
  connection: OpenFinanceConnection;
  /** Resumo da importação; nulo quando o banco ainda não estava pronto. */
  imported: ImportResponse | null;
}
