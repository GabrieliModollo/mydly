import { OUTBOX_KEY, OutboxEntry, readOutbox, writeOutbox } from './outbox-storage';

const ENTRY: OutboxEntry = {
  id: 'a',
  text: '50 reais',
  occurredAt: '2026-11-05T12:00:00.000Z',
  rejected: false,
  userId: 'user-1',
};

/** Armazenamento que recusa tudo, como um navegador com dados de site bloqueados. */
const BLOCKED = {
  getItem: () => {
    throw new DOMException('bloqueado', 'SecurityError');
  },
  setItem: () => {
    throw new DOMException('cheio', 'QuotaExceededError');
  },
  removeItem: () => {
    throw new DOMException('bloqueado', 'SecurityError');
  },
} as unknown as Storage;

describe('fila no aparelho', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('grava e lê de volta', () => {
    const rejected = { ...ENTRY, id: 'b', rejected: true };
    writeOutbox(localStorage, [ENTRY, rejected]);

    expect(readOutbox(localStorage)).toEqual([ENTRY, rejected]);
  });

  it('fila vazia apaga a chave, sem deixar rastro no aparelho', () => {
    writeOutbox(localStorage, [ENTRY]);
    writeOutbox(localStorage, []);

    expect(localStorage.getItem(OUTBOX_KEY)).toBeNull();
    expect(readOutbox(localStorage)).toEqual([]);
  });

  it.each([
    ['JSON quebrado', '{'],
    ['não é uma lista', '{"id":"a"}'],
  ])('%s vira fila vazia', (_, raw) => {
    localStorage.setItem(OUTBOX_KEY, raw);

    expect(readOutbox(localStorage)).toEqual([]);
  });

  it('ignora só os itens com formato errado', () => {
    localStorage.setItem(
      OUTBOX_KEY,
      JSON.stringify([
        ENTRY,
        { ...ENTRY, id: '' },
        { ...ENTRY, text: '   ' },
        { ...ENTRY, occurredAt: 'ontem' },
        { ...ENTRY, rejected: 'não' },
        { ...ENTRY, userId: '' },
        { id: 'sem-dono', text: '50 reais', occurredAt: ENTRY.occurredAt, rejected: false },
        null,
        'texto',
      ]),
    );

    expect(readOutbox(localStorage)).toEqual([ENTRY]);
  });

  it('sem armazenamento ou com ele bloqueado, não quebra o app', () => {
    expect(readOutbox(undefined)).toEqual([]);
    expect(readOutbox(BLOCKED)).toEqual([]);
    expect(() => writeOutbox(BLOCKED, [ENTRY])).not.toThrow();
    expect(() => writeOutbox(BLOCKED, [])).not.toThrow();
    expect(() => writeOutbox(undefined, [ENTRY])).not.toThrow();
  });
});
