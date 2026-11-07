import { DEFAULT_TONE, loadTone, saveTone } from './tone-storage';

function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    get length() {
      return data.size;
    },
    clear: () => data.clear(),
    getItem: (key) => data.get(key) ?? null,
    key: (index) => [...data.keys()][index] ?? null,
    removeItem: (key) => data.delete(key),
    setItem: (key, value) => data.set(key, value),
  } as Storage;
}

describe('tom guardado no aparelho', () => {
  it('padrão é "direto" quando nada foi escolhido', () => {
    expect(loadTone(memoryStorage())).toBe(DEFAULT_TONE);
    expect(DEFAULT_TONE).toBe('direct');
  });

  it('guarda e lê o tom escolhido', () => {
    const storage = memoryStorage();
    saveTone('scold', storage);

    expect(loadTone(storage)).toBe('scold');
  });

  it('valor desconhecido volta ao padrão', () => {
    const storage = memoryStorage();
    storage.setItem('mydly.tone', 'grosso');

    expect(loadTone(storage)).toBe('direct');
  });

  it('armazenamento bloqueado não quebra o app', () => {
    const blocked = {
      getItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    } as unknown as Storage;

    expect(loadTone(blocked)).toBe('direct');
    expect(() => saveTone('gentle', blocked)).not.toThrow();
  });
});
