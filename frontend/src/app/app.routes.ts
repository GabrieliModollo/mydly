import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'MyDly · Hoje',
    loadComponent: () => import('./today/today.page').then((m) => m.TodayPage),
  },
  {
    path: 'resumo',
    title: 'MyDly · Resumo',
    loadComponent: () => import('./summary/summary.page').then((m) => m.SummaryPage),
  },
  { path: '**', redirectTo: '' },
];
