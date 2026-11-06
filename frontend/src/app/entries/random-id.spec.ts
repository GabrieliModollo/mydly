import { randomId } from './random-id';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('randomId', () => {
  it('usa crypto.randomUUID quando disponível', () => {
    const source = { randomUUID: () => '11111111-1111-4111-8111-111111111111' } as unknown as Crypto;

    expect(randomId(source)).toBe('11111111-1111-4111-8111-111111111111');
  });

  it('gera um UUID v4 válido fora de contexto seguro, sem crypto.randomUUID', () => {
    const source = { getRandomValues: (array: Uint8Array) => array.fill(0xff) } as unknown as Crypto;

    const id = randomId(source);

    expect(id).toMatch(UUID_V4);
    expect(id).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff');
  });

  it('gera ids diferentes a cada chamada', () => {
    expect(randomId()).not.toBe(randomId());
  });
});
