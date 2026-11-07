import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { WeeksApi } from './weeks-api';

describe('WeeksApi', () => {
  let api: WeeksApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(WeeksApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pede a semana atual com o tom', async () => {
    const result = firstValueFrom(api.summary(null, 'gentle'));

    const req = http.expectOne((r) => r.url === '/api/v1/weeks/current/summary');
    expect(req.request.params.get('tone')).toBe('gentle');
    req.flush({ weekStart: '2026-11-02' });

    expect(await result).toEqual({ weekStart: '2026-11-02' });
  });

  it('pede uma semana pela segunda-feira', async () => {
    const result = firstValueFrom(api.summary('2026-10-26', 'scold'));

    http.expectOne((r) => r.url === '/api/v1/weeks/2026-10-26/summary').flush({ weekStart: '2026-10-26' });

    expect((await result).weekStart).toBe('2026-10-26');
  });
});
