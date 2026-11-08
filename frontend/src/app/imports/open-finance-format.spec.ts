import { HttpErrorResponse } from '@angular/common/http';
import { OpenFinanceConnection } from '../entries/open-finance.models';
import { connectionStatusLabel, lastSyncLabel, openFinanceErrorMessage } from './open-finance-format';

const CONNECTION: OpenFinanceConnection = {
  id: 'c1',
  institution: 'Pluggy Bank',
  status: 'UPDATED',
  lastSyncedAt: '2026-11-07T21:30:00Z',
};

describe('formatação do Open Finance', () => {
  it.each([
    ['UPDATED', 'Conectado'],
    ['UPDATING', 'O banco ainda está enviando os dados. Sincronize de novo em instantes.'],
    ['LOGIN_ERROR', 'O banco pediu para entrar de novo. Desconecte e conecte outra vez.'],
    ['OUTDATED', 'O banco pediu para entrar de novo. Desconecte e conecte outra vez.'],
    ['NOT_FOUND', 'A conexão não existe mais no Pluggy. Desconecte e conecte outra vez.'],
    ['OUTRO', 'Situação desconhecida no Pluggy.'],
  ])('%s', (status, label) => {
    expect(connectionStatusLabel({ ...CONNECTION, status })).toBe(label);
  });

  it('última sincronização no horário de São Paulo', () => {
    expect(lastSyncLabel(CONNECTION)).toBe('Sincronizado em 07/11, 18:30');
    expect(lastSyncLabel({ ...CONNECTION, lastSyncedAt: null })).toBe('Ainda não sincronizado');
  });

  it.each([
    [new HttpErrorResponse({ status: 400, error: { detail: 'O MyDly só aceita bancos de teste (sandbox).' } }),
      'O MyDly só aceita bancos de teste (sandbox).'],
    [new HttpErrorResponse({ status: 0 }), 'Sem conexão. Para falar com o banco, o MyDly precisa de internet.'],
    [new HttpErrorResponse({ status: 502, error: null }), 'Não foi possível falar com o banco agora. Tente de novo.'],
    [new Error('x'), 'Não foi possível falar com o banco agora. Tente de novo.'],
  ])('erro %#', (error, expected) => {
    expect(openFinanceErrorMessage(error)).toBe(expected);
  });
});
