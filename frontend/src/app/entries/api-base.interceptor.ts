import { HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken, inject } from '@angular/core';
import { environment } from '../../environments/environment';

/**
 * Endereço da API. Na web é vazio (mesma origem); no app nativo vem do build ({@code MYDLY_API_URL}),
 * porque lá a página é servida pelo próprio aparelho (ADR 0016). Substituível nos testes.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});

/** Põe o endereço da API na frente dos caminhos {@code /api/...}; na web não muda nada. */
export const apiBaseInterceptor: HttpInterceptorFn = (request, next) => {
  const base = inject(API_BASE_URL);
  if (!base || !request.url.startsWith('/api/')) {
    return next(request);
  }
  return next(request.clone({ url: base + request.url }));
};
