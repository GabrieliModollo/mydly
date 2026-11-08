import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, apiBaseInterceptor } from './api-base.interceptor';

describe('apiBaseInterceptor', () => {
  function setup(base: string): { http: HttpClient; backend: HttpTestingController } {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiBaseInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: base },
      ],
    });
    return { http: TestBed.inject(HttpClient), backend: TestBed.inject(HttpTestingController) };
  }

  it('na web a API fica na mesma origem', () => {
    const { http, backend } = setup('');
    void firstValueFrom(http.get('/api/v1/entries'));

    backend.expectOne('/api/v1/entries');
    backend.verify();
  });

  it('no app nativo o endereço da API vai na frente', () => {
    const { http, backend } = setup('http://10.0.2.2:8080');
    void firstValueFrom(http.get('/api/v1/entries'));
    void firstValueFrom(http.get('/assets/x.json'));

    backend.expectOne('http://10.0.2.2:8080/api/v1/entries');
    backend.expectOne('/assets/x.json');
    backend.verify();
  });
});
