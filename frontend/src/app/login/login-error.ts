import { HttpErrorResponse } from '@angular/common/http';

/** A mensagem do login que falhou, a partir da resposta da API. */
export function loginErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401) return 'Usuário ou senha incorretos.';
    if (error.status === 429) return blockedMessage(error.headers?.get('Retry-After') ?? null);
    if (error.status === 0) return 'Sem conexão. Para entrar, o MyDly precisa de internet.';
  }
  return 'Não foi possível entrar agora. Tente de novo.';
}

/**
 * Senhas erradas demais (ADR 0017). O header {@code Retry-After} traz os segundos até poder tentar de
 * novo; sem ele, ou com um valor que não é número, a mensagem não promete prazo.
 */
function blockedMessage(retryAfter: string | null): string {
  const seconds = retryAfter !== null && /^\d+$/.test(retryAfter.trim()) ? Number(retryAfter.trim()) : NaN;
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return 'Muitas tentativas de login. Espere alguns minutos e tente de novo.';
  }
  const minutes = Math.ceil(seconds / 60);
  const wait = minutes === 1 ? '1 minuto' : `${minutes} minutos`;
  return `Muitas tentativas de login. Tente de novo em ${wait}.`;
}
