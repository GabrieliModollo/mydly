import { HabitRow } from '../entries/week.models';
import {
  categoryBars,
  dayInitials,
  formatWeekRange,
  habitDescription,
  previousWeekLabel,
  shiftDate,
} from './summary-format';

describe('formatação do resumo da semana', () => {
  it('período no mesmo mês e em meses ou anos diferentes', () => {
    expect(formatWeekRange('2026-11-02', '2026-11-08')).toBe('2 a 8 de novembro');
    expect(formatWeekRange('2026-10-26', '2026-11-01')).toBe('26 de outubro a 1 de novembro');
    expect(formatWeekRange('2026-12-28', '2027-01-03')).toBe('28 de dezembro de 2026 a 3 de janeiro');
  });

  it('anda de semana em semana atravessando meses e anos', () => {
    expect(shiftDate('2026-11-02', -7)).toBe('2026-10-26');
    expect(shiftDate('2026-12-28', 7)).toBe('2027-01-04');
  });

  it('descreve os 7 pontos para leitor de tela, com a polaridade', () => {
    const habit: HabitRow = {
      code: 'JUNK_FOOD',
      name: 'Besteira',
      polarity: 'BAD',
      days: ['ACHIEVED', 'SLIPPED', 'NONE', 'FUTURE', 'FUTURE', 'FUTURE', 'FUTURE'],
      score: { achieved: 1, elapsedDays: 3, target: null, limit: null, slips: null, goal: null },
    };

    expect(habitDescription(habit, '2026-11-02')).toBe(
      'Besteira, 1 de 3 dias. segunda: evitado. terça: deslize. quarta: sem registro. os próximos 4 dias ainda não chegaram.',
    );
  });

  it('hábito bom não cumprido diz "não cumprido"', () => {
    const habit: HabitRow = {
      code: 'GYM',
      name: 'Academia',
      polarity: 'GOOD',
      days: ['ACHIEVED', 'MISSED', 'ACHIEVED', 'ACHIEVED', 'ACHIEVED', 'ACHIEVED', 'FUTURE'],
      score: { achieved: 5, elapsedDays: 6, target: null, limit: null, slips: null, goal: null },
    };

    expect(habitDescription(habit, '2026-11-02')).toContain('terça: não cumprido');
    expect(habitDescription(habit, '2026-11-02')).toContain('domingo ainda não chegou');
  });

  it('semana que começa no domingo: iniciais e nomes seguem o primeiro dia', () => {
    expect(dayInitials('2026-11-02')).toEqual(['S', 'T', 'Q', 'Q', 'S', 'S', 'D']);
    expect(dayInitials('2026-11-01')).toEqual(['D', 'S', 'T', 'Q', 'Q', 'S', 'S']);

    const habit: HabitRow = {
      code: 'GYM',
      name: 'Academia',
      polarity: 'GOOD',
      days: ['ACHIEVED', 'NONE', 'NONE', 'NONE', 'NONE', 'NONE', 'FUTURE'],
      score: { achieved: 1, elapsedDays: 6, target: null, limit: null, slips: null, goal: null },
    };
    expect(habitDescription(habit, '2026-11-01')).toContain('domingo: cumprido');
    expect(habitDescription(habit, '2026-11-01')).toContain('sábado ainda não chegou');
  });

  it('barras proporcionais à maior categoria, com nome em português', () => {
    expect(
      categoryBars([
        { category: 'FOOD', amount: 120 },
        { category: null, amount: 30 },
      ]),
    ).toEqual([
      { label: 'Alimentação', amount: 'R$ 120,00', percent: 100 },
      { label: 'Sem categoria', amount: 'R$ 30,00', percent: 25 },
    ]);
  });

  it('linha da semana anterior só quando ela tem dados', () => {
    expect(previousWeekLabel(null, '2026-11-02')).toBeNull();
    expect(previousWeekLabel({ spent: 250, habitsAchieved: 1, comparedDays: 7 }, '2026-11-02')).toBe(
      'Semana anterior: R$ 250,00 em gastos e 1 hábito cumprido.',
    );
  });

  it('no meio da semana, a semana anterior é comparada até o mesmo dia', () => {
    expect(previousWeekLabel({ spent: 100, habitsAchieved: 4, comparedDays: 3 }, '2026-11-02')).toBe(
      'Semana anterior, até quarta: R$ 100,00 em gastos e 4 hábitos cumpridos.',
    );
  });
});
