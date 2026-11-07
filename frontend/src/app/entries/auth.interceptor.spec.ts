import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthSession } from './session';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let token: string | null;
  let ended: number;
  let navigations: string[];

  beforeEach(() => {
    token = 'token-1';
    ended = 0;
    navigations = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthSession, useValue: { token: () => token, end: () => ended++ } },
        { provide: Router, useValue: { navigateByUrl: async (url: string) => navigations.push(url) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('põe o token nos pedidos à API', () => {
    void firstValueFrom(http.get('/api/v1/entries'));

    expect(backend.expectOne('/api/v1/entries').request.headers.get('Authorization')).toBe('Bearer token-1');
  });

  it('não põe o token no login nem fora da API', () => {
    void firstValueFrom(http.post('/api/v1/auth/login', {}));
    void firstValueFrom(http.get('/assets/x.json'));

    expect(backend.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('/assets/x.json').request.headers.has('Authorization')).toBe(false);
  });

  it('sem token válido, o pedido vai sem o header', () => {
    token = null;
    void firstValueFrom(http.get('/api/v1/entries'));

    expect(backend.expectOne('/api/v1/entries').request.headers.has('Authorization')).toBe(false);
  });

  it('401 encerra a sessão, leva ao login e devolve o erro a quem pediu', async () => {
    const request = firstValueFrom(http.get('/api/v1/entries'));
    backend.expectOne('/api/v1/entries').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(request).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(ended).toBe(1);
    expect(navigations).toEqual(['/login']);
  });

  it('outros erros não mexem na sessão', async () => {
    const request = firstValueFrom(http.get('/api/v1/entries'));
    backend.expectOne('/api/v1/entries').flush(null, { status: 503, statusText: 'Service Unavailable' });

    await expect(request).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(ended).toBe(0);
    expect(navigations).toEqual([]);
  });
});
