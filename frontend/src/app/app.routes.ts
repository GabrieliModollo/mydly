import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'MyDly · Hoje',
    loadComponent: () => import('./today/today.page').then((m) => m.TodayPage),
  },
  { path: '**', redirectTo: '' },
];
