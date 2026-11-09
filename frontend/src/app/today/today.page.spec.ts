import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { Connectivity } from '../entries/connectivity';
import { EntriesStore, NOW, ResolveResult, TimelineEntry } from '../entries/entries-store';
import { EntryResponse } from '../entries/entry.models';
import { BudgetAlert, DailyGoal } from '../entries/goal.models';
import { TodayPage } from './today.page';

registerLocaleData(localePt, 'pt-BR');

const PENDING: EntryResponse = {
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
    },
  ],
};

describe('TodayPage', () => {
  let fixture: ComponentFixture<TodayPage>;
  let submitted: string[];
  let input: HTMLInputElement;
  let timeline: WritableSignal<TimelineEntry[]>;
  let resolveResult: ResolveResult;
  let online: WritableSignal<boolean>;
  let waitingCount: WritableSignal<number>;
  let retried: string[];

  beforeEach(async () => {
    submitted = [];
    retried = [];
    timeline = signal([]);
    resolveResult = 'ok';
    online = signal(true);
    waitingCount = signal(0);
    const store: Partial<EntriesStore> = {
      loadState: signal('loaded'),
      timeline,
      waitingCount,
      retry: async (id: string) => {
        retried.push(id);
      },
      summary: signal({ spent: 62.5, habitsDone: 3 }),
      load: async () => undefined,
      submit: async (text: string) => {
        submitted.push(text);
      },
      resolve: async () => resolveResult,
      find: () => PENDING,
      habitFor: () => undefined,
      dailyGoals: signal([]),
      budgetAlerts: signal([]),
      today: signal('2026-11-05'),
      earlierPending: signal([]),
      summaryLoaded: signal(true),
      loadEarlierPending: async () => undefined,
      checkDay: () => undefined,
    };
    TestBed.configureTestingModule({
      imports: [TodayPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        { provide: EntriesStore, useValue: store },
        { provide: Connectivity, useValue: { online } },
        { provide: NOW, useValue: () => new Date('2026-11-05T12:00:00Z') },
      ],
    });
    fixture = TestBed.createComponent(TodayPage);
    await fixture.whenStable();
    input = fixture.nativeElement.querySelector('#entry-text');
  });

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('sem conexão avisa quantos lançamentos estão guardados no aparelho', async () => {
    expect(text()).not.toContain('Sem conexão');

    online.set(false);
    waitingCount.set(2);
    await fixture.whenStable();

    expect(text()).toContain('Sem conexão. 2 lançamentos guardados no aparelho, enviados quando a internet voltar.');
  });

  it('lançamento guardado diz que está no aparelho e oferece "Enviar agora" só com rede', async () => {
    timeline.set([{ id: 'q', text: '50 reais', occurredAt: '2026-11-05T12:00:00Z', sync: 'queued' }]);
    await fixture.whenStable();
    expect(text()).toContain('Guardado no aparelho.');

    const button = [...fixture.nativeElement.querySelectorAll('button')].find((candidate: HTMLButtonElement) =>
      candidate.textContent?.includes('Enviar agora'),
    ) as HTMLButtonElement;
    button.click();
    expect(retried).toEqual(['q']);

    online.set(false);
    await fixture.whenStable();
    expect(text()).toContain('Guardado no aparelho.');
    expect(text()).not.toContain('Enviar agora');
  });

  it('lançamento guardado de outro dia mostra a data junto do horário', async () => {
    timeline.set([
      { id: 'y', text: 'Dormi mal', occurredAt: '2026-11-04T20:00:00Z', sync: 'queued' },
      { id: 't', text: '50 reais', occurredAt: '2026-11-05T12:00:00Z', sync: 'queued' },
    ]);
    await fixture.whenStable();

    const times = [...fixture.nativeElement.querySelectorAll('.entry__time')].map((el: Element) => el.textContent?.trim());
    expect(times[0]).toMatch(/^04\/11, \d{2}:\d{2}$/);
    expect(times[1]).toMatch(/^\d{2}:\d{2}$/);
  });

  function type(text: string): void {
    input.value = text;
    input.dispatchEvent(new Event('input'));
  }

  function pressEnter(init: KeyboardEventInit = {}): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true, ...init });
    input.dispatchEvent(event);
    return event;
  }

  it('envia com Enter logo depois de digitar, antes de a tela atualizar o botão', () => {
    type('50 reais');
    pressEnter();

    expect(submitted).toEqual(['50 reais']);
    expect(input.value).toBe('');
  });

  it('dois lançamentos seguidos não se misturam', () => {
    type('50 reais');
    pressEnter();
    type('bebi 2 litros de água');
    pressEnter();

    expect(submitted).toEqual(['50 reais', 'bebi 2 litros de água']);
  });

  it('não envia texto em branco', () => {
    type('   ');
    pressEnter();

    expect(submitted).toEqual([]);
  });

  it('não envia enquanto o teclado ainda compõe um caractere (IME, acentos)', () => {
    type('caf');
    pressEnter({ isComposing: true });

    expect(submitted).toEqual([]);
  });

  it('exemplo do estado vazio preenche o campo sem enviar', async () => {
    const example = fixture.nativeElement.querySelector('.example') as HTMLButtonElement;
    example.click();
    await fixture.whenStable();

    expect(input.value).toBe(example.textContent?.trim());
    expect(submitted).toEqual([]);
  });

  it('mostra a data de hoje no formato do design', () => {
    const date = fixture.nativeElement.querySelector('.header__date') as HTMLElement;

    expect(date.textContent?.trim()).toBe('Quinta, 5 de novembro');
  });

  it('mostra o gasto e os hábitos do dia nos cartões de resumo', () => {
    const values = [...fixture.nativeElement.querySelectorAll('.summary__value')].map((el: Element) =>
      el.textContent?.trim(),
    );

    expect(values).toEqual(['R$ 62,50', '3']);
  });
});

describe('TodayPage · confirmação', () => {
  let fixture: ComponentFixture<TodayPage>;
  let resolveResult: ResolveResult;
  let resolved: unknown[];
  let timeline: WritableSignal<TimelineEntry[]>;

  beforeEach(async () => {
    resolveResult = 'ok';
    resolved = [];
    timeline = signal<TimelineEntry[]>([
      { id: 'p', text: PENDING.text, occurredAt: PENDING.occurredAt, sync: 'synced', entry: PENDING },
    ]);
    const store: Partial<EntriesStore> = {
      loadState: signal('loaded'),
      timeline,
      summary: signal({ spent: 0, habitsDone: 0 }),
      load: async () => undefined,
      resolve: async (...args: unknown[]) => {
        resolved.push(args);
        return resolveResult;
      },
      find: () => PENDING,
      habitFor: () => undefined,
      dailyGoals: signal([]),
      budgetAlerts: signal([]),
      today: signal('2026-11-05'),
      earlierPending: signal([]),
      summaryLoaded: signal(true),
      loadEarlierPending: async () => undefined,
      checkDay: () => undefined,
    };
    TestBed.configureTestingModule({
      imports: [TodayPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        { provide: EntriesStore, useValue: store },
        { provide: NOW, useValue: () => new Date('2026-11-05T12:00:00Z') },
      ],
    });
    fixture = TestBed.createComponent(TodayPage);
    await fixture.whenStable();
  });

  function link(): HTMLButtonElement | undefined {
    return [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Quanto foi?'),
    );
  }

  /** Item aberto no painel; o ion-modal em si é do Ionic (e no jsdom sai do lugar ao abrir). */
  function confirming(): { entry: EntryResponse; index: number } | null {
    return (fixture.componentInstance as unknown as { confirming(): { entry: EntryResponse; index: number } | null })
      .confirming();
  }

  it('item pendente mostra só "Precisa confirmar" e o link "Quanto foi?"', () => {
    const chips = [...fixture.nativeElement.querySelectorAll('.chip')].map((c: Element) => c.textContent?.trim());

    expect(chips).toEqual(['Precisa confirmar']);
    expect(link()).toBeTruthy();
  });

  it('o link abre o painel de confirmação', async () => {
    expect(confirming()).toBeNull();

    link()!.click();
    await fixture.whenStable();

    expect(confirming()).toEqual({ entry: PENDING, index: 0 });
  });

  it('lançamento desatualizado reabre o painel com aviso', async () => {
    resolveResult = 'stale';
    const page = fixture.componentInstance as unknown as {
      resolve(id: string, index: number, request: unknown): Promise<void>;
    };

    await page.resolve('p', 0, { action: 'DISMISS' });
    await fixture.whenStable();

    expect(resolved).toEqual([['p', 0, { action: 'DISMISS' }]]);
    expect(fixture.nativeElement.querySelector('.banner[role="status"]')?.textContent).toContain('mudou');
    expect(confirming()).toEqual({ entry: PENDING, index: 0 });
  });

  it('lançamento com vários itens mostra uma linha de chips por item e o link só no pendente', async () => {
    const routine = {
      ...PENDING.items[0],
      type: 'ROUTINE' as const,
      direction: null,
      outcome: 'DONE' as const,
      category: null,
      description: 'Fui à academia',
      confidence: 0.6,
      needsConfirmation: false,
    };
    const compound: EntryResponse = {
      ...PENDING,
      id: 'c',
      text: 'Fui à academia e comprei pão',
      items: [routine, { ...PENDING.items[0], description: 'comprei pão' }],
    };
    timeline.set([{ id: 'c', text: compound.text, occurredAt: compound.occurredAt, sync: 'synced', entry: compound }]);
    await fixture.whenStable();

    const rows = [...fixture.nativeElement.querySelectorAll('.item')] as HTMLElement[];
    expect(rows.map((row) => [...row.querySelectorAll('.chip')].map((c) => c.textContent?.trim()))).toEqual([
      ['Rotina'],
      ['Precisa confirmar'],
    ]);
    expect(rows.map((row) => row.querySelector('.chips')?.getAttribute('aria-label'))).toEqual([
      'O que o MyDly entendeu de: Fui à academia',
      'O que o MyDly entendeu de: comprei pão',
    ]);
    expect(rows[0].querySelector('button')).toBeNull();

    rows[1].querySelector('button')!.click();
    await fixture.whenStable();

    expect(confirming()).toEqual({ entry: compound, index: 1 });
  });

  /** "Gastei 50 reais na farmácia", aceito pelo parser. */
  const ACCEPTED: EntryResponse = {
    ...PENDING,
    id: 'a',
    text: 'Gastei 50 reais na farmácia',
    status: 'CONFIRMED',
    needsConfirmation: false,
    items: [
      {
        ...PENDING.items[0],
        amount: 50,
        category: 'PHARMACY',
        description: 'Gastei 50 reais na farmácia',
        confidence: 0.95,
        needsConfirmation: false,
        state: 'ACCEPTED_AUTO',
        canReopen: true,
      },
    ],
  };

  function show(entry: EntryResponse): Promise<void> {
    timeline.set([{ id: entry.id, text: entry.text, occurredAt: entry.occurredAt, sync: 'synced', entry }]);
    return fixture.whenStable();
  }

  function button(text: string): HTMLButtonElement | undefined {
    return [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.trim() === text,
    );
  }

  it('"Não foi isso" reabre o item e abre o painel para corrigir', async () => {
    await show(ACCEPTED);
    const reopen = button('Não foi isso');
    expect(reopen?.getAttribute('aria-label')).toBe('Não foi isso: Gastei 50 reais na farmácia');

    reopen!.click();
    await fixture.whenStable();

    expect(resolved).toEqual([['a', 0, { action: 'REOPEN' }]]);
    expect(fixture.nativeElement.querySelector('.banner[role="status"]')?.textContent).toContain('Removido dos seus números');
    expect(confirming()).toEqual({ entry: PENDING, index: 0 });
  });

  it('salvar a correção tira o aviso de "Não foi isso"', async () => {
    await show(ACCEPTED);
    button('Não foi isso')!.click();
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      resolve(id: string, index: number, request: unknown): Promise<void>;
    };

    await page.resolve('a', 0, { action: 'CONFIRM_FINANCE', amount: 35, direction: 'EXPENSE', category: 'PHARMACY' });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.banner[role="status"]')).toBeNull();
  });

  it('"Desfazer" traz de volta o item descartado sem abrir o painel', async () => {
    const dismissed: EntryResponse = {
      ...ACCEPTED,
      items: [{ ...ACCEPTED.items[0], dismissed: true, resolvedByUser: true, state: 'DISMISSED' }],
    };
    await show(dismissed);
    expect(button('Desfazer')?.getAttribute('aria-label')).toBe('Desfazer descarte: Gastei 50 reais na farmácia');

    button('Desfazer')!.click();
    await fixture.whenStable();

    expect(resolved).toEqual([['a', 0, { action: 'REOPEN' }]]);
    expect(confirming()).toBeNull();
  });

  it('falha ao reabrir avisa e não abre o painel', async () => {
    resolveResult = 'error';
    await show(ACCEPTED);

    button('Não foi isso')!.click();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.banner[role="status"]')?.textContent).toContain('Não foi possível desfazer');
    expect(confirming()).toBeNull();
  });

  it('falha ao salvar mostra aviso e mantém o painel fechado', async () => {
    resolveResult = 'error';
    const page = fixture.componentInstance as unknown as {
      resolve(id: string, index: number, request: unknown): Promise<void>;
    };

    await page.resolve('p', 0, { action: 'DISMISS' });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.banner[role="status"]')?.textContent).toContain('Não foi possível salvar');
    expect(confirming()).toBeNull();
  });
});

describe('TodayPage · metas e orçamento', () => {
  let fixture: ComponentFixture<TodayPage>;

  async function open(dailyGoals: DailyGoal[], budgetAlerts: BudgetAlert[]): Promise<HTMLElement> {
    const store: Partial<EntriesStore> = {
      loadState: signal('loaded'),
      timeline: signal([]),
      summary: signal({ spent: 85, habitsDone: 1 }),
      load: async () => undefined,
      find: () => undefined,
      habitFor: () => undefined,
      dailyGoals: signal(dailyGoals),
      budgetAlerts: signal(budgetAlerts),
      today: signal('2026-11-05'),
      earlierPending: signal([]),
      summaryLoaded: signal(true),
      loadEarlierPending: async () => undefined,
      checkDay: () => undefined,
    };
    TestBed.configureTestingModule({
      imports: [TodayPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        { provide: EntriesStore, useValue: store },
        { provide: NOW, useValue: () => new Date('2026-11-05T12:00:00Z') },
      ],
    });
    fixture = TestBed.createComponent(TodayPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const water: DailyGoal = { habit: 'HYDRATION', habitName: 'Hidratação', target: 2, unit: 'L', done: 1.5, met: false };

  it('o cartão de hábitos mostra o progresso da meta diária por escrito e numa barra acessível', async () => {
    const root = await open([water], []);

    const goal = root.querySelector('.summary__card:nth-child(2) .goal')!;
    expect(goal.querySelector('.goal__name')?.textContent).toBe('Hidratação');
    expect(goal.querySelector('.goal__value')?.textContent).toBe('1,5 / 2 L');
    const bar = goal.querySelector('[role="progressbar"]')!;
    expect(bar.getAttribute('aria-label')).toBe('Meta de Hidratação');
    expect(bar.getAttribute('aria-valuetext')).toBe('1,5 de 2 L');
    expect(bar.getAttribute('aria-valuenow')).toBe('1.5');
    expect(bar.getAttribute('aria-valuemax')).toBe('2');
    expect((goal.querySelector('.goal__fill') as HTMLElement).style.width).toBe('75%');
  });

  it('meta passada do alvo: aria-valuenow no máximo e o valor real no texto', async () => {
    const root = await open([{ ...water, done: 2.5, met: true }], []);

    const bar = root.querySelector('.goal [role="progressbar"]')!;
    expect(bar.getAttribute('aria-valuenow')).toBe('2');
    expect(bar.getAttribute('aria-valuemax')).toBe('2');
    expect(bar.getAttribute('aria-valuetext')).toBe('2,5 de 2 L, meta cumprida');
    expect(root.querySelector('.goal__value')?.textContent).toBe('2,5 / 2 L');
  });

  it('sem meta diária o cartão fica só com o número', async () => {
    const root = await open([], []);

    expect(root.querySelector('.goals')).toBeNull();
  });

  it('alerta de orçamento discreto, anunciado pelo leitor de tela sem roubar o foco', async () => {
    const root = await open([], [
      { category: 'FOOD', period: 'WEEKLY', limit: 100, spent: 85, percent: 85, status: 'NEAR' },
    ]);

    const region = root.querySelector('.budget-alerts')!;
    expect(region.getAttribute('role')).toBe('status');
    expect(region.textContent).toContain('Alimentação: 85% do limite da semana (R$ 85,00 de R$ 100,00)');
    expect(region.contains(document.activeElement)).toBe(false);
    expect(region.querySelector('[data-status]')?.getAttribute('data-status')).toBe('NEAR');
  });

  it('a região do alerta existe vazia, para anunciar quando o alerta chegar', async () => {
    const root = await open([], []);

    const region = root.querySelector('.budget-alerts[role="status"]');
    expect(region).not.toBeNull();
    expect(region?.textContent?.trim()).toBe('');
  });
});

describe('TodayPage, outros dias e carregamento', () => {
  async function open(changes: Partial<EntriesStore>): Promise<HTMLElement> {
    const store: Partial<EntriesStore> = {
      loadState: signal('loaded'),
      timeline: signal([]),
      waitingCount: signal(0),
      summary: signal({ spent: 0, habitsDone: 0 }),
      load: async () => undefined,
      find: () => PENDING,
      habitFor: () => undefined,
      dailyGoals: signal([]),
      budgetAlerts: signal([]),
      today: signal('2026-11-06'),
      earlierPending: signal([]),
      summaryLoaded: signal(true),
      loadEarlierPending: async () => undefined,
      checkDay: () => undefined,
      ...changes,
    };
    TestBed.configureTestingModule({
      imports: [TodayPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        { provide: EntriesStore, useValue: store },
        { provide: Connectivity, useValue: { online: signal(true) } },
        { provide: NOW, useValue: () => new Date('2026-11-06T12:00:00Z') },
      ],
    });
    const fixture = TestBed.createComponent(TodayPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('mostra os pendentes de outros dias com a data e o "Quanto foi?"', async () => {
    const root = await open({
      earlierPending: signal([{ id: 'p', text: 'Comprei pão', occurredAt: PENDING.occurredAt, sync: 'synced', entry: PENDING }]),
    });

    const section = root.querySelector('[aria-labelledby="earlier-title"]');
    expect(section?.textContent).toContain('Ficou para confirmar');
    expect(section?.textContent).toContain('Comprei pão');
    expect(section?.textContent).toContain('05/11');
    expect([...section!.querySelectorAll('button')].some((b) => b.textContent?.includes('Quanto foi?'))).toBe(true);
  });

  it('sem pendentes de outros dias, a seção não aparece', async () => {
    const root = await open({});

    expect(root.querySelector('[aria-labelledby="earlier-title"]')).toBeNull();
  });

  it('enquanto o resumo do dia não chega, os cartões mostram um traço, e não R$ 0,00', async () => {
    const root = await open({ summaryLoaded: signal(false) });

    const cards = root.querySelector('[aria-label="Resumo de hoje"]')!;
    expect(cards.textContent).not.toContain('R$');
    expect(cards.textContent).toContain('—');
    expect(cards.textContent).toContain('Carregando');
  });
});
