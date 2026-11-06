import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIonicAngular } from '@ionic/angular';
import { EntriesStore, NOW } from '../entries/entries-store';
import { TodayPage } from './today.page';

registerLocaleData(localePt, 'pt-BR');

describe('TodayPage', () => {
  let fixture: ComponentFixture<TodayPage>;
  let submitted: string[];
  let input: HTMLInputElement;

  beforeEach(async () => {
    submitted = [];
    const store: Partial<EntriesStore> = {
      loadState: signal('loaded'),
      timeline: signal([]),
      summary: signal({ spent: 62.5, habitsDone: 3 }),
      load: async () => undefined,
      submit: async (text: string) => {
        submitted.push(text);
      },
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
