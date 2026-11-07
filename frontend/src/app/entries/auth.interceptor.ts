import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthApi } from './auth-api';
import { AuthSession } from './session';

/**
 * Põe o token em todo pedido à API, menos no login. Uma recusa por falta de token ou token vencido
 * (401) encerra a sessão e leva ao login; quem fez o pedido recebe o erro mesmo assim e decide o que
 * fazer (a fila de lançamentos, por exemplo, guarda o lançamento para depois do login).
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/') || request.url === AuthApi.LOGIN_URL) {
    return next(request);
  }
  const session = inject(AuthSession);
  const router = inject(Router);
  const token = session.token();
  const authorized = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        session.end();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
