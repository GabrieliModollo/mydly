import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LoginResponse } from './auth.models';

/** Login e cadastro na API. São os únicos pedidos que vão sem token. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  static readonly LOGIN_URL = '/api/v1/auth/login';
  static readonly REGISTER_URL = '/api/v1/auth/register';

  private readonly http = inject(HttpClient);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(AuthApi.LOGIN_URL, { username, password });
  }

  /** Cria a conta; a resposta é a mesma do login, e a pessoa já entra (ADR 0018). */
  register(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(AuthApi.REGISTER_URL, { username, password });
  }
}
