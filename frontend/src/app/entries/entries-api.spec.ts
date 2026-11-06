import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { EntriesApi } from './entries-api';
import { EntryResponse } from './entry.models';

const RESPONSE: EntryResponse = {
  id: '3f1c2a9e-8b7d-4c6a-9e2f-1a2b3c4d5e6f',
  text: '50 reais',
  occurredAt: '2026-11-05T12:00:00Z',
  status: 'PENDING',
  needsConfirmation: false,
  items: [],
};

describe('EntriesApi', () => {
  let api: EntriesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(EntriesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cria o lançamento com POST enviando id, texto e data', async () => {
    const request = { id: RESPONSE.id, text: '50 reais', occurredAt: '2026-11-05T12:00:00Z' };
    const result = firstValueFrom(api.create(request));

    const req = http.expectOne('/api/v1/entries');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(RESPONSE);

    expect(await result).toEqual(RESPONSE);
  });

  it('lista os lançamentos de hoje sem informar data', async () => {
    const result = firstValueFrom(api.listToday());

    const req = http.expectOne('/api/v1/entries');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([RESPONSE]);

    expect(await result).toEqual([RESPONSE]);
  });
});
