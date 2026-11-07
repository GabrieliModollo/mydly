import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Category } from './entry.models';
import {
  BudgetRequest,
  BudgetResponse,
  HabitGoalRequest,
  HabitGoalResponse,
  Preferences,
} from './goal.models';

/**
 * Acesso HTTP às metas de hábito (routines), aos orçamentos (finance) e às preferências. Toda mudança
 * vale de hoje em diante no servidor; o app não manda data.
 */
@Injectable({ providedIn: 'root' })
export class GoalsApi {
  private readonly http = inject(HttpClient);

  /** Todos os hábitos do catálogo, com a meta vigente hoje (ou sem meta). */
  habitGoals(): Observable<HabitGoalResponse[]> {
    return this.http.get<HabitGoalResponse[]>('/api/v1/goals/habits');
  }

  saveHabitGoal(habit: string, request: HabitGoalRequest): Observable<HabitGoalResponse> {
    return this.http.put<HabitGoalResponse>(`/api/v1/goals/habits/${encodeURIComponent(habit)}`, request);
  }

  /** Tira a meta de hoje em diante; sem meta, não muda nada. */
  removeHabitGoal(habit: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/goals/habits/${encodeURIComponent(habit)}`);
  }

  /** Todas as categorias, com o orçamento vigente e o gasto do período atual. */
  budgets(): Observable<BudgetResponse[]> {
    return this.http.get<BudgetResponse[]>('/api/v1/budgets');
  }

  saveBudget(category: Category, request: BudgetRequest): Observable<BudgetResponse> {
    return this.http.put<BudgetResponse>(`/api/v1/budgets/${category}`, request);
  }

  removeBudget(category: Category): Observable<void> {
    return this.http.delete<void>(`/api/v1/budgets/${category}`);
  }

  preferences(): Observable<Preferences> {
    return this.http.get<Preferences>('/api/v1/preferences');
  }

  savePreferences(preferences: Preferences): Observable<Preferences> {
    return this.http.put<Preferences>('/api/v1/preferences', preferences);
  }
}
