import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIonicAngular } from '@ionic/angular';
import { EntryItemResponse, EntryResponse, ResolveItemRequest } from '../entries/entry.models';
import { ConfirmSheet } from './confirm-sheet';

function entryWith(item: Partial<EntryItemResponse>): EntryResponse {
  return {
    id: 'p',
    text: 'Comprei pão',
    occurredAt: '2026-11-05T12:00:00Z',
    status: 'NEEDS_INPUT',
    needsConfirmation: true,
    version: 0,
    items: [
      {
        type: 'FINANCE',
        direction: 'EXPENSE',
        outcome: null,
        category: 'FOOD',
        amount: null,
        quantity: null,
        unit: null,
        description: 'Comprei pão',
        confidence: 0.4,
        needsConfirmation: true,
        resolvedByUser: false,
        dismissed: false,
        state: 'PENDING',
        revision: 1,
        canReopen: false,
        ...item,
      },
    ],
  };
}

describe('ConfirmSheet', () => {
  let fixture: ComponentFixture<ConfirmSheet>;
  let emitted: ResolveItemRequest[];
  let closed: number;

  async function open(entry: EntryResponse, index = 0): Promise<HTMLElement> {
    TestBed.configureTestingModule({ imports: [ConfirmSheet], providers: [provideIonicAngular({ mode: 'md' })] });
    fixture = TestBed.createComponent(ConfirmSheet);
    fixture.componentRef.setInput('entry', entry);
    fixture.componentRef.setInput('index', index);
    emitted = [];
    closed = 0;
    fixture.componentInstance.resolved.subscribe((request) => emitted.push(request));
    fixture.componentInstance.closed.subscribe(() => closed++);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function button(root: HTMLElement, text: string): HTMLButtonElement {
    const found = [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
    if (!found) {
      throw new Error(`botão "${text}" não encontrado`);
    }
    return found;
  }

  async function typeAmount(root: HTMLElement, value: string): Promise<void> {
    const input = root.querySelector<HTMLInputElement>('#confirm-amount')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('mostra a frase original e a categoria sugerida já escolhida', async () => {
    const root = await open(entryWith({}));

    expect(root.querySelector('h2')?.textContent).toContain('Quanto foi?');
    expect(root.querySelector('blockquote')?.textContent).toContain('Comprei pão');
    expect(button(root, 'Alimentação').getAttribute('aria-pressed')).toBe('true');
    expect(button(root, 'Mercado').getAttribute('aria-pressed')).toBe('false');
  });

  it('num lançamento com vários itens mostra só o trecho do item', async () => {
    const base = entryWith({ description: 'comprei pão' });
    const routine = {
      ...base.items[0],
      type: 'ROUTINE' as const,
      direction: null,
      outcome: 'DONE' as const,
      category: null,
      description: 'Fui à academia',
      confidence: 0.6,
      needsConfirmation: false,
    };
    const root = await open({ ...base, text: 'Fui à academia e comprei pão', items: [routine, base.items[0]] }, 1);

    expect(root.querySelector('blockquote')?.textContent?.trim()).toBe('comprei pão');
  });

  it('item reaberto com "Não foi isso" já vem com o valor para corrigir', async () => {
    const root = await open(entryWith({ amount: 50, category: 'PHARMACY' }));

    expect(root.querySelector<HTMLInputElement>('#confirm-amount')?.value).toBe('50,00');
  });

  it('só deixa salvar com um valor válido e envia valor, direção e categoria', async () => {
    const root = await open(entryWith({}));
    expect(button(root, 'Salvar gasto').disabled).toBe(true);

    await typeAmount(root, '8,5x');
    expect(button(root, 'Salvar gasto').disabled).toBe(true);
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('8,50');

    await typeAmount(root, '8,50');
    button(root, 'Mercado').click();
    await fixture.whenStable();
    button(root, 'Salvar gasto').click();

    expect(emitted).toEqual([{ action: 'CONFIRM_FINANCE', amount: 8.5, direction: 'EXPENSE', category: 'GROCERIES' }]);
  });

  it('tocar de novo na categoria escolhida tira a escolha', async () => {
    const root = await open(entryWith({}));
    await typeAmount(root, '10');

    button(root, 'Alimentação').click();
    await fixture.whenStable();
    button(root, 'Salvar gasto').click();

    expect(emitted).toEqual([{ action: 'CONFIRM_FINANCE', amount: 10, direction: 'EXPENSE', category: null }]);
  });

  it('entrada usa o próprio rótulo e direção', async () => {
    const root = await open(entryWith({ direction: 'INCOME', category: null }));
    await typeAmount(root, '100');

    button(root, 'Salvar entrada').click();

    expect(emitted).toEqual([{ action: 'CONFIRM_FINANCE', amount: 100, direction: 'INCOME', category: null }]);
  });

  it('"Não foi um gasto" descarta o item', async () => {
    const root = await open(entryWith({}));

    button(root, 'Não foi um gasto').click();

    expect(emitted).toEqual([{ action: 'DISMISS' }]);
  });

  it('item não entendido pergunta primeiro o tipo', async () => {
    const root = await open(entryWith({ type: 'UNKNOWN', direction: null, category: null }));

    expect(root.querySelector('h2')?.textContent).toContain('O que foi isso?');
    expect(root.querySelector('#confirm-amount')).toBeNull();
    expect(button(root, 'Salvar gasto').disabled).toBe(true);

    button(root, 'Uma rotina').click();
    await fixture.whenStable();
    button(root, 'Salvar rotina').click();

    expect(emitted).toEqual([{ action: 'CONFIRM_ROUTINE' }]);
  });

  it('item não entendido pode virar gasto', async () => {
    const root = await open(entryWith({ type: 'UNKNOWN', direction: null, category: null }));

    button(root, 'Um gasto').click();
    await fixture.whenStable();
    await typeAmount(root, '12');
    button(root, 'Salvar gasto').click();

    expect(emitted).toEqual([{ action: 'CONFIRM_FINANCE', amount: 12, direction: 'EXPENSE', category: null }]);
    expect(button(root, 'Não era nada')).toBeTruthy();
  });

  it('fecha pelo botão de fechar, sem resolver', async () => {
    const root = await open(entryWith({}));

    root.querySelector<HTMLButtonElement>('[aria-label="Fechar"]')!.click();

    expect(closed).toBe(1);
    expect(emitted).toEqual([]);
  });
});
