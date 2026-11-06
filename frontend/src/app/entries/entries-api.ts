import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateEntryRequest, EntryResponse } from './entry.models';

/** Acesso HTTP à API de lançamentos. O caminho é relativo: proxy no ng serve, nginx no Docker. */
@Injectable({ providedIn: 'root' })
export class EntriesApi {
  static readonly BASE_URL = '/api/v1/entries';

  private readonly http = inject(HttpClient);

  /** Cria o lançamento. Reenviar o mesmo id é seguro: a API devolve o registro existente. */
  create(request: CreateEntryRequest): Observable<EntryResponse> {
    return this.http.post<EntryResponse>(EntriesApi.BASE_URL, request);
  }

  /** Lançamentos de hoje (no fuso de São Paulo, definido pelo servidor), do mais recente ao mais antigo. */
  listToday(): Observable<EntryResponse[]> {
    return this.http.get<EntryResponse[]>(EntriesApi.BASE_URL);
  }
}
