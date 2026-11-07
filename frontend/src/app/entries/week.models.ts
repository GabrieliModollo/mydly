/**
 * Tipos espelhando o WeekSummaryResponse do backend (GET /api/v1/weeks/{inicio}/summary).
 */
import { Category, Polarity } from './entry.models';
import { BudgetPeriod, BudgetStatus, GoalKind } from './goal.models';

/** Como um dia ficou para um hábito; NONE é dia sem registro e FUTURE, dia que ainda não chegou. */
export type DayMark = 'ACHIEVED' | 'SLIPPED' | 'MISSED' | 'NONE' | 'FUTURE';

/** Tom das mensagens da semana, como a API recebe. */
export type Tone = 'gentle' | 'direct' | 'scold';

/** Na ordem de prioridade da API. */
export type InsightKind =
  | 'BUDGET_OVER'
  | 'RECURRING_SLIP'
  | 'GOAL_AT_RISK'
  | 'SPENDING_UP'
  | 'BUDGET_NEAR'
  | 'SLEEP_AND_SPENDING'
  | 'GOAL_MET'
  | 'STREAK'
  | 'OVERVIEW'
  | 'WELCOME';

export interface HabitRow {
  code: string;
  name: string;
  polarity: Polarity;
  /** Os 7 dias, a partir do primeiro dia da semana (segunda por padrão, configurável no servidor). */
  days: DayMark[];
  score: HabitScore;
}

/**
 * Placar do hábito. Sem meta, {@code achieved} de {@code elapsedDays}. Com meta de dias, {@code target}
 * é a meta; com meta diária, os dias da semana com meta. No limite de hábito ruim, {@code limit} e
 * {@code slips} (deslizes) vêm preenchidos e {@code target} é nulo.
 */
export interface HabitScore {
  achieved: number;
  elapsedDays: number;
  target: number | null;
  limit: number | null;
  slips: number | null;
  /** O tipo da meta da semana (a vigente no dia de referência); nulo sem meta. */
  goal: GoalKind | null;
}

/** Orçamento de uma categoria no resumo da semana; o mensal vai do dia 1 ao dia de referência. */
export interface WeekBudget {
  category: Category;
  period: BudgetPeriod;
  limit: number;
  periodStart: string;
  periodEnd: string;
  spent: number;
  percent: number;
  status: BudgetStatus;
}

export interface WeekInsight {
  kind: InsightKind;
  text: string;
  numbers: Record<string, unknown>;
}

export interface WeekSummaryResponse {
  weekStart: string;
  weekEnd: string;
  daysElapsed: number;
  tone: Tone;
  habits: HabitRow[];
  money: {
    spent: number;
    received: number;
    spentByCategory: { category: Category | null; amount: number }[];
  };
  /** As categorias com orçamento no dia de referência da semana. */
  budgets: WeekBudget[];
  /** A semana anterior nos mesmos dias decorridos ({@code comparedDays}); nula sem dados. */
  previous: { spent: number; habitsAchieved: number; comparedDays: number } | null;
  /** No máximo três; a primeira é a mensagem principal. */
  insights: WeekInsight[];
}
