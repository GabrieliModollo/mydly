import { HttpErrorResponse } from '@angular/common/http';

/** Regras do cadastro, as mesmas da API (ADR 0018). */
export const MIN_PASSWORD_LENGTH = 8;
/** O BCrypt da API só usa os primeiros 72 bytes da senha. */
export const MAX_PASSWORD_BYTES = 72;
const USERNAME = /^[a-z0-9][a-z0-9._-]{2,39}$/;

export const REGISTER_MESSAGES = {
  missing: 'Informe o usuário e a senha.',
  invalidUsername: 'Use de 3 a 40 letras sem acento, números, ponto, hífen ou sublinhado no usuário.',
  passwordTooShort: `A senha precisa de pelo menos ${MIN_PASSWORD_LENGTH} caracteres.`,
  passwordTooLong: 'A senha é longa demais.',
  mismatch: 'As senhas não conferem.',
  taken: 'Esse usuário já existe. Escolha outro.',
  offline: 'Sem conexão. Para criar a conta, o MyDly precisa de internet.',
  tooMany: 'Muitas tentativas. Espere um pouco e tente de novo.',
  unknown: 'Não foi possível criar a conta agora. Tente de novo.',
} as const;

/** Campo que precisa de correção, para a tela pôr o foco nele. */
export type RegisterField = 'username' | 'password' | 'confirmation';

export interface RegisterProblem {
  message: string;
  field: RegisterField;
}

/** Uma falha ao enviar: sem campo quando o problema não é do que foi digitado (rede, servidor). */
export interface RegisterFailure {
  message: string;
  field: RegisterField | null;
}

/**
 * Confere o cadastro antes de enviar, com as regras da API: o usuário é comparado sem espaços nas
 * pontas e em minúsculas, como a API guarda. Devolve {@code null} quando está tudo certo.
 */
export function registerProblem(username: string, password: string, confirmation: string): RegisterProblem | null {
  const name = username.trim().toLowerCase();
  if (!name) {
    return { message: REGISTER_MESSAGES.missing, field: 'username' };
  }
  if (!USERNAME.test(name)) {
    return { message: REGISTER_MESSAGES.invalidUsername, field: 'username' };
  }
  if (!password) {
    return { message: REGISTER_MESSAGES.missing, field: 'password' };
  }
  if ([...password].length < MIN_PASSWORD_LENGTH) {
    return { message: REGISTER_MESSAGES.passwordTooShort, field: 'password' };
  }
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return { message: REGISTER_MESSAGES.passwordTooLong, field: 'password' };
  }
  if (password !== confirmation) {
    return { message: REGISTER_MESSAGES.mismatch, field: 'confirmation' };
  }
  return null;
}

/**
 * O cadastro que a API recusou ou que não chegou a ela: a mensagem e, quando a recusa aponta um campo,
 * qual é.
 */
export function registerFailure(error: unknown): RegisterFailure {
  if (!(error instanceof HttpErrorResponse)) {
    return { message: REGISTER_MESSAGES.unknown, field: null };
  }
  switch (error.status) {
    case 0:
      return { message: REGISTER_MESSAGES.offline, field: null };
    case 409:
      return { message: REGISTER_MESSAGES.taken, field: 'username' };
    case 429:
      return { message: REGISTER_MESSAGES.tooMany, field: null };
    case 400:
      return refusal(error.error);
    default:
      return { message: REGISTER_MESSAGES.unknown, field: null };
  }
}

/** A API diz o motivo da recusa no campo {@code code} do problema. */
function refusal(body: unknown): RegisterFailure {
  const code = typeof body === 'object' && body !== null ? (body as { code?: unknown }).code : undefined;
  switch (code) {
    case 'INVALID_USERNAME':
      return { message: REGISTER_MESSAGES.invalidUsername, field: 'username' };
    case 'PASSWORD_TOO_SHORT':
      return { message: REGISTER_MESSAGES.passwordTooShort, field: 'password' };
    case 'PASSWORD_TOO_LONG':
      return { message: REGISTER_MESSAGES.passwordTooLong, field: 'password' };
    default:
      return { message: REGISTER_MESSAGES.unknown, field: null };
  }
}
