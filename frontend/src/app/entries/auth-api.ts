import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LoginResponse } from './auth.models';

/** Login na API. É o único pedido que vai sem token. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  static readonly LOGIN_URL = '/api/v1/auth/login';

  private readonly http = inject(HttpClient);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(AuthApi.LOGIN_URL, { username, password });
  }
}
