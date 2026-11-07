import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { Observable, of, throwError } from 'rxjs';
import { ImportedTransaction, ImportResponse } from '../entries/import.models';
import { ImportsApi } from '../entries/imports-api';
import { ImportsPage } from './imports.page';

const IMPORTED: ImportedTransaction = {
  id: 't1',
  date: '2026-11-05',
  amount: 50,
  direction: 'EXPENSE',
  description: 'DROGASIL',
  status: 'IMPORTED',
  matchedEntryId: null,
  matchedItemIndex: null,
};

describe('ImportsPage', () => {
  let fixture: ComponentFixture<ImportsPage>;
  let root: HTMLElement;
  let list: ImportedTransaction[];
  let uploads: File[];
  let toggles: [string, boolean][];
  let uploadAnswer: () => Observable<ImportResponse>;

  beforeEach(async () => {
    list = [IMPORTED];
    uploads = [];
    toggles = [];
    uploadAnswer = () =>
      of({ format: 'CSV', read: 2, added: 1, matched: 1, duplicates: 0, transactions: [IMPORTED] } as ImportResponse);
    TestBed.configureTestingModule({
      imports: [ImportsPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        provideRouter([]),
        {
          provide: ImportsApi,
          useValue: {
            transactions: () => of(list),
            importStatement: (file: File) => {
              uploads.push(file);
              return uploadAnswer();
            },
            setIgnored: (id: string, ignored: boolean) => {
              toggles.push([id, ignored]);
              return of({ ...IMPORTED, status: ignored ? 'IGNORED' : 'IMPORTED' });
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ImportsPage);
    await fixture.whenStable();
    root = fixture.nativeElement as HTMLElement;
  });

  function choose(file: File): void {
    const input = root.querySelector('#statement-file') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
  }

  async function submit(): Promise<void> {
    root.querySelector('form')?.dispatchEvent(new Event('submit', { cancelable: true }));
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  it('o campo de arquivo tem rótulo e a lista mostra o que cada transação faz', () => {
    expect(root.querySelector('label[for="statement-file"]')?.textContent).toContain('Escolher arquivo OFX ou CSV');
    expect(root.textContent).toContain('DROGASIL');
    expect(root.textContent).toContain('Saída · R$ 50,00');
    expect(root.textContent).toContain('Conta nos seus números');
  });

  it('importa o arquivo escolhido e anuncia o resumo', async () => {
    const file = new File(['Data;Valor\n01/11/2026;-50,00'], 'extrato.csv', { type: 'text/csv' });
    choose(file);
    await fixture.whenStable();
    expect(root.querySelector('.file__text')?.textContent).toContain('extrato.csv');

    await submit();

    expect(uploads).toEqual([file]);
    expect(root.querySelector('[role="status"]')?.textContent).toContain(
      '2 transações lidas: 1 entrou nos seus números e 1 já estava lançada e não conta duas vezes.',
    );
  });

  it('sem arquivo, pede para escolher', async () => {
    await submit();

    expect(uploads).toEqual([]);
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Escolha o arquivo do extrato.');
  });

  it('mostra o motivo quando a API recusa o arquivo', async () => {
    uploadAnswer = () =>
      throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'Linha 2: valor inválido.' } }));
    choose(new File(['x'], 'a.csv'));

    await submit();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Linha 2: valor inválido.');
  });

  it('"Não contar" e "Contar de novo" trocam a linha pela resposta da API', async () => {
    const button = () => root.querySelector('.row .link-button') as HTMLButtonElement;
    expect(button().getAttribute('aria-label')).toBe('Não contar: DROGASIL de 05/11');

    button().click();
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    expect(toggles).toEqual([['t1', true]]);
    expect(root.textContent).toContain('Não conta');
    expect(button().textContent).toContain('Contar de novo');

    button().click();
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    expect(toggles).toEqual([
      ['t1', true],
      ['t1', false],
    ]);
  });
});
