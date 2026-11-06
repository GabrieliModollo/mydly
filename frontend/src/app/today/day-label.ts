import { formatDate } from '@angular/common';

/**
 * Data do cabeçalho no formato do design: "Quinta, 5 de novembro".
 * Tira o "-feira" e deixa só a primeira letra maiúscula, como se escreve em português.
 */
export function formatDayLabel(date: Date, locale: string): string {
  const text = formatDate(date, "EEEE, d 'de' MMMM", locale).replace('-feira', '');
  return text.charAt(0).toLocaleUpperCase(locale) + text.slice(1);
}
