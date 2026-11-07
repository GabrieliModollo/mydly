import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { Observable, of, Subject, throwError } from 'rxjs';
import {
  BudgetRequest,
  BudgetResponse,
  HabitGoalRequest,
  HabitGoalResponse,
  Preferences,
} from '../entries/goal.models';
import { GoalsApi } from '../entries/goals-api';
import { GoalsPage } from './goals.page';

const HYDRATION: HabitGoalResponse = {
  habit: 'HYDRATION',
  name: 'Hidratação',
  polarity: 'GOOD',
  measure: 'VOLUME',
  allowedKinds: ['DAILY_QUANTITY', 'WEEKLY_FREQUENCY'],
  allowedUnits: ['L', 'mL'],
  goal: { kind: 'DAILY_QUANTITY', targetQuantity: 2, unit: 'L', daysPerWeek: null, validFrom: '2026-11-01' },
};
const GYM: HabitGoalResponse = {
  habit: 'GYM',
  name: 'Academia',
  polarity: 'GOOD',
  measure: 'DURATION',
  allowedKinds: ['DAILY_QUANTITY', 'WEEKLY_FREQUENCY'],
  allowedUnits: ['h', 'min'],
  goal: null,
};
const DELIVERY: HabitGoalResponse = {
  habit: 'DELIVERY',
  name: 'Delivery',
  polarity: 'BAD',
  measure: 'NONE',
  allowedKinds: ['WEEKLY_FREQUENCY'],
  allowedUnits: [],
  goal: { kind: 'WEEKLY_FREQUENCY', targetQuantity: null, unit: null, daysPerWeek: 2, validFrom: '2026-11-01' },
};
const FOOD: BudgetResponse = {
  category: 'FOOD',
  period: 'WEEKLY',
  limit: 100,
  validFrom: '2026-11-01',
  progress: { periodStart: '2026-11-02', periodEnd: '2026-11-08', spent: 85, percent: 85, status: 'NEAR' },
};
const GROCERIES: BudgetResponse = { category: 'GROCERIES', period: null, limit: null, validFrom: null, progress: null };

interface Page {
  editingHabit(): HabitGoalResponse | null;
  sheetError(): string | null;
  closeSheet(): void;
  saveHabit(request: HabitGoalRequest): Promise<void>;
  saveBudget(request: BudgetRequest): Promise<void>;
}

describe('GoalsPage', () => {
  let fixture: ComponentFixture<GoalsPage>;
  let habits: HabitGoalResponse[];
  let budgets: BudgetResponse[];
  let savedHabits: [string, HabitGoalRequest][];
  let savedPreferences: Preferences[];
  let budgetCalls: number;
  let saveHabitResponse: (habit: string, request: HabitGoalRequest) => Observable<HabitGoalResponse>;
  let habitGoals: () => Observable<HabitGoalResponse[]>;
  let savePreferencesResponse: (preferences: Preferences) => Observable<Preferences>;
  let serverPreferences: () => Observable<Preferences>;

  async function open(): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [GoalsPage],
      providers: [
        provideRouter([]),
        provideIonicAngular({ mode: 'md' }),
        {
          provide: GoalsApi,
          useValue: {
            habitGoals: () => habitGoals(),
            budgets: () => {
              budgetCalls++;
              return of(budgets);
            },
            preferences: () => serverPreferences(),
            saveHabitGoal: (habit: string, request: HabitGoalRequest) => {
              savedHabits.push([habit, request]);
              return saveHabitResponse(habit, request);
            },
            saveBudget: (category: string, request: BudgetRequest) =>
              of({ ...FOOD, category, period: request.period, limit: request.limit }),
            removeHabitGoal: () => of(undefined),
            removeBudget: () => of(undefined),
            savePreferences: (preferences: Preferences) => {
              savedPreferences.push(preferences);
              return savePreferencesResponse(preferences);
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(GoalsPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function page(): Page {
    return fixture.componentInstance as unknown as Page;
  }

  function button(root: HTMLElement, label: string): HTMLButtonElement {
    const found = [...root.querySelectorAll('button')].find(
      (b) => b.getAttribute('aria-label') === label || b.textContent?.trim() === label,
    );
    if (!found) {
      throw new Error(`botão "${label}" não encontrado`);
    }
    return found;
  }

  function rowText(root: HTMLElement, name: string): string {
    const row = [...root.querySelectorAll('.row')].find((item) => item.querySelector('.row__name')?.textContent === name);
    return row?.querySelector('.row__detail')?.textContent?.trim() ?? '';
  }

  beforeEach(() => {
    habits = [GYM, HYDRATION, DELIVERY];
    budgets = [FOOD, GROCERIES];
    savedHabits = [];
    savedPreferences = [];
    budgetCalls = 0;
    habitGoals = () => of(habits);
    savePreferencesResponse = (preferences) => of(preferences);
    serverPreferences = () => of({ firstDayOfWeek: 'MONDAY' });
    saveHabitResponse = (habit, request) =>
      of({
        ...GYM,
        goal: {
          kind: 'WEEKLY_FREQUENCY',
          targetQuantity: null,
          unit: null,
          daysPerWeek: request.kind === 'WEEKLY_FREQUENCY' ? request.daysPerWeek : null,
          validFrom: '2026-11-07',
        },
      });
  });

  it('mostra a meta atual de cada hábito, ou "sem meta", e os hábitos para evitar com limite', async () => {
    const root = await open();

    expect(rowText(root, 'Hidratação')).toBe('2 L por dia');
    expect(rowText(root, 'Academia')).toBe('Sem meta');
    expect(rowText(root, 'Delivery')).toBe('No máximo 2 dias por semana');
    expect(button(root, 'Definir meta de Academia').textContent?.trim()).toBe('Definir');
    expect(button(root, 'Editar limite de Delivery').textContent?.trim()).toBe('Editar');
  });

  it('orçamentos com o gasto do período por escrito e barra acessível', async () => {
    const root = await open();

    expect(rowText(root, 'Alimentação')).toBe('R$ 100,00 por semana');
    expect(rowText(root, 'Mercado')).toBe('Sem limite');
    const bar = root.querySelector('[role="progressbar"]')!;
    expect(bar.getAttribute('aria-valuetext')).toBe('R$ 85,00 de R$ 100,00, 85%, perto do limite');
    expect(bar.getAttribute('aria-valuenow')).toBe('85');
    expect(bar.getAttribute('aria-valuemax')).toBe('100');
    expect(root.querySelector('.row__progress')?.textContent).toContain('R$ 85,00 de R$ 100,00');
  });

  it('estados vazios explicam o que fazer', async () => {
    habits = [GYM];
    budgets = [GROCERIES];

    const root = await open();

    expect(root.textContent).toContain('Nenhuma meta ainda.');
    expect(root.textContent).toContain('Nenhum limite ainda.');
  });

  it('o início da semana tem label e mostra o dia atual', async () => {
    const root = await open();

    const select = root.querySelector<HTMLSelectElement>('#first-day')!;
    expect(root.querySelector('label[for="first-day"]')?.textContent).toContain('Início da semana');
    expect(select.value).toBe('MONDAY');
  });

  it('trocar o início da semana salva a preferência, anuncia e recarrega os orçamentos', async () => {
    const root = await open();
    const calls = budgetCalls;

    const select = root.querySelector<HTMLSelectElement>('#first-day')!;
    select.value = 'SUNDAY';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(savedPreferences).toEqual([{ firstDayOfWeek: 'SUNDAY' }]);
    expect(root.querySelector('[role="status"]')?.textContent).toContain('A semana agora começa no domingo.');
    expect(budgetCalls).toBe(calls + 1);
  });

  describe('várias mudanças do início da semana em voo', () => {
    let responses: Subject<Preferences>[];

    beforeEach(() => {
      responses = [];
      savePreferencesResponse = () => {
        const response = new Subject<Preferences>();
        responses.push(response);
        return response;
      };
    });

    async function settle(): Promise<void> {
      for (let round = 0; round < 3; round++) {
        await fixture.whenStable();
      }
    }

    function choose(root: HTMLElement, day: string): void {
      const select = root.querySelector<HTMLSelectElement>('#first-day')!;
      select.value = day;
      select.dispatchEvent(new Event('change'));
    }

    function reply(index: number, day: Preferences['firstDayOfWeek']): void {
      responses[index].next({ firstDayOfWeek: day });
      responses[index].complete();
    }

    function selected(root: HTMLElement): string {
      return root.querySelector<HTMLSelectElement>('#first-day')!.value;
    }

    function status(root: HTMLElement): string {
      return root.querySelector('[role="status"]')?.textContent?.trim() ?? '';
    }

    it('três mudanças seguidas geram duas gravações, a segunda com o último dia', async () => {
      const root = await open();
      const calls = budgetCalls;
      choose(root, 'SUNDAY');
      choose(root, 'TUESDAY');
      choose(root, 'WEDNESDAY');

      expect(savedPreferences).toEqual([{ firstDayOfWeek: 'SUNDAY' }]);
      expect(selected(root)).toBe('WEDNESDAY');

      reply(0, 'SUNDAY');
      await settle();
      expect(savedPreferences).toEqual([{ firstDayOfWeek: 'SUNDAY' }, { firstDayOfWeek: 'WEDNESDAY' }]);
      // Enquanto a última não volta, nada de aviso nem de orçamentos.
      expect(status(root)).toBe('');
      expect(budgetCalls).toBe(calls);

      reply(1, 'WEDNESDAY');
      await settle();
      expect(savedPreferences).toHaveLength(2);
      expect(selected(root)).toBe('WEDNESDAY');
      expect(status(root)).toBe('A semana agora começa na quarta.');
      expect(budgetCalls).toBe(calls + 1);
    });

    it('a falha da primeira com um dia pendente envia o pendente', async () => {
      const root = await open();
      choose(root, 'SUNDAY');
      choose(root, 'TUESDAY');

      responses[0].error(new HttpErrorResponse({ status: 500 }));
      await settle();
      expect(savedPreferences).toEqual([{ firstDayOfWeek: 'SUNDAY' }, { firstDayOfWeek: 'TUESDAY' }]);

      reply(1, 'TUESDAY');
      await settle();
      expect(selected(root)).toBe('TUESDAY');
      expect(status(root)).toBe('A semana agora começa na terça.');
    });

    it('a falha da última gravação relê o dia que o servidor tem', async () => {
      const root = await open();
      // A primeira gravação valeu no servidor; a segunda falhou.
      serverPreferences = () => of({ firstDayOfWeek: 'SUNDAY' });
      choose(root, 'SUNDAY');
      choose(root, 'TUESDAY');

      reply(0, 'SUNDAY');
      await settle();
      responses[1].error(new HttpErrorResponse({ status: 500 }));
      await settle();

      expect(selected(root)).toBe('SUNDAY');
      expect(status(root)).toBe('Não foi possível salvar o início da semana. Tente de novo.');
    });

    it('voltar ao dia que está sendo gravado não manda outra gravação', async () => {
      const root = await open();
      choose(root, 'SUNDAY');
      choose(root, 'TUESDAY');
      choose(root, 'SUNDAY');

      reply(0, 'SUNDAY');
      await settle();

      expect(savedPreferences).toEqual([{ firstDayOfWeek: 'SUNDAY' }]);
      expect(selected(root)).toBe('SUNDAY');
      expect(status(root)).toBe('A semana agora começa no domingo.');
    });
  });

  it('orçamento estourado: aria-valuenow no limite e o gasto real no texto', async () => {
    budgets = [
      { ...FOOD, progress: { periodStart: '2026-11-02', periodEnd: '2026-11-08', spent: 120, percent: 120, status: 'OVER' } },
    ];

    const root = await open();

    const bar = root.querySelector('[role="progressbar"]')!;
    expect(bar.getAttribute('aria-valuenow')).toBe('100');
    expect(bar.getAttribute('aria-valuemax')).toBe('100');
    expect(bar.getAttribute('aria-valuetext')).toBe('R$ 120,00 de R$ 100,00, 120%, passou do limite');
  });

  it('abre o painel do hábito e devolve o foco ao botão que abriu ao fechar', async () => {
    const root = await open();
    const opener = button(root, 'Definir meta de Academia');

    opener.click();
    await fixture.whenStable();
    expect(page().editingHabit()?.habit).toBe('GYM');

    page().closeSheet();
    await fixture.whenStable();

    expect(page().editingHabit()).toBeNull();
    expect(document.activeElement).toBe(opener);
  });

  it('salvar a meta atualiza a linha, anuncia e fecha o painel', async () => {
    const root = await open();
    button(root, 'Definir meta de Academia').click();
    await fixture.whenStable();

    await page().saveHabit({ kind: 'WEEKLY_FREQUENCY', daysPerWeek: 4 });
    await fixture.whenStable();

    expect(savedHabits).toEqual([['GYM', { kind: 'WEEKLY_FREQUENCY', daysPerWeek: 4 }]]);
    expect(rowText(root, 'Academia')).toBe('4 dias por semana');
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Meta de Academia salva.');
    expect(page().editingHabit()).toBeNull();
  });

  it('a recusa da API (400) fica no painel com a mensagem dela', async () => {
    saveHabitResponse = () =>
      throwError(
        () => new HttpErrorResponse({ status: 400, error: { detail: 'A meta de Academia é medida em h ou min.' } }),
      );
    const root = await open();
    button(root, 'Definir meta de Academia').click();
    await fixture.whenStable();

    await page().saveHabit({ kind: 'DAILY_QUANTITY', targetQuantity: 2, unit: 'h' });

    expect(page().sheetError()).toBe('A meta de Academia é medida em h ou min.');
    expect(page().editingHabit()?.habit).toBe('GYM');
  });

  it('salvar o orçamento atualiza a linha', async () => {
    const root = await open();
    button(root, 'Definir limite de Mercado').click();
    await fixture.whenStable();

    await page().saveBudget({ period: 'MONTHLY', limit: 800 });
    await fixture.whenStable();

    expect(rowText(root, 'Mercado')).toBe('R$ 800,00 por mês');
  });

  it('falha ao carregar avisa e permite tentar de novo', async () => {
    habitGoals = () => throwError(() => new Error('rede'));
    const root = await open();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar suas metas.');

    habitGoals = () => of(habits);
    button(root, 'Tentar de novo').click();
    await fixture.whenStable();

    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(rowText(root, 'Hidratação')).toBe('2 L por dia');
  });

  it('a barra de navegação tem a aba Metas', async () => {
    const root = await open();

    const links = [...root.querySelectorAll('nav a')].map((link) => link.textContent?.trim());
    expect(links).toEqual(['Hoje', 'Resumo', 'Metas']);
  });
});
