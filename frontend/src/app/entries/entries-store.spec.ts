import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EntriesStore, NEW_ENTRY_ID, NOW } from './entries-store';
import { EntryResponse } from './entry.models';

const URL = '/api/v1/entries';
const NOW_ISO = '2026-11-05T12:00:00.000Z';

function saved(id: string, text: string, occurredAt = NOW_ISO): EntryResponse {
  return {
    id,
    text,
    occurredAt,
    status: 'PENDING',
    needsConfirmation: false,
    items: [
      {
        type: 'FINANCE',
        direction: 'EXPENSE',
        outcome: null,
        amount: 50,
        quantity: null,
        unit: null,
        description: text,
        confidence: 0.95,
      },
    ],
  };
}

describe('EntriesStore', () => {
  let store: EntriesStore;
  let http: HttpTestingController;
  let nextId: number;

  beforeEach(() => {
    nextId = 0;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NEW_ENTRY_ID, useValue: () => `id-${++nextId}` },
        { provide: NOW, useValue: () => new Date(NOW_ISO) },
      ],
    });
    store = TestBed.inject(EntriesStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('mostra o lançamento na hora, como enviando, e troca pela resposta da API', async () => {
    const sending = store.submit('  50 reais  ');

    expect(store.timeline()).toEqual([{ id: 'id-1', text: '50 reais', occurredAt: NOW_ISO, sync: 'sending' }]);
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ id: 'id-1', text: '50 reais', occurredAt: NOW_ISO });

    req.flush(saved('id-1', '50 reais'));
    await sending;

    expect(store.timeline()[0].sync).toBe('synced');
    expect(store.timeline()[0].entry?.items[0].amount).toBe(50);
  });

  it('ignora texto vazio', async () => {
    await store.submit('   ');

    expect(store.timeline()).toEqual([]);
    http.expectNone(URL);
  });

  it('em falha de rede marca para tentar de novo e reenvia com o MESMO id e data', async () => {
    const first = store.submit('50 reais');
    http.expectOne(URL).error(new ProgressEvent('error'));
    await first;
    expect(store.timeline()[0].sync).toBe('failed');

    const retry = store.retry('id-1');
    expect(store.timeline()[0].sync).toBe('sending');
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ id: 'id-1', text: '50 reais', occurredAt: NOW_ISO });
    req.flush(saved('id-1', '50 reais'));
    await retry;

    expect(store.timeline()).toHaveLength(1);
    expect(store.timeline()[0].sync).toBe('synced');
  });

  it('erro do servidor (5xx) também pode ser reenviado', async () => {
    const sending = store.submit('50 reais');
    http.expectOne(URL).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await sending;

    expect(store.timeline()[0].sync).toBe('failed');
  });

  it('recusa da API (4xx) não oferece reenvio e pode ser removida', async () => {
    const sending = store.submit('50 reais');
    http.expectOne(URL).flush(null, { status: 409, statusText: 'Conflict' });
    await sending;
    expect(store.timeline()[0].sync).toBe('rejected');

    await store.retry('id-1');
    http.expectNone(URL);

    store.dismiss('id-1');
    expect(store.timeline()).toEqual([]);
  });

  it('carrega os lançamentos do dia do mais recente para o mais antigo', async () => {
    const loading = store.load();
    expect(store.loadState()).toBe('loading');

    http.expectOne(URL).flush([
      saved('a', 'Dormi bem', '2026-11-05T08:00:00.000000Z'),
      saved('b', 'Bebi 2 litros de água', '2026-11-05T15:30:00.123456Z'),
    ]);
    await loading;

    expect(store.loadState()).toBe('loaded');
    expect(store.timeline().map((entry) => entry.id)).toEqual(['b', 'a']);
  });

  it('ao recarregar mantém o que ainda não chegou ao servidor', async () => {
    const sending = store.submit('50 reais');
    http.expectOne((req) => req.method === 'POST').error(new ProgressEvent('error'));
    await sending;

    const loading = store.load();
    http.expectOne((req) => req.method === 'GET').flush([saved('x', 'Dormi bem', '2026-11-05T08:00:00Z')]);
    await loading;

    expect(store.timeline().map((entry) => [entry.id, entry.sync])).toEqual([
      ['id-1', 'failed'],
      ['x', 'synced'],
    ]);
  });

  it('o resumo do dia considera só o que a API já interpretou', async () => {
    const loading = store.load();
    http.expectOne(URL).flush([saved('a', 'Gastei 50 reais')]);
    await loading;

    const sending = store.submit('Gastei 30 reais');
    expect(store.summary()).toEqual({ spent: 50, habitsDone: 0 });

    http.expectOne(URL).flush(saved('id-1', 'Gastei 30 reais'));
    await sending;
    expect(store.summary()).toEqual({ spent: 100, habitsDone: 0 });
  });

  it('marca erro quando não consegue carregar', async () => {
    const loading = store.load();
    http.expectOne(URL).error(new ProgressEvent('error'));
    await loading;

    expect(store.loadState()).toBe('error');
  });
});
