import { Category, DaySummaryResponse, EntryItemResponse, EntryResponse, ItemHabit } from './entry.models';

/** Nomes das categorias em português, na ordem em que o app as mostra. */
export const CATEGORY_LABELS: Record<Category, string> = {
  FOOD: 'Alimentação',
  GROCERIES: 'Mercado',
  PHARMACY: 'Farmácia',
  TRANSPORT: 'Transporte',
  HOUSING: 'Moradia',
  HEALTH: 'Saúde',
  LEISURE: 'Lazer',
  WORK: 'Trabalho',
  OTHER: 'Outros',
};

/**
 * Valor digitado pelo usuário em reais: {@code 8,50}, {@code 8.5}, {@code 1.250,90} ou {@code R$ 12}.
 * Devolve {@code null} quando não é um valor positivo com até duas casas decimais.
 */
export function parseAmount(text: string): number | null {
  const cleaned = text.replace(/r\$/i, '').replace(/\s/g, '');
  const brazilian = /^\d{1,3}(\.\d{3})+(,\d{1,2})?$|^\d+(,\d{1,2})?$/;
  const dotDecimal = /^\d+\.\d{1,2}$/;
  let normalized: string;
  if (brazilian.test(cleaned)) {
    normalized = cleaned.replace(/\./g, '').replace(',', '.');
  } else if (dotDecimal.test(cleaned)) {
    normalized = cleaned;
  } else {
    return null;
  }
  const value = Number(normalized);
  return value > 0 ? value : null;
}

/** Tom visual de um chip; cada um tem fundo e texto próprios no tema (docs/design.md). */
export type ChipTone = 'expense' | 'income' | 'routine' | 'missed' | 'slip' | 'attention' | 'neutral';

export interface Chip {
  label: string;
  tone: ChipTone;
}

/** Totais do dia mostrados nos cartões de resumo. */
export interface DaySummary {
  /** Soma das saídas que valem (aceitas pelo parser ou confirmadas), calculada pela API. */
  spent: number;
  /** Hábitos bons cumpridos mais hábitos ruins evitados, calculados pela API. */
  habitsDone: number;
}

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const decimal = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });

/** {@code 1250.9} → {@code R$ 1.250,90}. */
export function formatMoney(amount: number): string {
  // O Intl usa espaço não separável (U+00A0) entre o símbolo e o valor; \s inclui esse caractere,
  // e trocamos por espaço comum para a leitura e os testes ficarem previsíveis.
  return money.format(amount).replace(/\s/g, ' ');
}

/** Quantidade com unidade em português: {@code 2.5 L} → {@code 2,5 L}, {@code 3 copo} → {@code 3 copos}. */
export function formatQuantity(quantity: number, unit: string | null): string {
  const value = decimal.format(quantity);
  if (unit === null) {
    return value;
  }
  if (unit === 'copo') {
    return `${value} ${quantity === 1 ? 'copo' : 'copos'}`;
  }
  return `${value} ${unit}`;
}

/** Chip que resume um item interpretado. O rótulo sempre diz o tipo: a cor nunca é a única pista. */
export function itemChip(item: EntryItemResponse): Chip {
  switch (item.type) {
    case 'FINANCE': {
      const income = item.direction === 'INCOME';
      const kind = income ? 'Entrada' : 'Saída';
      const value = item.amount === null ? 'valor a definir' : formatMoney(item.amount);
      return { label: `${kind} · ${value}`, tone: income ? 'income' : 'expense' };
    }
    case 'ROUTINE':
      return item.outcome === 'MISSED'
        ? { label: 'Rotina · não cumprido', tone: 'missed' }
        : { label: routineLabel(item), tone: 'routine' };
    default:
      return { label: 'Não entendi', tone: 'neutral' };
  }
}

function routineLabel(item: EntryItemResponse): string {
  if (item.quantity === null) {
    return 'Rotina';
  }
  // Contagem sem unidade ("fiz 30 flexões"): o "×" deixa claro que é quantidade de vezes.
  return item.unit === null ? `Rotina · ${formatQuantity(item.quantity, null)}×` : `Rotina · ${formatQuantity(item.quantity, item.unit)}`;
}

/**
 * Chip de uma rotina que caiu num hábito do catálogo (docs/design.md). Num hábito ruim, a ação não
 * acontecer é conquista ("Evitou besteira") e acontecer é deslize, com cor própria.
 */
export function habitChip(item: EntryItemResponse, habit: ItemHabit): Chip | null {
  if (!habit.habitName || !habit.status) {
    return null;
  }
  const name = habit.habitName;
  switch (habit.status) {
    case 'SLIPPED':
      return { label: `Deslize · ${name}`, tone: 'slip' };
    case 'MISSED':
      return { label: `${name} · não cumprido`, tone: 'missed' };
    case 'ACHIEVED':
      if (habit.polarity === 'BAD') {
        return { label: `Evitou ${name.toLowerCase()}`, tone: 'routine' };
      }
      return { label: item.quantity === null ? name : `${name} · ${quantityLabel(item)}`, tone: 'routine' };
  }
}

function quantityLabel(item: EntryItemResponse): string {
  const quantity = item.quantity ?? 0;
  return item.unit === null ? `${formatQuantity(quantity, null)}×` : formatQuantity(quantity, item.unit);
}

/**
 * Chips de um item. Quando ele precisa de confirmação, mostra só esse aviso: a interpretação ainda é
 * incerta, e um chip como "Saída · valor a definir" só repetiria a dúvida. Finanças ganham também o
 * chip da categoria, e rotinas mostram o hábito do catálogo quando ele é conhecido.
 */
export function chipsForItem(item: EntryItemResponse, habit?: ItemHabit): Chip[] {
  if (item.needsConfirmation) {
    return [{ label: 'Precisa confirmar', tone: 'attention' }];
  }
  if (item.dismissed) {
    return [{ label: 'Descartado', tone: 'neutral' }];
  }
  const fromHabit = item.type === 'ROUTINE' && habit ? habitChip(item, habit) : null;
  const chips = [fromHabit ?? itemChip(item)];
  if (item.type === 'FINANCE' && item.category) {
    chips.push({ label: CATEGORY_LABELS[item.category], tone: 'neutral' });
  }
  return chips;
}

/** Chips de todos os itens de um lançamento. */
export function chipsFor(entry: EntryResponse): Chip[] {
  return entry.items.flatMap((item) => chipsForItem(item));
}

/** Os cartões de resumo a partir da resposta da API; zerados enquanto ela não chega. */
export function summaryFrom(response: DaySummaryResponse | null): DaySummary {
  return response ? { spent: response.spent, habitsDone: response.habitsAchieved } : { spent: 0, habitsDone: 0 };
}

/** O dia de uma data em São Paulo, no formato {@code AAAA-MM-DD}, que é o fuso dos totais da API. */
export function dayInSaoPaulo(date: Date): string {
  // O locale en-CA formata como AAAA-MM-DD.
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(date);
}

/** Aviso da tela Hoje sem conexão, com quantos lançamentos esperam no aparelho. */
export function offlineNotice(waiting: number): string {
  if (waiting <= 0) {
    return 'Sem conexão. O que você lançar fica guardado no aparelho e é enviado quando a internet voltar.';
  }
  if (waiting === 1) {
    return 'Sem conexão. 1 lançamento guardado no aparelho, enviado quando a internet voltar.';
  }
  return `Sem conexão. ${waiting} lançamentos guardados no aparelho, enviados quando a internet voltar.`;
}
