import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
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

  it.each([
    ['900', 'Muitas tentativas de login. Tente de novo em 15 minutos.'],
    ['61', 'Muitas tentativas de login. Tente de novo em 2 minutos.'],
    ['45', 'Muitas tentativas de login. Tente de novo em 1 minuto.'],
    ['0', 'Muitas tentativas de login. Espere alguns minutos e tente de novo.'],
    ['Wed, 21 Oct 2044 07:28:00 GMT', 'Muitas tentativas de login. Espere alguns minutos e tente de novo.'],
  ])('429 com Retry-After %s', (retryAfter, expected) => {
    const headers = new HttpHeaders({ 'Retry-After': retryAfter });
    expect(loginErrorMessage(new HttpErrorResponse({ status: 429, headers }))).toBe(expected);
  });

  it('429 sem Retry-After não promete prazo', () => {
    expect(loginErrorMessage(new HttpErrorResponse({ status: 429 }))).toBe(
      'Muitas tentativas de login. Espere alguns minutos e tente de novo.',
    );
  });

  it('erro que não é da API', () => {
    expect(loginErrorMessage(new Error('x'))).toBe('Não foi possível entrar agora. Tente de novo.');
  });
});
