import { chipsFor, chipsForItem, dayInSaoPaulo, formatMoney, formatQuantity, itemChip, parseAmount, summaryFrom } from './entry-format';
import { EntryItemResponse, EntryResponse, ItemHabit } from './entry.models';

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
    state: 'ACCEPTED_AUTO',
    revision: 1,
    canReopen: true,
    ...overrides,
  };
}

function entry(items: EntryItemResponse[], needsConfirmation = false): EntryResponse {
  return {
    id: crypto.randomUUID(),
    text: 'texto',
    occurredAt: '2026-11-05T12:00:00Z',
    status: needsConfirmation ? 'NEEDS_INPUT' : 'CONFIRMED',
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
  it('vem da API: hábitos cumpridos já somam bons cumpridos e ruins evitados', () => {
    const response = {
      date: '2026-11-05',
      spent: 62.5,
      received: 100,
      habitsAchieved: 3,
      slips: 1,
      spentByCategory: [],
      items: [],
      dailyGoals: [],
      budgetAlerts: [],
    };

    expect(summaryFrom(response)).toEqual({ spent: 62.5, habitsDone: 3 });
  });

  it('fica zerado enquanto a API não responde', () => {
    expect(summaryFrom(null)).toEqual({ spent: 0, habitsDone: 0 });
  });

  it('o dia é o de São Paulo, mesmo quando em UTC já é amanhã', () => {
    // 02:00Z do dia 6 ainda são 23:00 do dia 5 em São Paulo.
    expect(dayInSaoPaulo(new Date('2026-11-06T02:00:00Z'))).toBe('2026-11-05');
    expect(dayInSaoPaulo(new Date('2026-11-06T03:00:00Z'))).toBe('2026-11-06');
  });
});

describe('chip do hábito', () => {
  const routine = item({ type: 'ROUTINE', direction: null, outcome: 'DONE', quantity: 2, unit: 'L' });

  function habit(overrides: Partial<ItemHabit>): ItemHabit {
    return {
      entryId: 'e',
      itemIndex: 0,
      habit: 'HYDRATION',
      habitName: 'Hidratação',
      polarity: 'GOOD',
      status: 'ACHIEVED',
      ...overrides,
    };
  }

  it('hábito bom cumprido mostra o nome do hábito e a quantidade', () => {
    expect(chipsForItem(routine, habit({}))).toEqual([{ label: 'Hidratação · 2 L', tone: 'routine' }]);
  });

  it('hábito ruim evitado é conquista: "Não comi besteira"', () => {
    const chips = chipsForItem(
      item({ type: 'ROUTINE', direction: null, outcome: 'MISSED' }),
      habit({ habitName: 'Besteira', polarity: 'BAD', status: 'ACHIEVED' }),
    );

    expect(chips).toEqual([{ label: 'Evitou besteira', tone: 'routine' }]);
  });

  it('hábito ruim consumido é deslize, com cor própria', () => {
    const chips = chipsForItem(
      item({ type: 'ROUTINE', direction: null, outcome: 'DONE', quantity: 2 }),
      habit({ habitName: 'Energético', polarity: 'BAD', status: 'SLIPPED' }),
    );

    expect(chips).toEqual([{ label: 'Deslize · Energético', tone: 'slip' }]);
  });

  it('hábito bom não cumprido diz qual hábito faltou', () => {
    const chips = chipsForItem(
      item({ type: 'ROUTINE', direction: null, outcome: 'MISSED' }),
      habit({ habitName: 'Academia', status: 'MISSED' }),
    );

    expect(chips).toEqual([{ label: 'Academia · não cumprido', tone: 'missed' }]);
  });

  it('rotina fora do catálogo continua com o chip de rotina', () => {
    expect(chipsForItem(routine, habit({ habit: null, habitName: null, polarity: null, status: null }))).toEqual([
      { label: 'Rotina · 2 L', tone: 'routine' },
    ]);
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
