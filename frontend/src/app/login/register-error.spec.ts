import { HttpErrorResponse } from '@angular/common/http';
import { REGISTER_MESSAGES, registerFailure, registerProblem } from './register-error';

describe('registerProblem', () => {
  it('cadastro válido não tem problema', () => {
    expect(registerProblem(' Ana.Souza ', 'uma-senha-boa', 'uma-senha-boa')).toBeNull();
  });

  it.each([
    ['', 'uma-senha-boa', 'uma-senha-boa', REGISTER_MESSAGES.missing, 'username'],
    ['   ', 'uma-senha-boa', 'uma-senha-boa', REGISTER_MESSAGES.missing, 'username'],
    ['ab', 'uma-senha-boa', 'uma-senha-boa', REGISTER_MESSAGES.invalidUsername, 'username'],
    ['com espaço', 'uma-senha-boa', 'uma-senha-boa', REGISTER_MESSAGES.invalidUsername, 'username'],
    ['joão', 'uma-senha-boa', 'uma-senha-boa', REGISTER_MESSAGES.invalidUsername, 'username'],
    ['-hifen', 'uma-senha-boa', 'uma-senha-boa', REGISTER_MESSAGES.invalidUsername, 'username'],
    ['a'.repeat(41), 'uma-senha-boa', 'uma-senha-boa', REGISTER_MESSAGES.invalidUsername, 'username'],
    ['ana', '', '', REGISTER_MESSAGES.missing, 'password'],
    ['ana', '1234567', '1234567', REGISTER_MESSAGES.passwordTooShort, 'password'],
    ['ana', 'ç'.repeat(37), 'ç'.repeat(37), REGISTER_MESSAGES.passwordTooLong, 'password'],
    ['ana', 'uma-senha-boa', 'outra-senha', REGISTER_MESSAGES.mismatch, 'confirmation'],
  ])('"%s" / "%s" / "%s"', (username, password, confirmation, message, field) => {
    expect(registerProblem(username, password, confirmation)).toEqual({ message, field });
  });

  it('conta letras com acento como um caractere e aceita até 72 bytes', () => {
    expect(registerProblem('ana', 'ç'.repeat(8), 'ç'.repeat(8))).toBeNull();
    expect(registerProblem('ana', 'ç'.repeat(36), 'ç'.repeat(36))).toBeNull();
  });
});

describe('registerFailure', () => {
  it.each([
    [409, null, REGISTER_MESSAGES.taken, 'username'],
    [0, null, REGISTER_MESSAGES.offline, null],
    [429, null, REGISTER_MESSAGES.tooMany, null],
    [400, { code: 'INVALID_USERNAME' }, REGISTER_MESSAGES.invalidUsername, 'username'],
    [400, { code: 'PASSWORD_TOO_SHORT' }, REGISTER_MESSAGES.passwordTooShort, 'password'],
    [400, { code: 'PASSWORD_TOO_LONG' }, REGISTER_MESSAGES.passwordTooLong, 'password'],
    [400, { code: 'OUTRO' }, REGISTER_MESSAGES.unknown, null],
    [400, null, REGISTER_MESSAGES.unknown, null],
    [500, null, REGISTER_MESSAGES.unknown, null],
  ])('status %i com %o', (status, error, message, field) => {
    expect(registerFailure(new HttpErrorResponse({ status, error }))).toEqual({ message, field });
  });

  it('erro que não é da API', () => {
    expect(registerFailure(new Error('x'))).toEqual({ message: REGISTER_MESSAGES.unknown, field: null });
  });
});
