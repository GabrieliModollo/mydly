import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { entryTimeFormat, formatDayLabel } from './day-label';

registerLocaleData(localePt, 'pt-BR');

describe('formatDayLabel', () => {
  it.each([
    ['2026-11-05T12:00:00', 'Quinta, 5 de novembro'],
    ['2026-11-02T12:00:00', 'Segunda, 2 de novembro'],
    ['2026-11-07T12:00:00', 'Sábado, 7 de novembro'],
    ['2026-11-08T12:00:00', 'Domingo, 8 de novembro'],
  ])('%s vira "%s"', (iso, expected) => {
    expect(formatDayLabel(new Date(iso), 'pt-BR')).toBe(expected);
  });
});

describe('entryTimeFormat', () => {
  const now = new Date('2026-11-05T12:00:00Z');

  it.each([
    ['2026-11-05T03:00:00Z', 'HH:mm'], // meia-noite em São Paulo: já é hoje
    ['2026-11-05T23:00:00Z', 'HH:mm'],
    ['2026-11-05T02:59:00Z', 'dd/MM, HH:mm'], // 23h59 do dia 4 em São Paulo
    ['2026-11-01T12:00:00Z', 'dd/MM, HH:mm'],
  ])('%s usa "%s"', (occurredAt, expected) => {
    expect(entryTimeFormat(occurredAt, now)).toBe(expected);
  });
});
