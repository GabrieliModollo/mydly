import { HttpErrorResponse } from '@angular/common/http';
import { Chip, formatMoney } from '../entries/entry-format';
import { ImportedTransaction, ImportResponse } from '../entries/import.models';

/** Resumo de uma importação, em uma frase. */
export function importSummary(response: ImportResponse): string {
  const parts: string[] = [];
  if (response.added > 0) {
    parts.push(response.added === 1 ? '1 entrou nos seus números' : `${response.added} entraram nos seus números`);
  }
  if (response.matched > 0) {
    parts.push(
      response.matched === 1
        ? '1 já estava lançada e não conta duas vezes'
        : `${response.matched} já estavam lançadas e não contam duas vezes`,
    );
  }
  if (response.duplicates > 0) {
    parts.push(
      response.duplicates === 1 ? '1 já tinha sido importada' : `${response.duplicates} já tinham sido importadas`,
    );
  }
  const read = response.read === 1 ? '1 transação lida' : `${response.read} transações lidas`;
  return `${read}: ${joinPortuguese(parts)}.`;
}

/** O chip do valor: saída em laranja, entrada em azul, com o tipo por escrito. */
export function amountChip(transaction: ImportedTransaction): Chip {
  const income = transaction.direction === 'INCOME';
  return { label: `${income ? 'Entrada' : 'Saída'} · ${formatMoney(transaction.amount)}`, tone: income ? 'income' : 'expense' };
}

/** O que a transação faz nos números, por escrito. */
export function statusLabel(transaction: ImportedTransaction): string {
  switch (transaction.status) {
    case 'IMPORTED':
      return 'Conta nos seus números';
    case 'MATCHED':
      return 'Já estava lançado no app';
    case 'IGNORED':
      return 'Não conta';
  }
}

/** Dia da transação no formato curto ("05/11"). */
export function shortDate(date: string): string {
  const [, month, day] = date.split('-');
  return `${day}/${month}`;
}

/** Mensagem de uma importação que falhou: a da API quando ela explica, ou uma genérica. */
export function importErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Sem conexão. Para importar o extrato, o MyDly precisa de internet.';
    }
    if (error.status === 413) {
      return 'O arquivo passa de 2 MB. Exporte um período menor.';
    }
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (error.status === 400 && typeof detail === 'string') {
      return detail;
    }
  }
  return 'Não foi possível importar o extrato. Tente de novo.';
}

function joinPortuguese(parts: string[]): string {
  if (parts.length === 0) {
    return 'nada novo';
  }
  if (parts.length === 1) {
    return parts[0];
  }
  return `${parts.slice(0, -1).join(', ')} e ${parts[parts.length - 1]}`;
}
