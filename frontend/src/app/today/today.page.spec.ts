import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIonicAngular } from '@ionic/angular';
import { EntriesStore, NOW, ResolveResult, TimelineEntry } from '../entries/entries-store';
import { EntryResponse } from '../entries/entry.models';
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
    },
  ],
};

describe('TodayPage', () => {
  let fixture: ComponentFixture<TodayPage>;
  let submitted: string[];
  let input: HTMLInputElement;
  let timeline: WritableSignal<TimelineEntry[]>;
  let resolveResult: ResolveResult;

  beforeEach(async () => {
    submitted = [];
    timeline = signal([]);
    resolveResult = 'ok';
    const store: Partial<EntriesStore> = {
      loadState: signal('loaded'),
      timeline,
      summary: signal({ spent: 62.5, habitsDone: 3 }),
      load: async () => undefined,
      submit: async (text: string) => {
        submitted.push(text);
      },
      resolve: async () => resolveResult,
      find: () => PENDING,
    };
    TestBed.configureTestingModule({
      imports: [TodayPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        { provide: EntriesStore, useValue: store },
        { provide: NOW, useValue: () => new Date('2026-11-05T12:00:00Z') },
      ],
    });
    fixture = TestBed.createComponent(TodayPage);
    await fixture.whenStable();
    input = fixture.nativeElement.querySelector('#entry-text');
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
    };
    TestBed.configureTestingModule({
      imports: [TodayPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
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
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('mudou');
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

  it('falha ao salvar mostra aviso e mantém o painel fechado', async () => {
    resolveResult = 'error';
    const page = fixture.componentInstance as unknown as {
      resolve(id: string, index: number, request: unknown): Promise<void>;
    };

    await page.resolve('p', 0, { action: 'DISMISS' });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Não foi possível salvar');
    expect(confirming()).toBeNull();
  });
});
