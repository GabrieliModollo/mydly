import { HttpErrorResponse } from '@angular/common/http';
import { loginErrorMessage } from './login-error';

describe('loginErrorMessage', () => {
  it.each([
    [401, 'Usuário ou senha incorretos.'],
    [0, 'Sem conexão. Para entrar, o MyDly precisa de internet.'],
    [500, 'Não foi possível entrar agora. Tente de novo.'],
    [400, 'Não foi possível entrar agora. Tente de novo.'],
  ])('status %i', (status, expected) => {
    expect(loginErrorMessage(new HttpErrorResponse({ status }))).toBe(expected);
  });

  it('erro que não é da API', () => {
    expect(loginErrorMessage(new Error('x'))).toBe('Não foi possível entrar agora. Tente de novo.');
  });
});
