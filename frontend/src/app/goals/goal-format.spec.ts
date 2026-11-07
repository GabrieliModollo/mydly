import { HabitGoalResponse } from '../entries/goal.models';
import { HabitScore, WeekBudget } from '../entries/week.models';
import {
  budgetAlertLabel,
  budgetBarNote,
  budgetSummary,
  dailyProgressLabel,
  dailyProgressText,
  formatGoalQuantity,
  goalScoreLabel,
  goalSummary,
  moneyProgressLabel,
  progressValue,
  progressWidth,
} from './goal-format';

function habit(overrides: Partial<HabitGoalResponse> = {}): HabitGoalResponse {
  return {
    habit: 'HYDRATION',
    name: 'Hidratação',
    polarity: 'GOOD',
    measure: 'VOLUME',
    allowedKinds: ['DAILY_QUANTITY', 'WEEKLY_FREQUENCY'],
    allowedUnits: ['L', 'mL'],
    goal: null,
    ...overrides,
  };
}

function score(overrides: Partial<HabitScore>): HabitScore {
  return { achieved: 3, elapsedDays: 4, target: null, limit: null, slips: null, goal: null, ...overrides };
}

describe('formatação das metas', () => {
  it('quantidade em pt-BR com a unidade', () => {
    expect(formatGoalQuantity(1.5, 'L')).toBe('1,5 L');
    expect(formatGoalQuantity(500, 'mL')).toBe('500 mL');
    expect(formatGoalQuantity(0.83, 'h')).toBe('0,83 h');
  });

  it('progresso da meta diária: "1,5 / 2 L" e por extenso', () => {
    const goal = { habit: 'HYDRATION', habitName: 'Hidratação', target: 2, unit: 'L' as const, done: 1.5, met: false };

    expect(dailyProgressLabel(goal)).toBe('1,5 / 2 L');
    expect(dailyProgressText(goal)).toBe('1,5 de 2 L');
    expect(dailyProgressText({ ...goal, done: 2, met: true })).toBe('2 de 2 L, meta cumprida');
  });

  it('dinheiro contra o limite: "R$ 85,00 de R$ 100,00"', () => {
    expect(moneyProgressLabel(85, 100)).toBe('R$ 85,00 de R$ 100,00');
  });

  it('largura da barra fica entre 0 e 100', () => {
    expect(progressWidth(1.5, 2)).toBe(75);
    expect(progressWidth(3, 2)).toBe(100);
    expect(progressWidth(1, 0)).toBe(0);
  });

  it('aria-valuenow fica entre 0 e o máximo; o valor real vai no texto', () => {
    expect(progressValue(1.5, 2)).toBe(1.5);
    expect(progressValue(2.5, 2)).toBe(2);
    expect(progressValue(120, 100)).toBe(100);
    expect(progressValue(-1, 100)).toBe(0);
    expect(progressValue(5, 0)).toBe(0);
  });

  it('resumo da meta de cada tipo, e "sem meta" ou "sem limite"', () => {
    expect(goalSummary(habit())).toBe('Sem meta');
    expect(goalSummary(habit({ polarity: 'BAD' }))).toBe('Sem limite');
    expect(
      goalSummary(habit({ goal: { kind: 'DAILY_QUANTITY', targetQuantity: 2, unit: 'L', daysPerWeek: null, validFrom: '' } })),
    ).toBe('2 L por dia');
    expect(
      goalSummary(habit({ goal: { kind: 'WEEKLY_FREQUENCY', targetQuantity: null, unit: null, daysPerWeek: 4, validFrom: '' } })),
    ).toBe('4 dias por semana');
    const limit = { kind: 'WEEKLY_FREQUENCY' as const, targetQuantity: null, unit: null, validFrom: '' };
    expect(goalSummary(habit({ polarity: 'BAD', goal: { ...limit, daysPerWeek: 1 } }))).toBe('No máximo 1 dia por semana');
    expect(goalSummary(habit({ polarity: 'BAD', goal: { ...limit, daysPerWeek: 0 } }))).toBe('Nenhum dia por semana');
  });

  it('resumo do orçamento', () => {
    expect(budgetSummary({ category: 'FOOD', period: null, limit: null, validFrom: null, progress: null })).toBe(
      'Sem limite',
    );
    expect(budgetSummary({ category: 'FOOD', period: 'MONTHLY', limit: 800, validFrom: '', progress: null })).toBe(
      'R$ 800,00 por mês',
    );
  });

  it('alerta do Hoje diz a categoria, o percentual e os valores', () => {
    expect(
      budgetAlertLabel({ category: 'FOOD', period: 'WEEKLY', limit: 100, spent: 85, percent: 85, status: 'NEAR' }),
    ).toBe('Alimentação: 85% do limite da semana (R$ 85,00 de R$ 100,00)');
    expect(
      budgetAlertLabel({ category: 'LEISURE', period: 'MONTHLY', limit: 300, spent: 310, percent: 103, status: 'OVER' }),
    ).toBe('Lazer passou do limite do mês: R$ 310,00 de R$ 300,00');
  });

  it('placar contra a meta no Resumo', () => {
    expect(goalScoreLabel(score({}))).toBeNull();
    expect(goalScoreLabel(score({ goal: 'WEEKLY_FREQUENCY', target: 4 }))).toBe('Meta: 3 de 4 dias');
    expect(goalScoreLabel(score({ goal: 'DAILY_QUANTITY', achieved: 2, target: 7 }))).toBe('Meta diária: 2 de 7 dias');
    expect(goalScoreLabel(score({ goal: 'WEEKLY_FREQUENCY', limit: 2, slips: 1 }))).toBe('Limite: 1 de no máximo 2 dias');
    expect(goalScoreLabel(score({ goal: 'WEEKLY_FREQUENCY', limit: 1, slips: 3 }))).toBe('Limite: 3 de no máximo 1 dia');
  });

  it('linha do orçamento sob a barra, semanal e mensal', () => {
    const weekly: WeekBudget = {
      category: 'FOOD',
      period: 'WEEKLY',
      limit: 100,
      periodStart: '2026-11-02',
      periodEnd: '2026-11-08',
      spent: 85,
      percent: 85,
      status: 'NEAR',
    };

    expect(budgetBarNote(weekly)).toBe('Limite da semana: R$ 100,00 (85%, perto do limite)');
    expect(budgetBarNote({ ...weekly, spent: 120, percent: 120, status: 'OVER' })).toBe(
      'Limite da semana: R$ 100,00 (120%, passou do limite)',
    );
    expect(budgetBarNote({ ...weekly, period: 'MONTHLY', limit: 1000, spent: 300, percent: 30, status: 'OK' })).toBe(
      'No mês: R$ 300,00 de R$ 1.000,00 (30%)',
    );
  });
});
