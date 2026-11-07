import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSession } from './session';

/** As telas do app só abrem com sessão válida; sem ela, vão para o login. */
export const authGuard: CanActivateFn = () =>
  inject(AuthSession).isLoggedIn() || inject(Router).createUrlTree(['/login']);

/** Quem já está logado e abre o login vai direto para o Hoje. */
export const guestGuard: CanActivateFn = () =>
  !inject(AuthSession).isLoggedIn() || inject(Router).createUrlTree(['/']);
