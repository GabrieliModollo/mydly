import { HttpErrorResponse } from '@angular/common/http';
import { ImportedTransaction, ImportResponse } from '../entries/import.models';
import { amountChip, importErrorMessage, importSummary, shortDate, statusLabel } from './import-format';

function response(read: number, added: number, matched: number, duplicates: number): ImportResponse {
  return { format: 'CSV', read, added, matched, duplicates, transactions: [] };
}

const TRANSACTION: ImportedTransaction = {
  id: 't1',
  date: '2026-11-05',
  amount: 50,
  direction: 'EXPENSE',
  description: 'DROGASIL',
  status: 'IMPORTED',
  matchedEntryId: null,
  matchedItemIndex: null,
};

describe('importSummary', () => {
  it.each([
    [response(3, 3, 0, 0), '3 transações lidas: 3 entraram nos seus números.'],
    [response(1, 1, 0, 0), '1 transação lida: 1 entrou nos seus números.'],
    [
      response(5, 2, 2, 1),
      '5 transações lidas: 2 entraram nos seus números, 2 já estavam lançadas e não contam duas vezes e 1 já tinha sido importada.',
    ],
    [response(2, 0, 1, 1), '2 transações lidas: 1 já estava lançada e não conta duas vezes e 1 já tinha sido importada.'],
    [response(4, 0, 0, 4), '4 transações lidas: 4 já tinham sido importadas.'],
  ])('%#', (input, expected) => {
    expect(importSummary(input)).toBe(expected);
  });
});

describe('formatação das transações', () => {
  it('saída em laranja e entrada em azul, com o tipo por escrito', () => {
    expect(amountChip(TRANSACTION)).toEqual({ label: 'Saída · R$ 50,00', tone: 'expense' });
    expect(amountChip({ ...TRANSACTION, direction: 'INCOME', amount: 3000 })).toEqual({
      label: 'Entrada · R$ 3.000,00',
      tone: 'income',
    });
  });

  it.each([
    ['IMPORTED', 'Conta nos seus números'],
    ['MATCHED', 'Já estava lançado no app'],
    ['IGNORED', 'Não conta'],
  ] as const)('%s', (status, label) => {
    expect(statusLabel({ ...TRANSACTION, status })).toBe(label);
  });

  it('data curta', () => {
    expect(shortDate('2026-11-05')).toBe('05/11');
  });
});

describe('importErrorMessage', () => {
  it('usa a explicação da API num 400', () => {
    const error = new HttpErrorResponse({ status: 400, error: { detail: 'Linha 3: data inválida.' } });

    expect(importErrorMessage(error)).toBe('Linha 3: data inválida.');
  });

  it.each([
    [new HttpErrorResponse({ status: 0 }), 'Sem conexão. Para importar o extrato, o MyDly precisa de internet.'],
    [new HttpErrorResponse({ status: 413 }), 'O arquivo passa de 2 MB. Exporte um período menor.'],
    [new HttpErrorResponse({ status: 500 }), 'Não foi possível importar o extrato. Tente de novo.'],
    [new HttpErrorResponse({ status: 400, error: null }), 'Não foi possível importar o extrato. Tente de novo.'],
    [new Error('x'), 'Não foi possível importar o extrato. Tente de novo.'],
  ])('%#', (error, expected) => {
    expect(importErrorMessage(error)).toBe(expected);
  });
});
