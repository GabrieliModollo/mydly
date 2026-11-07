/**
 * Tipos espelhando o WeekSummaryResponse do backend (GET /api/v1/weeks/{inicio}/summary).
 */
import { Category, Polarity } from './entry.models';

/** Como um dia ficou para um hábito; NONE é dia sem registro e FUTURE, dia que ainda não chegou. */
export type DayMark = 'ACHIEVED' | 'SLIPPED' | 'MISSED' | 'NONE' | 'FUTURE';

/** Tom das mensagens da semana, como a API recebe. */
export type Tone = 'gentle' | 'direct' | 'scold';

export type InsightKind =
  | 'RECURRING_SLIP'
  | 'SPENDING_UP'
  | 'SLEEP_AND_SPENDING'
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

/** Placar do hábito; {@code target} chega com as metas e por enquanto é nulo. */
export interface HabitScore {
  achieved: number;
  elapsedDays: number;
  target: number | null;
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
  /** A semana anterior nos mesmos dias decorridos ({@code comparedDays}); nula sem dados. */
  previous: { spent: number; habitsAchieved: number; comparedDays: number } | null;
  /** No máximo três; a primeira é a mensagem principal. */
  insights: WeekInsight[];
}
