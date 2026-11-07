import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIonicAngular } from '@ionic/angular';
import { HabitGoalRequest, HabitGoalResponse } from '../entries/goal.models';
import { HabitGoalSheet } from './habit-goal-sheet';

const HYDRATION: HabitGoalResponse = {
  habit: 'HYDRATION',
  name: 'Hidratação',
  polarity: 'GOOD',
  measure: 'VOLUME',
  allowedKinds: ['DAILY_QUANTITY', 'WEEKLY_FREQUENCY'],
  allowedUnits: ['L', 'mL'],
  goal: null,
};

const DELIVERY: HabitGoalResponse = {
  habit: 'DELIVERY',
  name: 'Delivery',
  polarity: 'BAD',
  measure: 'NONE',
  allowedKinds: ['WEEKLY_FREQUENCY'],
  allowedUnits: [],
  goal: null,
};

describe('HabitGoalSheet', () => {
  let fixture: ComponentFixture<HabitGoalSheet>;
  let saved: HabitGoalRequest[];
  let removed: number;
  let closed: number;

  async function open(habit: HabitGoalResponse): Promise<HTMLElement> {
    TestBed.configureTestingModule({ imports: [HabitGoalSheet], providers: [provideIonicAngular({ mode: 'md' })] });
    fixture = TestBed.createComponent(HabitGoalSheet);
    fixture.componentRef.setInput('habit', habit);
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
    const input = root.querySelector<HTMLInputElement>('#goal-quantity')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('é um diálogo com título e põe o foco no primeiro campo ao abrir', async () => {
    const root = await open(HYDRATION);

    const form = root.querySelector('form')!;
    expect(root.querySelector(`#${form.getAttribute('aria-labelledby')}`)?.textContent).toBe('Meta de Hidratação');
    expect(document.activeElement).toBe(button(root, 'Quantidade por dia'));
  });

  it('todo campo tem label e todo grupo tem legenda', async () => {
    const root = await open(HYDRATION);

    expect(root.querySelector('label[for="goal-quantity"]')?.textContent).toContain('Quantidade por dia');
    const legends = [...root.querySelectorAll('fieldset legend')].map((legend) => legend.textContent?.trim());
    expect(legends).toEqual(['Tipo de meta', 'Unidade']);
  });

  it('meta diária em litros ou mililitros', async () => {
    const root = await open(HYDRATION);

    await type(root, '1,5');
    button(root, 'Salvar meta').click();
    expect(saved).toEqual([{ kind: 'DAILY_QUANTITY', targetQuantity: 1.5, unit: 'L' }]);

    button(root, 'mL').click();
    await type(root, '500');
    button(root, 'Salvar meta').click();
    expect(saved.at(-1)).toEqual({ kind: 'DAILY_QUANTITY', targetQuantity: 500, unit: 'mL' });
  });

  it('quantidade inválida anuncia o erro ligado ao campo e não deixa salvar', async () => {
    const root = await open(HYDRATION);

    await type(root, 'dois');

    const input = root.querySelector<HTMLInputElement>('#goal-quantity')!;
    const error = root.querySelector('#goal-quantity-error');
    expect(error?.getAttribute('role')).toBe('alert');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('goal-quantity-error');
    expect(button(root, 'Salvar meta').disabled).toBe(true);
  });

  it('dias por semana num hábito bom vão de 1 a 7', async () => {
    const root = await open(HYDRATION);

    button(root, 'Dias por semana').click();
    await fixture.whenStable();

    const days = [...root.querySelectorAll('.pill--day')].map((pill) => pill.textContent?.trim());
    expect(days).toEqual(['1', '2', '3', '4', '5', '6', '7']);
    button(root, '4').click();
    await fixture.whenStable();
    button(root, 'Salvar meta').click();
    expect(saved).toEqual([{ kind: 'WEEKLY_FREQUENCY', daysPerWeek: 4 }]);
  });

  it('hábito ruim tem limite de 0 a 7 dias, sem meta por quantidade', async () => {
    const root = await open(DELIVERY);

    expect(root.querySelector('h2')?.textContent).toBe('Limite de Delivery');
    expect(root.textContent).not.toContain('Tipo de meta');
    expect(root.querySelector('legend')?.textContent).toContain('No máximo quantos dias por semana');
    const days = [...root.querySelectorAll('.pill--day')].map((pill) => pill.textContent?.trim());
    expect(days).toEqual(['0', '1', '2', '3', '4', '5', '6', '7']);
    expect(document.activeElement).toBe(button(root, '0'));

    button(root, '2').click();
    await fixture.whenStable();
    button(root, 'Salvar limite').click();
    expect(saved).toEqual([{ kind: 'WEEKLY_FREQUENCY', daysPerWeek: 2 }]);
  });

  it('Esc fecha o painel', async () => {
    const root = await open(HYDRATION);

    root.querySelector('form')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(closed).toBe(1);
  });

  it('parte da meta atual e oferece tirar a meta', async () => {
    const root = await open({
      ...HYDRATION,
      goal: { kind: 'DAILY_QUANTITY', targetQuantity: 2.5, unit: 'L', daysPerWeek: null, validFrom: '2026-11-01' },
    });

    expect(root.querySelector<HTMLInputElement>('#goal-quantity')!.value).toBe('2,5');
    button(root, 'Tirar meta').click();
    expect(removed).toBe(1);
  });

  it('sem meta não mostra "Tirar meta"', async () => {
    const root = await open(HYDRATION);

    expect([...root.querySelectorAll('button')].some((b) => b.textContent?.includes('Tirar'))).toBe(false);
  });

  it('erro da API aparece no painel e é anunciado', async () => {
    const root = await open(HYDRATION);

    fixture.componentRef.setInput('error', 'A meta de Hidratação é medida em L ou mL.');
    await fixture.whenStable();

    const alerts = [...root.querySelectorAll('[role="alert"]')].map((alert) => alert.textContent?.trim());
    expect(alerts).toContain('A meta de Hidratação é medida em L ou mL.');
  });
});
