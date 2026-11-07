import { HttpErrorResponse } from '@angular/common/http';

/** Mensagem do login que falhou: senha errada, sem internet ou outro problema. */
export function loginErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401) {
      return 'Usuário ou senha incorretos.';
    }
    if (error.status === 0) {
      return 'Sem conexão. Para entrar, o MyDly precisa de internet.';
    }
  }
  return 'Não foi possível entrar agora. Tente de novo.';
}
