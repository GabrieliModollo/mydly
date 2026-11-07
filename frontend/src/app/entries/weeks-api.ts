import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Tone, WeekSummaryResponse } from './week.models';

/** Acesso HTTP ao resumo da semana (módulo insights do backend). */
@Injectable({ providedIn: 'root' })
export class WeeksApi {
  private readonly http = inject(HttpClient);

  /**
   * Resumo da semana que começa em {@code start} ({@code AAAA-MM-DD}), ou da semana atual quando
   * {@code start} é {@code null}.
   */
  summary(start: string | null, tone: Tone): Observable<WeekSummaryResponse> {
    const path = start ?? 'current';
    return this.http.get<WeekSummaryResponse>(`/api/v1/weeks/${path}/summary`, {
      params: new HttpParams().set('tone', tone),
    });
  }
}
