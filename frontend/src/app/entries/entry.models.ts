/**
 * Tipos espelhando o contrato da API (EntryResponse e EntryItemResponse no backend).
 * Mantenha em sincronia com o Swagger em /swagger-ui.html.
 */

export type EntryType = 'FINANCE' | 'ROUTINE' | 'UNKNOWN';
export type Direction = 'EXPENSE' | 'INCOME';
export type EntryStatus = 'PENDING' | 'CONFIRMED' | 'NEEDS_INPUT';
export type RoutineOutcome = 'DONE' | 'MISSED';

export interface EntryItemResponse {
  type: EntryType;
  direction: Direction | null;
  /** Só em ROUTINE: se o hábito foi cumprido. */
  outcome: RoutineOutcome | null;
  amount: number | null;
  quantity: number | null;
  unit: string | null;
  description: string | null;
  confidence: number;
}

export interface EntryResponse {
  id: string;
  text: string;
  occurredAt: string;
  status: EntryStatus;
  needsConfirmation: boolean;
  items: EntryItemResponse[];
}

export interface CreateEntryRequest {
  id: string;
  text: string;
  occurredAt: string;
}
