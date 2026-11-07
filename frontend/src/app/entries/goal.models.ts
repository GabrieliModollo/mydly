/**
 * Tipos espelhando as APIs de metas, orçamentos e preferências do backend (HabitGoalResponse,
 * BudgetResponse e Preferences). Mantenha em sincronia com o Swagger em /swagger-ui.html.
 */
import { Category, Polarity } from './entry.models';

/** Tipo de meta de hábito. Num hábito ruim, WEEKLY_FREQUENCY é um limite (no máximo n dias). */
export type GoalKind = 'DAILY_QUANTITY' | 'WEEKLY_FREQUENCY';
export type Measure = 'VOLUME' | 'DURATION' | 'NONE';
export type GoalUnit = 'L' | 'mL' | 'h' | 'min';

export interface HabitGoal {
  kind: GoalKind;
  /** Só em DAILY_QUANTITY. */
  targetQuantity: number | null;
  unit: GoalUnit | null;
  /** Só em WEEKLY_FREQUENCY; num hábito ruim é o máximo de dias. */
  daysPerWeek: number | null;
  validFrom: string;
}

/** Espelha o HabitGoalResponse (GET /api/v1/goals/habits). */
export interface HabitGoalResponse {
  habit: string;
  name: string;
  polarity: Polarity;
  measure: Measure;
  allowedKinds: GoalKind[];
  allowedUnits: GoalUnit[];
  /** A meta vigente hoje; nula quando o hábito está sem meta. */
  goal: HabitGoal | null;
}

export type HabitGoalRequest =
  | { kind: 'DAILY_QUANTITY'; targetQuantity: number; unit: GoalUnit }
  | { kind: 'WEEKLY_FREQUENCY'; daysPerWeek: number };

export type BudgetPeriod = 'WEEKLY' | 'MONTHLY';
/** OK abaixo de 80%; NEAR de 80% a 100%; OVER acima do limite. */
export type BudgetStatus = 'OK' | 'NEAR' | 'OVER';

export interface BudgetProgress {
  periodStart: string;
  periodEnd: string;
  spent: number;
  /** Arredondado para baixo: 79,9% é 79. */
  percent: number;
  status: BudgetStatus;
}

/** Espelha o BudgetResponse (GET /api/v1/budgets). Sem orçamento, só a categoria vem preenchida. */
export interface BudgetResponse {
  category: Category;
  period: BudgetPeriod | null;
  limit: number | null;
  validFrom: string | null;
  progress: BudgetProgress | null;
}

export interface BudgetRequest {
  period: BudgetPeriod;
  limit: number;
}

export type DayOfWeekName = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface Preferences {
  firstDayOfWeek: DayOfWeekName;
}

/** Progresso de uma meta diária no resumo do dia, como 1,5 de 2 L. */
export interface DailyGoal {
  habit: string;
  habitName: string;
  target: number;
  unit: GoalUnit;
  /** Arredondado para baixo em duas casas, para nunca parecer cumprida sem estar. */
  done: number;
  met: boolean;
}

/** Categoria em aviso (NEAR) ou acima do limite (OVER) no período que contém o dia. */
export interface BudgetAlert {
  category: Category;
  period: BudgetPeriod;
  limit: number;
  spent: number;
  percent: number;
  status: Exclude<BudgetStatus, 'OK'>;
}
