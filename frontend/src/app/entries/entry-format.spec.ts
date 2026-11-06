import { chipsFor, daySummary, formatMoney, formatQuantity, itemChip } from './entry-format';
import { EntryItemResponse, EntryResponse } from './entry.models';

function item(overrides: Partial<EntryItemResponse>): EntryItemResponse {
  return {
    type: 'FINANCE',
    direction: 'EXPENSE',
    amount: null,
    quantity: null,
    unit: null,
    description: null,
    confidence: 0.9,
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

  it('assume quando não entendeu, em tom neutro', () => {
    expect(itemChip(item({ type: 'UNKNOWN', direction: null }))).toEqual({ label: 'Não entendi', tone: 'neutral' });
  });

  it('lançamento que precisa de confirmação mostra só esse aviso', () => {
    expect(chipsFor(entry([item({ amount: null })], true))).toEqual([
      { label: 'Precisa confirmar', tone: 'attention' },
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
      entry([item({ amount: null })], true),
      entry([item({ type: 'ROUTINE', direction: null, quantity: 2, unit: 'L' })]),
      entry([item({ type: 'UNKNOWN', direction: null })]),
    ]);

    expect(summary).toEqual({ spent: 62.5, habitsDone: 2 });
  });

  it('dia vazio tem tudo zerado', () => {
    expect(daySummary([])).toEqual({ spent: 0, habitsDone: 0 });
  });
});
