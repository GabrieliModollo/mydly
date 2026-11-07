import { CATEGORY_LABELS, formatMoney } from '../entries/entry-format';
import {
  BudgetAlert,
  BudgetPeriod,
  BudgetResponse,
  BudgetStatus,
  DailyGoal,
  DayOfWeekName,
  GoalUnit,
  HabitGoalResponse,
} from '../entries/goal.models';
import { HabitScore, WeekBudget } from '../entries/week.models';

const quantity = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });

/** {@code 1.5, 'L'} → "1,5 L"; {@code 30, 'min'} → "30 min". */
export function formatGoalQuantity(value: number, unit: GoalUnit): string {
  return `${quantity.format(value)} ${unit}`;
}

/** Progresso de uma meta diária no cartão: "1,5 / 2 L". */
export function dailyProgressLabel(goal: DailyGoal): string {
  return `${quantity.format(goal.done)} / ${formatGoalQuantity(goal.target, goal.unit)}`;
}

/** O mesmo progresso por extenso, para leitor de tela: "1,5 de 2 L, meta cumprida". */
export function dailyProgressText(goal: DailyGoal): string {
  const status = goal.met ? ', meta cumprida' : '';
  return `${quantity.format(goal.done)} de ${formatGoalQuantity(goal.target, goal.unit)}${status}`;
}

/** "R$ 85,00 de R$ 100,00". */
export function moneyProgressLabel(spent: number, limit: number): string {
  return `${formatMoney(spent)} de ${formatMoney(limit)}`;
}

/** Largura de uma barra de progresso, de 0 a 100; o que passa do alvo fica cheio. */
export function progressWidth(value: number, max: number): number {
  if (max <= 0) {
    return 0;
  }
  return Math.max(0, Math.min(100, Math.round((value / max) * 100)));
}

/**
 * O {@code aria-valuenow} de uma barra de progresso, sempre entre 0 e {@code max}: o ARIA não aceita
 * valor acima do máximo. O valor real (2,5 de 2 L, R$ 120,00 de R$ 100,00) vai no {@code aria-valuetext}.
 */
export function progressValue(value: number, max: number): number {
  if (max <= 0) {
    return 0;
  }
  return Math.min(Math.max(value, 0), max);
}

function days(count: number): string {
  return count === 1 ? '1 dia' : `${count} dias`;
}

/** A meta atual de um hábito em uma linha: "2 L por dia", "4 dias por semana", "No máximo 2 dias por semana". */
export function goalSummary(habit: HabitGoalResponse): string {
  const goal = habit.goal;
  if (!goal) {
    return habit.polarity === 'BAD' ? 'Sem limite' : 'Sem meta';
  }
  if (goal.kind === 'DAILY_QUANTITY' && goal.targetQuantity !== null && goal.unit !== null) {
    return `${formatGoalQuantity(goal.targetQuantity, goal.unit)} por dia`;
  }
  const count = goal.daysPerWeek ?? 0;
  if (habit.polarity === 'BAD') {
    return count === 0 ? 'Nenhum dia por semana' : `No máximo ${days(count)} por semana`;
  }
  return `${days(count)} por semana`;
}

export const PERIOD_LABELS: Record<BudgetPeriod, string> = { WEEKLY: 'semana', MONTHLY: 'mês' };

/** O orçamento atual de uma categoria: "R$ 100,00 por semana" ou "Sem limite". */
export function budgetSummary(budget: BudgetResponse): string {
  if (budget.limit === null || budget.period === null) {
    return 'Sem limite';
  }
  return `${formatMoney(budget.limit)} por ${PERIOD_LABELS[budget.period]}`;
}

/** O que o status de um orçamento quer dizer, por escrito: a cor nunca é a única pista. */
export function budgetStatusLabel(percent: number, status: BudgetStatus): string {
  switch (status) {
    case 'OVER':
      return `${percent}%, passou do limite`;
    case 'NEAR':
      return `${percent}%, perto do limite`;
    default:
      return `${percent}%`;
  }
}

function periodOf(period: BudgetPeriod): string {
  return period === 'WEEKLY' ? 'da semana' : 'do mês';
}

/** Alerta do Hoje: "Alimentação: 85% do limite da semana (R$ 85,00 de R$ 100,00)". */
export function budgetAlertLabel(alert: BudgetAlert): string {
  const name = CATEGORY_LABELS[alert.category];
  const amounts = moneyProgressLabel(alert.spent, alert.limit);
  if (alert.status === 'OVER') {
    return `${name} passou do limite ${periodOf(alert.period)}: ${amounts}`;
  }
  return `${name}: ${alert.percent}% do limite ${periodOf(alert.period)} (${amounts})`;
}

/**
 * Placar contra a meta no Resumo: "Meta: 3 de 4 dias", "Meta diária: 2 de 7 dias" ou, num hábito
 * ruim, "Limite: 1 de no máximo 2 dias". Nulo sem meta.
 */
export function goalScoreLabel(score: HabitScore): string | null {
  if (score.goal === null) {
    return null;
  }
  if (score.limit !== null && score.slips !== null) {
    return `Limite: ${score.slips} de no máximo ${days(score.limit)}`;
  }
  if (score.target === null) {
    return null;
  }
  const prefix = score.goal === 'DAILY_QUANTITY' ? 'Meta diária' : 'Meta';
  return `${prefix}: ${score.achieved} de ${days(score.target)}`;
}

/**
 * A linha do orçamento sob a barra da categoria no Resumo. O semanal é a própria semana; o mensal é o
 * mês até o dia de referência, e por isso não vira marca na barra da semana.
 */
export function budgetBarNote(budget: WeekBudget): string {
  if (budget.period === 'MONTHLY') {
    return `No mês: ${moneyProgressLabel(budget.spent, budget.limit)} (${budgetStatusLabel(budget.percent, budget.status)})`;
  }
  return `Limite da semana: ${formatMoney(budget.limit)} (${budgetStatusLabel(budget.percent, budget.status)})`;
}

export const DAY_OF_WEEK_OPTIONS: readonly { value: DayOfWeekName; label: string }[] = [
  { value: 'MONDAY', label: 'Segunda' },
  { value: 'TUESDAY', label: 'Terça' },
  { value: 'WEDNESDAY', label: 'Quarta' },
  { value: 'THURSDAY', label: 'Quinta' },
  { value: 'FRIDAY', label: 'Sexta' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];
