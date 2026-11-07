import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ImportedTransaction, ImportResponse } from './import.models';

/** Acesso HTTP à importação de extrato (módulo integrations, ADR 0014). */
@Injectable({ providedIn: 'root' })
export class ImportsApi {
  private readonly http = inject(HttpClient);

  /** Envia o arquivo OFX ou CSV. Reenviar o mesmo arquivo não duplica nada. */
  importStatement(file: File): Observable<ImportResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<ImportResponse>('/api/v1/imports', form);
  }

  /** As transações importadas dos últimos 60 dias, da mais nova à mais antiga. */
  transactions(): Observable<ImportedTransaction[]> {
    return this.http.get<ImportedTransaction[]>('/api/v1/imports/transactions');
  }

  /** "Não contar" ({@code true}) ou "Contar de novo" ({@code false}). */
  setIgnored(id: string, ignored: boolean): Observable<ImportedTransaction> {
    return this.http.put<ImportedTransaction>(`/api/v1/imports/transactions/${encodeURIComponent(id)}`, { ignored });
  }
}
