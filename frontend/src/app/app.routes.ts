import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './entries/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'MyDly · Entrar',
    canActivate: [guestGuard],
    loadComponent: () => import('./login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    title: 'MyDly · Hoje',
    canActivate: [authGuard],
    loadComponent: () => import('./today/today.page').then((m) => m.TodayPage),
  },
  {
    path: 'resumo',
    title: 'MyDly · Resumo',
    canActivate: [authGuard],
    loadComponent: () => import('./summary/summary.page').then((m) => m.SummaryPage),
  },
  {
    path: 'metas',
    title: 'MyDly · Metas',
    canActivate: [authGuard],
    loadComponent: () => import('./goals/goals.page').then((m) => m.GoalsPage),
  },
  { path: '**', redirectTo: '' },
];
