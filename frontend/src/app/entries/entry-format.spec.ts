import { chipsFor, chipsForItem, daySummary, formatMoney, formatQuantity, itemChip, parseAmount } from './entry-format';
import { EntryItemResponse, EntryResponse } from './entry.models';

function item(overrides: Partial<EntryItemResponse>): EntryItemResponse {
  return {
    type: 'FINANCE',
    direction: 'EXPENSE',
    outcome: null,
    amount: null,
    quantity: null,
    unit: null,
    category: null,
    description: null,
    confidence: 0.9,
    needsConfirmation: false,
    resolvedByUser: false,
    dismissed: false,
    ...overrides,
  };
}

function entry(items: EntryItemResponse[], needsConfirmation = false): EntryResponse {
  return {
    id: crypto.randomUUID(),
    text: 'texto',
    occurredAt: '2026-11-05T12:00:00Z',
    status: needsConfirmation ? 'NEEDS_INPUT' : 'PENDING',
    needsConfirmation,
    version: 0,
    items,
  };
}

describe('formatação dos chips', () => {
  it('formata dinheiro em reais no padrão brasileiro', () => {
    expect(formatMoney(50)).toBe('R$ 50,00');
    expect(formatMoney(1250.9)).toBe('R$ 1.250,90');
    expect(formatMoney(0)).toBe('R$ 0,00');
  });

  it.each([
    [2, 'L', '2 L'],
    [2.5, 'L', '2,5 L'],
    [500, 'mL', '500 mL'],
    [72.5, 'kg', '72,5 kg'],
    [1, 'copo', '1 copo'],
    [3, 'copo', '3 copos'],
    [30, null, '30'],
  ])('formata %s %s como "%s"', (quantity, unit, expected) => {
    expect(formatQuantity(quantity, unit)).toBe(expected);
  });

  it('mostra saída e entrada de dinheiro com tons diferentes', () => {
    expect(itemChip(item({ amount: 50 }))).toEqual({ label: 'Saída · R$ 50,00', tone: 'expense' });
    expect(itemChip(item({ direction: 'INCOME', amount: 3000 }))).toEqual({
      label: 'Entrada · R$ 3.000,00',
      tone: 'income',
    });
  });

  it('avisa quando a finança ainda não tem valor', () => {
    expect(itemChip(item({ amount: null })).label).toBe('Saída · valor a definir');
  });

  it('mostra rotina com unidade, sem quantidade e com contagem', () => {
    expect(itemChip(item({ type: 'ROUTINE', direction: null, quantity: 2, unit: 'L' }))).toEqual({
      label: 'Rotina · 2 L',
      tone: 'routine',
    });
    expect(itemChip(item({ type: 'ROUTINE', direction: null })).label).toBe('Rotina');
    expect(itemChip(item({ type: 'ROUTINE', direction: null, quantity: 30 })).label).toBe('Rotina · 30×');
  });

  it('mostra hábito não cumprido com tom e rótulo próprios, mesmo com quantidade', () => {
    expect(itemChip(item({ type: 'ROUTINE', direction: null, outcome: 'MISSED' }))).toEqual({
      label: 'Rotina · não cumprido',
      tone: 'missed',
    });
    expect(
      itemChip(item({ type: 'ROUTINE', direction: null, outcome: 'MISSED', quantity: 2, unit: 'L' })).label,
    ).toBe('Rotina · não cumprido');
  });

  it('assume quando não entendeu, em tom neutro', () => {
    expect(itemChip(item({ type: 'UNKNOWN', direction: null }))).toEqual({ label: 'Não entendi', tone: 'neutral' });
  });

  it('item que precisa de confirmação mostra só esse aviso', () => {
    expect(chipsForItem(item({ amount: null, category: 'FOOD', needsConfirmation: true }))).toEqual([
      { label: 'Precisa confirmar', tone: 'attention' },
    ]);
  });

  it('finança mostra a categoria num chip neutro', () => {
    expect(chipsForItem(item({ amount: 8.5, category: 'FOOD' }))).toEqual([
      { label: 'Saída · R$ 8,50', tone: 'expense' },
      { label: 'Alimentação', tone: 'neutral' },
    ]);
  });

  it('item descartado aparece como descartado', () => {
    expect(chipsForItem(item({ dismissed: true, resolvedByUser: true }))).toEqual([
      { label: 'Descartado', tone: 'neutral' },
    ]);
  });

  it('lançamento confirmado mostra um chip por item', () => {
    const chips = chipsFor(entry([item({ amount: 30 }), item({ type: 'ROUTINE', direction: null })]));

    expect(chips.map((chip) => chip.label)).toEqual(['Saída · R$ 30,00', 'Rotina']);
  });
});

describe('resumo do dia', () => {
  it('soma só as saídas com valor e conta as rotinas', () => {
    const summary = daySummary([
      entry([item({ amount: 50 })]),
      entry([item({ amount: 12.5 }), item({ type: 'ROUTINE', direction: null })]),
      entry([item({ direction: 'INCOME', amount: 100 })]),
      entry([item({ amount: null, needsConfirmation: true })], true),
      entry([item({ amount: 999, dismissed: true, resolvedByUser: true })]),
      entry([item({ type: 'ROUTINE', direction: null, quantity: 2, unit: 'L' })]),
      entry([item({ type: 'UNKNOWN', direction: null })]),
    ]);

    expect(summary).toEqual({ spent: 62.5, habitsDone: 2 });
  });

  it('hábito não cumprido não conta como cumprido', () => {
    const summary = daySummary([
      entry([item({ type: 'ROUTINE', direction: null, outcome: 'DONE' })]),
      entry([item({ type: 'ROUTINE', direction: null, outcome: 'MISSED' })]),
    ]);

    expect(summary.habitsDone).toBe(1);
  });

  it('dia vazio tem tudo zerado', () => {
    expect(daySummary([])).toEqual({ spent: 0, habitsDone: 0 });
  });
});

describe('parseAmount', () => {
  it.each([
    ['8,50', 8.5],
    ['8.5', 8.5],
    ['8', 8],
    ['1.250,90', 1250.9],
    ['12.345', 12345],
    ['R$ 12', 12],
    [' 0,99 ', 0.99],
  ])('"%s" vira %s', (text, expected) => {
    expect(parseAmount(text)).toBe(expected);
  });

  it.each(['', 'abc', '0', '0,00', '-5', '8,505', '1,2,3', '12.34.5'])('"%s" não é um valor válido', (text) => {
    expect(parseAmount(text)).toBeNull();
  });
});
