import { CATEGORY_LABELS, formatMoney } from '../entries/entry-format';
import { Category } from '../entries/entry.models';
import { DayMark, HabitRow, Tone } from '../entries/week.models';

/** Iniciais e nomes por dia da semana do JavaScript (0 = domingo), como no protótipo. */
const INITIALS = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'] as const;
const NAMES = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'] as const;

function weekdayOf(isoDate: string): number {
  const { day, month, year } = parts(isoDate);
  return new Date(Date.UTC(year, month, day)).getUTCDay();
}

/** Os nomes dos 7 dias a partir do primeiro dia da semana; nada aqui supõe que ela começa na segunda. */
export function dayNames(weekStart: string): string[] {
  const first = weekdayOf(weekStart);
  return Array.from({ length: 7 }, (_, index) => NAMES[(first + index) % 7]);
}

/** As iniciais dos 7 dias a partir do primeiro dia da semana: "S T Q Q S S D" quando começa na segunda. */
export function dayInitials(weekStart: string): string[] {
  const first = weekdayOf(weekStart);
  return Array.from({ length: 7 }, (_, index) => INITIALS[(first + index) % 7]);
}

export const TONE_OPTIONS: readonly { tone: Tone; label: string }[] = [
  { tone: 'gentle', label: 'Gentil' },
  { tone: 'direct', label: 'Direto' },
  { tone: 'scold', label: 'Bronca' },
];

const MONTHS = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
];

function parts(isoDate: string): { day: number; month: number; year: number } {
  const [year, month, day] = isoDate.split('-').map(Number);
  return { day, month: month - 1, year };
}

/** {@code 2026-11-02} a {@code 2026-11-08} → "2 a 8 de novembro"; meses diferentes repetem o mês. */
export function formatWeekRange(start: string, end: string): string {
  const from = parts(start);
  const to = parts(end);
  if (from.month === to.month) {
    return `${from.day} a ${to.day} de ${MONTHS[to.month]}`;
  }
  const yearSuffix = from.year === to.year ? '' : ` de ${from.year}`;
  return `${from.day} de ${MONTHS[from.month]}${yearSuffix} a ${to.day} de ${MONTHS[to.month]}`;
}

/** A data {@code days} dias depois (ou antes, se negativo), em {@code AAAA-MM-DD}. */
export function shiftDate(isoDate: string, days: number): string {
  const { day, month, year } = parts(isoDate);
  const shifted = new Date(Date.UTC(year, month, day + days));
  return shifted.toISOString().slice(0, 10);
}

/** O que um ponto significa, no hábito bom ou ruim. */
export function markLabel(mark: DayMark, polarity: HabitRow['polarity']): string {
  switch (mark) {
    case 'ACHIEVED':
      return polarity === 'BAD' ? 'evitado' : 'cumprido';
    case 'SLIPPED':
      return 'deslize';
    case 'MISSED':
      return 'não cumprido';
    case 'NONE':
      return 'sem registro';
    case 'FUTURE':
      return 'ainda não chegou';
  }
}

/**
 * Descrição dos 7 pontos para leitor de tela: "Hidratação, 2 de 3 dias. Segunda: cumprido. Terça: ...".
 * Os dias que ainda não chegaram são agrupados, para a leitura não ficar longa.
 */
export function habitDescription(habit: HabitRow, weekStart: string): string {
  const names = dayNames(weekStart);
  const known = habit.days
    .map((mark, index) => ({ mark, name: names[index] }))
    .filter(({ mark }) => mark !== 'FUTURE')
    .map(({ mark, name }) => `${name}: ${markLabel(mark, habit.polarity)}`);
  const future = habit.days.filter((mark) => mark === 'FUTURE').length;
  const elapsed = habit.score.elapsedDays;
  const days = elapsed === 1 ? '1 dia' : `${elapsed} dias`;
  const sentences = [`${habit.name}, ${habit.score.achieved} de ${days}`, ...known];
  if (future > 0) {
    sentences.push(future === 1 ? `${names[6]} ainda não chegou` : `os próximos ${future} dias ainda não chegaram`);
  }
  return sentences.join('. ') + '.';
}

export interface CategoryBar {
  label: string;
  amount: string;
  /** Largura da barra, de 0 a 100, proporcional à maior categoria. */
  percent: number;
}

/** Barras dos gastos por categoria, já na ordem da API (do maior para o menor). */
export function categoryBars(totals: { category: Category | null; amount: number }[]): CategoryBar[] {
  const max = Math.max(0, ...totals.map((total) => total.amount));
  return totals.map((total) => ({
    label: total.category ? CATEGORY_LABELS[total.category] : 'Sem categoria',
    amount: formatMoney(total.amount),
    percent: max > 0 ? Math.round((total.amount / max) * 100) : 0,
  }));
}

/**
 * Linha de comparação com a semana anterior nos mesmos dias decorridos ("Semana anterior, até quarta:
 * ..."), ou {@code null} quando ela não tem dados.
 */
export function previousWeekLabel(
  previous: { spent: number; habitsAchieved: number; comparedDays: number } | null,
  weekStart: string,
): string | null {
  if (!previous) {
    return null;
  }
  const habits = previous.habitsAchieved === 1 ? '1 hábito cumprido' : `${previous.habitsAchieved} hábitos cumpridos`;
  const upTo = previous.comparedDays < 7 ? `, até ${dayNames(weekStart)[previous.comparedDays - 1]}` : '';
  return `Semana anterior${upTo}: ${formatMoney(previous.spent)} em gastos e ${habits}.`;
}
