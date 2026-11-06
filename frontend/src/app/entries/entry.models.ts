/**
 * Tipos espelhando o contrato da API (EntryResponse, EntryItemResponse e ResolveItemRequest no
 * backend). Mantenha em sincronia com o Swagger em /swagger-ui.html.
 */

export type EntryType = 'FINANCE' | 'ROUTINE' | 'UNKNOWN';
export type Direction = 'EXPENSE' | 'INCOME';
export type EntryStatus = 'CONFIRMED' | 'NEEDS_INPUT';
export type ItemState = 'PENDING' | 'ACCEPTED_AUTO' | 'ACCEPTED_BY_USER' | 'DISMISSED';
export type RoutineOutcome = 'DONE' | 'MISSED';
export type Category =
  | 'FOOD'
  | 'GROCERIES'
  | 'PHARMACY'
  | 'TRANSPORT'
  | 'HOUSING'
  | 'HEALTH'
  | 'LEISURE'
  | 'WORK'
  | 'OTHER';

export interface EntryItemResponse {
  type: EntryType;
  direction: Direction | null;
  /** Só em ROUTINE: se o hábito foi cumprido. */
  outcome: RoutineOutcome | null;
  /** Só em FINANCE: sugerida pelo parser ou escolhida pelo usuário. */
  category: Category | null;
  amount: number | null;
  quantity: number | null;
  unit: string | null;
  description: string | null;
  confidence: number;
  /** Ainda falta o usuário confirmar. */
  needsConfirmation: boolean;
  /** O usuário confirmou ou descartou este item. */
  resolvedByUser: boolean;
  /** O usuário disse que não era nada a registrar. */
  dismissed: boolean;
  state: ItemState;
  revision: number;
  canReopen: boolean;
}

export interface EntryResponse {
  id: string;
  text: string;
  occurredAt: string;
  status: EntryStatus;
  needsConfirmation: boolean;
  /** Versão do lançamento; vai no If-Match ao resolver um item. */
  version: number;
  items: EntryItemResponse[];
}

export interface CreateEntryRequest {
  id: string;
  text: string;
  occurredAt: string;
}

export type ResolveItemRequest =
  | { action: 'CONFIRM_FINANCE'; amount: number; direction: Direction; category: Category | null }
  | { action: 'CONFIRM_ROUTINE' }
  | { action: 'DISMISS' }
  | { action: 'REOPEN' };

export type Polarity = 'GOOD' | 'BAD';
export type HabitStatus = 'ACHIEVED' | 'SLIPPED' | 'MISSED';

/** Hábito do catálogo em que um item de rotina caiu; {@code habit} nulo = outros. */
export interface ItemHabit {
  entryId: string;
  itemIndex: number;
  habit: string | null;
  habitName: string | null;
  polarity: Polarity | null;
  status: HabitStatus | null;
}

/** Espelha o DaySummaryResponse do backend (GET /api/v1/days/{date}/summary). */
export interface DaySummaryResponse {
  date: string;
  spent: number;
  received: number;
  habitsAchieved: number;
  slips: number;
  spentByCategory: { category: Category | null; amount: number }[];
  items: ItemHabit[];
}
