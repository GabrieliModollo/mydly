import { TestBed } from '@angular/core/testing';
import { Connectivity } from './connectivity';

describe('Connectivity', () => {
  let connectivity: Connectivity;
  let calls: number;

  beforeEach(() => {
    calls = 0;
    connectivity = TestBed.inject(Connectivity);
    connectivity.whenBackOnline(() => calls++);
  });

  afterEach(() => vi.restoreAllMocks());

  it('começa com o estado do navegador', () => {
    expect(connectivity.online()).toBe(navigator.onLine);
  });

  it('sem rede fica offline e, quando ela volta, avisa quem espera', () => {
    window.dispatchEvent(new Event('offline'));
    expect(connectivity.online()).toBe(false);
    expect(calls).toBe(0);

    window.dispatchEvent(new Event('online'));
    expect(connectivity.online()).toBe(true);
    expect(calls).toBe(1);
  });

  it('o app voltando para a frente com rede também avisa; sem rede, não', () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');

    document.dispatchEvent(new Event('visibilitychange'));
    expect(calls).toBe(1);

    window.dispatchEvent(new Event('offline'));
    document.dispatchEvent(new Event('visibilitychange'));
    expect(calls).toBe(1);
  });

  it('o app indo para o fundo não avisa', () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');

    document.dispatchEvent(new Event('visibilitychange'));

    expect(calls).toBe(0);
  });

  it('quem cancelou deixa de ser avisado', () => {
    let other = 0;
    const stop = connectivity.whenBackOnline(() => other++);
    stop();

    window.dispatchEvent(new Event('online'));

    expect(other).toBe(0);
    expect(calls).toBe(1);
  });
});
