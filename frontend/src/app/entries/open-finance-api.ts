import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { OpenFinanceStatus, OpenFinanceSync } from './open-finance.models';

/** Acesso HTTP ao Open Finance (Pluggy, só sandbox, ADR 0015). As chaves do Pluggy ficam no servidor. */
@Injectable({ providedIn: 'root' })
export class OpenFinanceApi {
  private readonly http = inject(HttpClient);

  status(): Observable<OpenFinanceStatus> {
    return this.http.get<OpenFinanceStatus>('/api/v1/open-finance');
  }

  /** O token de 30 minutos que abre o widget do Pluggy. */
  connectToken(): Observable<string> {
    return this.http
      .post<{ connectToken: string }>('/api/v1/open-finance/connect-token', {})
      .pipe(map((response) => response.connectToken));
  }

  /** Liga o item que o widget criou e importa as transações. */
  connect(itemId: string): Observable<OpenFinanceSync> {
    return this.http.post<OpenFinanceSync>('/api/v1/open-finance/connections', { itemId });
  }

  sync(id: string): Observable<OpenFinanceSync> {
    return this.http.post<OpenFinanceSync>(`/api/v1/open-finance/connections/${encodeURIComponent(id)}/sync`, {});
  }

  disconnect(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/open-finance/connections/${encodeURIComponent(id)}`);
  }
}
