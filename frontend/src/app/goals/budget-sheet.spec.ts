import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIonicAngular } from '@ionic/angular';
import { BudgetRequest, BudgetResponse } from '../entries/goal.models';
import { BudgetSheet } from './budget-sheet';

const EMPTY: BudgetResponse = { category: 'FOOD', period: null, limit: null, validFrom: null, progress: null };

describe('BudgetSheet', () => {
  let fixture: ComponentFixture<BudgetSheet>;
  let saved: BudgetRequest[];
  let removed: number;
  let closed: number;

  async function open(budget: BudgetResponse): Promise<HTMLElement> {
    TestBed.configureTestingModule({ imports: [BudgetSheet], providers: [provideIonicAngular({ mode: 'md' })] });
    fixture = TestBed.createComponent(BudgetSheet);
    fixture.componentRef.setInput('budget', budget);
    saved = [];
    removed = 0;
    closed = 0;
    fixture.componentInstance.saved.subscribe((request) => saved.push(request));
    fixture.componentInstance.removed.subscribe(() => removed++);
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

  async function type(root: HTMLElement, value: string): Promise<void> {
    const input = root.querySelector<HTMLInputElement>('#budget-limit')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('título com a categoria, foco no valor e label no campo', async () => {
    const root = await open(EMPTY);

    expect(root.querySelector('h2')?.textContent).toBe('Limite de Alimentação');
    expect(document.activeElement).toBe(root.querySelector('#budget-limit'));
    expect(root.querySelector('label[for="budget-limit"]')?.textContent).toContain('Limite de gasto');
    expect(root.querySelector('legend')?.textContent).toContain('Período');
  });

  it('salva o limite por semana ou por mês', async () => {
    const root = await open(EMPTY);

    await type(root, '100');
    button(root, 'Por mês').click();
    await fixture.whenStable();
    button(root, 'Salvar limite').click();

    expect(saved).toEqual([{ period: 'MONTHLY', limit: 100 }]);
  });

  it('valor inválido anuncia o erro e não deixa salvar', async () => {
    const root = await open(EMPTY);

    await type(root, '0');

    expect(root.querySelector('#budget-limit-error')?.getAttribute('role')).toBe('alert');
    expect(root.querySelector('#budget-limit')?.getAttribute('aria-describedby')).toBe('budget-limit-error');
    expect(button(root, 'Salvar limite').disabled).toBe(true);
  });

  it('parte do limite atual, tira o limite e fecha com Esc', async () => {
    const root = await open({ ...EMPTY, period: 'WEEKLY', limit: 100, validFrom: '2026-11-01' });

    expect(root.querySelector<HTMLInputElement>('#budget-limit')!.value).toBe('100,00');
    expect(button(root, 'Por semana').getAttribute('aria-pressed')).toBe('true');
    button(root, 'Tirar limite').click();
    expect(removed).toBe(1);

    root.querySelector('form')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(closed).toBe(1);
  });
});
