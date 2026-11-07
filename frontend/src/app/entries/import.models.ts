/** Espelha a importação de extrato da API (POST /api/v1/imports, ADR 0014). */
import type { Direction } from './entry.models';

/** IMPORTED: conta nos números. MATCHED: é o mesmo de um lançamento escrito no app. IGNORED: não conta. */
export type ImportedStatus = 'IMPORTED' | 'MATCHED' | 'IGNORED';

export interface ImportedTransaction {
  id: string;
  /** AAAA-MM-DD */
  date: string;
  amount: number;
  direction: Direction;
  description: string | null;
  status: ImportedStatus;
  matchedEntryId: string | null;
  matchedItemIndex: number | null;
}

export interface ImportResponse {
  format: 'OFX' | 'CSV';
  read: number;
  added: number;
  matched: number;
  duplicates: number;
  transactions: ImportedTransaction[];
}
