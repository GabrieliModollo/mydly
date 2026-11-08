import { HttpErrorResponse } from '@angular/common/http';
import { OpenFinanceConnection } from '../entries/open-finance.models';

/** O status do item no Pluggy, por escrito. */
export function connectionStatusLabel(connection: OpenFinanceConnection): string {
  switch (connection.status) {
    case 'UPDATED':
      return 'Conectado';
    case 'UPDATING':
    case 'MERGING':
      return 'O banco ainda está enviando os dados. Sincronize de novo em instantes.';
    case 'LOGIN_ERROR':
    case 'OUTDATED':
    case 'WAITING_USER_INPUT':
    case 'WAITING_USER_ACTION':
      return 'O banco pediu para entrar de novo. Desconecte e conecte outra vez.';
    case 'NOT_FOUND':
      return 'A conexão não existe mais no Pluggy. Desconecte e conecte outra vez.';
    default:
      return 'Situação desconhecida no Pluggy.';
  }
}

/** Quando foi a última sincronização, em São Paulo ("Sincronizado em 07/11, 18:30"). */
export function lastSyncLabel(connection: OpenFinanceConnection, locale = 'pt-BR'): string {
  if (!connection.lastSyncedAt) {
    return 'Ainda não sincronizado';
  }
  const when = new Intl.DateTimeFormat(locale, {
    timeZone: 'America/Sao_Paulo',
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(connection.lastSyncedAt));
  return `Sincronizado em ${when}`;
}

/** Mensagem de uma operação de Open Finance que falhou: a da API quando ela explica. */
export function openFinanceErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Sem conexão. Para falar com o banco, o MyDly precisa de internet.';
    }
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string') {
      return detail;
    }
  }
  return 'Não foi possível falar com o banco agora. Tente de novo.';
}
