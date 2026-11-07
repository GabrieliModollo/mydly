import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { Observable, of, throwError } from 'rxjs';
import { Tone, WeekSummaryResponse } from '../entries/week.models';
import { WeeksApi } from '../entries/weeks-api';
import { SummaryPage } from './summary.page';

function week(weekStart: string, overrides: Partial<WeekSummaryResponse> = {}): WeekSummaryResponse {
  return {
    weekStart,
    weekEnd: '2026-11-08',
    daysElapsed: 3,
    tone: 'direct',
    habits: [
      {
        code: 'HYDRATION',
        name: 'Hidratação',
        polarity: 'GOOD',
        days: ['ACHIEVED', 'ACHIEVED', 'NONE', 'FUTURE', 'FUTURE', 'FUTURE', 'FUTURE'],
        score: { achieved: 2, elapsedDays: 3, target: null, limit: null, slips: null, goal: null },
      },
    ],
    money: { spent: 150, received: 0, spentByCategory: [{ category: 'FOOD', amount: 150 }] },
    budgets: [],
    previous: { spent: 100, habitsAchieved: 4, comparedDays: 3 },
    insights: [
      { kind: 'SPENDING_UP', text: 'Gastos da semana: R$ 150,00, 50% acima da semana passada.', numbers: {} },
      { kind: 'SLEEP_AND_SPENDING', text: 'Dias de sono ruim: R$ 60,00 de gasto médio.', numbers: {} },
      { kind: 'STREAK', text: 'Hidratação: 3 dias seguidos.', numbers: {} },
    ],
    ...overrides,
  };
}

describe('SummaryPage', () => {
  let fixture: ComponentFixture<SummaryPage>;
  let calls: [string | null, Tone][];
  let respond: (monday: string | null, tone: Tone) => Observable<WeekSummaryResponse>;

  async function open(): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [SummaryPage],
      providers: [
        provideRouter([]),
        provideIonicAngular({ mode: 'md' }),
        {
          provide: WeeksApi,
          useValue: {
            summary: (monday: string | null, tone: Tone) => {
              calls.push([monday, tone]);
              return respond(monday, tone);
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(SummaryPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function button(root: HTMLElement, text: string): HTMLButtonElement {
    const found = [...root.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === text || b.getAttribute('aria-label') === text,
    );
    if (!found) {
      throw new Error(`botão "${text}" não encontrado`);
    }
    return found;
  }

  beforeEach(() => {
    localStorage.clear();
    calls = [];
    respond = (monday) => of(week(monday ?? '2026-11-02'));
  });

  it('abre a semana atual no tom "Direto" com a mensagem principal no cartão escuro', async () => {
    const root = await open();

    expect(calls).toEqual([[null, 'direct']]);
    expect(root.querySelector('.message__text')?.textContent).toContain('50% acima');
    expect(button(root, 'Direto').getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelector('h1')?.textContent).toContain('Sua semana');
    expect(root.querySelector('.header__date')?.textContent).toContain('2 a 8 de novembro');
  });

  it('trocar o tom guarda no aparelho e pede as mensagens de novo', async () => {
    const root = await open();

    button(root, 'Bronca').click();
    await fixture.whenStable();

    expect(calls.at(-1)).toEqual([null, 'scold']);
    expect(localStorage.getItem('mydly.tone')).toBe('scold');
    expect(button(root, 'Bronca').getAttribute('aria-pressed')).toBe('true');
  });

  it('usa o tom guardado no aparelho', async () => {
    localStorage.setItem('mydly.tone', 'gentle');

    await open();

    expect(calls).toEqual([[null, 'gentle']]);
  });

  it('o cruzamento de sono e gastos tem cartão próprio no acento', async () => {
    const root = await open();

    expect(root.querySelector('.card--accent')?.textContent).toContain('sono ruim');
    const secondary = [...root.querySelectorAll('.card:not(.card--accent) .card__text')].map((el) => el.textContent);
    expect(secondary).toEqual(['Hidratação: 3 dias seguidos.']);
  });

  it('hábitos com 7 pontos, placar e descrição para leitor de tela', async () => {
    const root = await open();

    const habit = root.querySelector('.habit')!;
    expect(habit.querySelectorAll('.dot')).toHaveLength(7);
    expect(habit.querySelector('.dot')?.getAttribute('data-mark')).toBe('ACHIEVED');
    expect(habit.querySelector('.habit__score')?.textContent?.trim()).toBe('2/3');
    expect(habit.querySelector('.sr-only')?.textContent).toContain('Hidratação, 2 de 3 dias. segunda: cumprido');
  });

  it('gastos por categoria com barra, total e semana anterior', async () => {
    const root = await open();

    expect(root.querySelector('.card__total')?.textContent).toContain('R$ 150,00');
    expect(root.querySelector('.bar__label')?.textContent).toBe('Alimentação');
    expect(root.querySelector('.card__note')?.textContent).toContain('Semana anterior, até quarta: R$ 100,00');
  });

  it('volta uma semana e depois retorna à atual; não passa da atual', async () => {
    const root = await open();
    expect(button(root, 'Próxima semana').disabled).toBe(true);

    button(root, 'Semana anterior').click();
    await fixture.whenStable();
    expect(calls.at(-1)).toEqual(['2026-10-26', 'direct']);
    expect(root.querySelector('h1')?.textContent).toContain('Resumo da semana');
    expect(button(root, 'Próxima semana').disabled).toBe(false);

    button(root, 'Próxima semana').click();
    await fixture.whenStable();
    expect(calls.at(-1)).toEqual([null, 'direct']);
    expect(button(root, 'Próxima semana').disabled).toBe(true);
  });

  it('semana sem dados mostra as boas-vindas e os cartões vazios', async () => {
    respond = () =>
      of(
        week('2026-11-02', {
          habits: [],
          money: { spent: 0, received: 0, spentByCategory: [] },
          previous: null,
          insights: [{ kind: 'WELCOME', text: 'Sua semana começa aqui.', numbers: {} }],
        }),
      );

    const root = await open();

    expect(root.querySelector('.message__text')?.textContent).toContain('Sua semana começa aqui.');
    expect(root.textContent).toContain('Nenhum hábito registrado nesta semana.');
    expect(root.textContent).toContain('Nenhum gasto nesta semana.');
  });

  it('falha ao carregar avisa e permite tentar de novo', async () => {
    respond = () => throwError(() => new Error('rede'));
    const root = await open();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar o resumo.');

    respond = (monday) => of(week(monday ?? '2026-11-02'));
    button(root, 'Tentar de novo').click();
    await fixture.whenStable();

    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.querySelector('.message__text')).not.toBeNull();
  });
});
