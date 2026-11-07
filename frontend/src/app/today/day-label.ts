import { formatDate } from '@angular/common';
import { dayInSaoPaulo } from '../entries/entry-format';

/**
 * Formato do horário de um lançamento na lista: só a hora quando é de hoje, e a data junto quando é de
 * outro dia (um lançamento guardado no aparelho desde ontem, por exemplo).
 */
export function entryTimeFormat(occurredAt: string, now: Date): string {
  return dayInSaoPaulo(new Date(occurredAt)) === dayInSaoPaulo(now) ? 'HH:mm' : 'dd/MM, HH:mm';
}

/**
 * Data do cabeçalho no formato do design: "Quinta, 5 de novembro".
 * Tira o "-feira" e deixa só a primeira letra maiúscula, como se escreve em português.
 */
export function formatDayLabel(date: Date, locale: string): string {
  const text = formatDate(date, "EEEE, d 'de' MMMM", locale).replace('-feira', '');
  return text.charAt(0).toLocaleUpperCase(locale) + text.slice(1);
}
