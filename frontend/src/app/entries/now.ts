import { InjectionToken } from '@angular/core';

/** Relógio do app. Substituível nos testes. */
export const NOW = new InjectionToken<() => Date>('NOW', {
  providedIn: 'root',
  factory: () => () => new Date(),
});
