import { Category, EntryItemResponse, EntryResponse } from './entry.models';

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
export type ChipTone = 'expense' | 'income' | 'routine' | 'missed' | 'attention' | 'neutral';

export interface Chip {
  label: string;
  tone: ChipTone;
}

/** Totais do dia mostrados nos cartões de resumo. */
export interface DaySummary {
  /** Soma das saídas com valor conhecido. */
  spent: number;
  /** Quantidade de rotinas cumpridas; as não cumpridas ficam de fora. */
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
 * Chips de um item. Quando ele precisa de confirmação, mostra só esse aviso: a interpretação ainda é
 * incerta, e um chip como "Saída · valor a definir" só repetiria a dúvida. Finanças ganham também o
 * chip da categoria.
 */
export function chipsForItem(item: EntryItemResponse): Chip[] {
  if (item.needsConfirmation) {
    return [{ label: 'Precisa confirmar', tone: 'attention' }];
  }
  if (item.dismissed) {
    return [{ label: 'Descartado', tone: 'neutral' }];
  }
  const chips = [itemChip(item)];
  if (item.type === 'FINANCE' && item.category) {
    chips.push({ label: CATEGORY_LABELS[item.category], tone: 'neutral' });
  }
  return chips;
}

/** Chips de todos os itens de um lançamento. */
export function chipsFor(entry: EntryResponse): Chip[] {
  return entry.items.flatMap(chipsForItem);
}

/** Soma das saídas e contagem de rotinas cumpridas dos lançamentos do dia; itens descartados ficam de fora. */
export function daySummary(entries: EntryResponse[]): DaySummary {
  const items = entries.flatMap((entry) => entry.items).filter((item) => !item.dismissed);
  return {
    spent: items
      .filter((item) => item.type === 'FINANCE' && item.direction === 'EXPENSE' && item.amount !== null)
      .reduce((total, item) => total + (item.amount ?? 0), 0),
    habitsDone: items.filter((item) => item.type === 'ROUTINE' && item.outcome !== 'MISSED').length,
  };
}
