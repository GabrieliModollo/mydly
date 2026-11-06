import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { formatDayLabel } from './day-label';

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
