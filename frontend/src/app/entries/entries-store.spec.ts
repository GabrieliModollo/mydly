import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Connectivity } from './connectivity';
import { applyResolution, EntriesStore, NEW_ENTRY_ID, NOW, RETRY_DELAY_MS } from './entries-store';
import { DaySummaryResponse, EntryResponse } from './entry.models';
import { OUTBOX_KEY, OUTBOX_STORAGE, OutboxEntry } from './outbox-storage';

/** Rede controlada pelo teste: {@code back()} faz o que o evento {@code online} do navegador faz. */
class FakeConnectivity {
  readonly online = signal(true);
  private listener: (() => void) | undefined;

  whenBackOnline(listener: () => void): () => void {
    this.listener = listener;
    return () => (this.listener = undefined);
  }

  back(): void {
    this.online.set(true);
    this.listener?.();
  }
}

/** Uma volta no laço de eventos, para as promessas do store andarem. */
const tick = () => new Promise((resolve) => setTimeout(resolve));

const RETRY_DELAY = 40;

const URL = '/api/v1/entries';
const NOW_ISO = '2026-11-05T12:00:00.000Z';
const SUMMARY_URL = '/api/v1/days/2026-11-05/summary';

function summary(spent: number, habitsAchieved = 0, items: DaySummaryResponse['items'] = []): DaySummaryResponse {
  return { date: '2026-11-05', spent, received: 0, habitsAchieved, slips: 0, spentByCategory: [], items, dailyGoals: [], budgetAlerts: [] };
}

function saved(id: string, text: string, occurredAt = NOW_ISO): EntryResponse {
  return {
    id,
    text,
    occurredAt,
    status: 'CONFIRMED',
    needsConfirmation: false,
    items: [
      {
        type: 'FINANCE',
        direction: 'EXPENSE',
        outcome: null,
        category: null,
        amount: 50,
        quantity: null,
        unit: null,
        description: text,
        confidence: 0.95,
        needsConfirmation: false,
        resolvedByUser: false,
        dismissed: false,
        state: 'ACCEPTED_AUTO',
        revision: 1,
        canReopen: true,
      },
    ],
    version: 0,
  };
}

/** "Comprei pão": gasto sem valor, esperando o usuário. */
function pending(id: string, version = 0): EntryResponse {
  const base = saved(id, 'Comprei pão');
  return {
    ...base,
    status: 'NEEDS_INPUT',
    needsConfirmation: true,
    version,
    items: [
      {
        ...base.items[0],
        amount: null,
        category: 'FOOD',
        confidence: 0.4,
        needsConfirmation: true,
        state: 'PENDING',
        canReopen: false,
      },
    ],
  };
}

function confirmed(id: string, version: number): EntryResponse {
  const base = pending(id, version);
  return {
    ...base,
    status: 'CONFIRMED',
    needsConfirmation: false,
    items: [
      {
        ...base.items[0],
        amount: 8.5,
        confidence: 1,
        needsConfirmation: false,
        resolvedByUser: true,
        state: 'ACCEPTED_BY_USER',
        revision: 2,
        canReopen: true,
      },
    ],
  };
}

describe('EntriesStore', () => {
  let store: EntriesStore;
  let http: HttpTestingController;
  let nextId: number;
  let now: string;
  let network: FakeConnectivity;

  beforeEach(() => {
    nextId = 0;
    now = NOW_ISO;
    network = new FakeConnectivity();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NEW_ENTRY_ID, useValue: () => `id-${++nextId}` },
        { provide: NOW, useValue: () => new Date(now) },
        { provide: Connectivity, useValue: network },
        { provide: OUTBOX_STORAGE, useValue: localStorage },
        { provide: RETRY_DELAY_MS, useValue: RETRY_DELAY },
      ],
    });
    store = TestBed.inject(EntriesStore);
    http = TestBed.inject(HttpTestingController);
  });

  /** Responde os pedidos de resumo que ficaram abertos; cada teste que se importa com eles responde antes. */
  afterEach(() => {
    http.match(SUMMARY_URL).forEach((req) => req.flush(summary(0)));
    http.verify();
    localStorage.clear();
  });

  /** O que está guardado no aparelho. */
  function outbox(): OutboxEntry[] {
    return JSON.parse(localStorage.getItem(OUTBOX_KEY) ?? '[]') as OutboxEntry[];
  }

  /** Um store novo, como se o app fosse aberto de novo com o que está no aparelho. */
  function reopenApp(entries: OutboxEntry[]): EntriesStore {
    localStorage.setItem(OUTBOX_KEY, JSON.stringify(entries));
    return TestBed.runInInjectionContext(() => new EntriesStore());
  }

  /** Responde o próximo envio da fila, conferindo qual lançamento foi. */
  async function answerPost(id: string, text = '50 reais'): Promise<void> {
    const req = http.expectOne((candidate) => candidate.method === 'POST');
    expect(req.request.body.id).toBe(id);
    req.flush(saved(id, text, req.request.body.occurredAt));
    await tick();
  }

  /** Espera os pedidos de resumo que o store dispara depois de cada mudança, e responde todos. */
  async function answerSummary(response: DaySummaryResponse): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    const requests = http.match(SUMMARY_URL);
    expect(requests.length).toBeGreaterThan(0);
    requests.forEach((req) => req.flush(response));
    await new Promise((resolve) => setTimeout(resolve));
  }

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

  it('em falha de rede guarda no aparelho e "Enviar agora" reenvia com o MESMO id e data', async () => {
    const first = store.submit('50 reais');
    expect(outbox()).toEqual([{ id: 'id-1', text: '50 reais', occurredAt: NOW_ISO, rejected: false }]);
    http.expectOne(URL).error(new ProgressEvent('error'));
    await first;
    expect(store.timeline()[0].sync).toBe('queued');
    expect(store.waitingCount()).toBe(1);

    const retry = store.retry('id-1');
    expect(store.timeline()[0].sync).toBe('sending');
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ id: 'id-1', text: '50 reais', occurredAt: NOW_ISO });
    req.flush(saved('id-1', '50 reais'));
    await retry;

    expect(store.timeline()).toHaveLength(1);
    expect(store.timeline()[0].sync).toBe('synced');
    expect(store.waitingCount()).toBe(0);
    expect(localStorage.getItem(OUTBOX_KEY)).toBeNull();
  });

  it('erro do servidor (5xx) também fica guardado para reenviar', async () => {
    const sending = store.submit('50 reais');
    http.expectOne(URL).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await sending;

    expect(store.timeline()[0].sync).toBe('queued');
  });

  it('recusa da API (4xx) não oferece reenvio, fica no aparelho até ser removida', async () => {
    const sending = store.submit('50 reais');
    http.expectOne(URL).flush(null, { status: 409, statusText: 'Conflict' });
    await sending;
    expect(store.timeline()[0].sync).toBe('rejected');
    expect(outbox()).toEqual([{ id: 'id-1', text: '50 reais', occurredAt: NOW_ISO, rejected: true }]);

    await store.retry('id-1');
    http.expectNone(URL);

    store.dismiss('id-1');
    expect(store.timeline()).toEqual([]);
    expect(localStorage.getItem(OUTBOX_KEY)).toBeNull();
  });

  it('sem rede, o lançamento vai direto para a fila, sem tentar enviar', async () => {
    network.online.set(false);

    await store.submit('50 reais');

    http.expectNone(URL);
    expect(store.timeline()[0].sync).toBe('queued');
    expect(outbox().map((entry) => entry.id)).toEqual(['id-1']);
  });

  it('quando a rede volta, envia a fila um por vez, do mais antigo ao mais novo', async () => {
    network.online.set(false);
    now = '2026-11-05T12:05:00.000Z';
    await store.submit('Bebi 2 litros de água');
    now = '2026-11-05T12:00:00.000Z';
    await store.submit('50 reais');

    network.back();

    // Só um envio por vez: o segundo espera a resposta do primeiro.
    await answerPost('id-2');
    expect(store.timeline().find((entry) => entry.id === 'id-1')?.sync).toBe('sending');
    await answerPost('id-1', 'Bebi 2 litros de água');

    expect(store.timeline().map((entry) => entry.sync)).toEqual(['synced', 'synced']);
    expect(localStorage.getItem(OUTBOX_KEY)).toBeNull();
  });

  it('falha no meio da fila para de enviar e tenta de novo depois de um tempo', async () => {
    network.online.set(false);
    await store.submit('50 reais');
    now = '2026-11-05T12:05:00.000Z';
    await store.submit('Bebi 2 litros de água');

    network.back();
    http.expectOne(URL).error(new ProgressEvent('error'));
    await tick();
    http.expectNone(URL);
    expect(store.waitingCount()).toBe(2);

    await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY + 20));
    await answerPost('id-1');
    await answerPost('id-2', 'Bebi 2 litros de água');
    expect(store.waitingCount()).toBe(0);
  });

  it('recusa no meio da fila não segura os outros', async () => {
    network.online.set(false);
    await store.submit('50 reais');
    now = '2026-11-05T12:05:00.000Z';
    await store.submit('Bebi 2 litros de água');

    network.back();
    http.expectOne(URL).flush(null, { status: 409, statusText: 'Conflict' });
    await tick();
    await answerPost('id-2', 'Bebi 2 litros de água');

    expect(store.timeline().map((entry) => [entry.id, entry.sync])).toEqual([
      ['id-2', 'synced'],
      ['id-1', 'rejected'],
    ]);
  });

  it('ao abrir o app, o que estava guardado volta, e um envio interrompido volta como guardado', async () => {
    const reopened = reopenApp([
      { id: 'a', text: '50 reais', occurredAt: '2026-11-05T11:00:00.000Z', rejected: false },
      { id: 'b', text: 'xyz', occurredAt: '2026-11-05T10:00:00.000Z', rejected: true },
    ]);

    expect(reopened.timeline().map((entry) => [entry.id, entry.sync])).toEqual([
      ['a', 'queued'],
      ['b', 'rejected'],
    ]);

    const loading = reopened.load();
    http.expectOne((req) => req.method === 'GET').flush([]);
    await loading;
    await answerPost('a');

    expect(reopened.timeline().map((entry) => [entry.id, entry.sync])).toEqual([
      ['a', 'synced'],
      ['b', 'rejected'],
    ]);
    expect(outbox().map((entry) => entry.id)).toEqual(['b']);
  });

  it('um lançamento guardado que o servidor já tem (resposta perdida) sai da fila sem reenviar', async () => {
    const reopened = reopenApp([{ id: 'a', text: '50 reais', occurredAt: NOW_ISO, rejected: false }]);

    const loading = reopened.load();
    http.expectOne((req) => req.method === 'GET').flush([saved('a', '50 reais')]);
    await loading;
    await tick();

    http.expectNone((req) => req.method === 'POST');
    expect(reopened.timeline()[0].sync).toBe('synced');
    expect(localStorage.getItem(OUTBOX_KEY)).toBeNull();
  });

  it('lançamento de ontem aparece enquanto está guardado e sai da lista de hoje depois de enviado', async () => {
    // 20h UTC do dia 4 = 17h do dia 4 em São Paulo; hoje é dia 5.
    const yesterday = '2026-11-04T20:00:00.000Z';
    const reopened = reopenApp([{ id: 'a', text: 'Dormi mal', occurredAt: yesterday, rejected: false }]);
    expect(reopened.timeline().map((entry) => entry.id)).toEqual(['a']);

    const loading = reopened.load();
    http.expectOne((req) => req.method === 'GET').flush([]);
    await loading;
    const req = http.expectOne((candidate) => candidate.method === 'POST');
    expect(req.request.body.occurredAt).toBe(yesterday);
    req.flush(saved('a', 'Dormi mal', yesterday));
    await tick();

    expect(reopened.timeline()).toEqual([]);
    expect(reopened.waitingCount()).toBe(0);
  });

  it('não apaga o que outra aba guardou e mostra o lançamento dela', async () => {
    network.online.set(false);
    await store.submit('50 reais');
    const otherTab: OutboxEntry = { id: 'other', text: 'Dormi bem', occurredAt: NOW_ISO, rejected: false };
    localStorage.setItem(OUTBOX_KEY, JSON.stringify([...outbox(), otherTab]));

    await store.submit('Bebi 2 litros de água');
    expect(outbox().map((entry) => entry.id).sort()).toEqual(['id-1', 'id-2', 'other']);

    window.dispatchEvent(new StorageEvent('storage', { key: OUTBOX_KEY }));
    expect(store.timeline().map((entry) => entry.id)).toContain('other');
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
    // Depois de carregar, a fila tenta de novo; a rede continua fora.
    http.expectOne((req) => req.method === 'POST').error(new ProgressEvent('error'));
    await tick();

    expect(store.timeline().map((entry) => [entry.id, entry.sync])).toEqual([
      ['id-1', 'queued'],
      ['x', 'synced'],
    ]);
  });

  it('os cartões vêm do resumo da API do dia em São Paulo e são recarregados depois de cada lançamento', async () => {
    expect(store.summary()).toEqual({ spent: 0, habitsDone: 0 });
    const loading = store.load();
    http.expectOne(URL).flush([saved('a', 'Gastei 50 reais')]);
    await loading;
    await answerSummary(summary(50, 2));
    expect(store.summary()).toEqual({ spent: 50, habitsDone: 2 });

    const sending = store.submit('Gastei 30 reais');
    http.expectOne(URL).flush(saved('id-1', 'Gastei 30 reais'));
    await sending;
    await answerSummary(summary(80, 2));

    expect(store.summary()).toEqual({ spent: 80, habitsDone: 2 });
  });

  it('falha no resumo mantém os cartões como estavam', async () => {
    const loading = store.load();
    http.expectOne(URL).flush([]);
    await loading;
    await answerSummary(summary(50));

    void store.refreshSummary();
    http.expectOne(SUMMARY_URL).error(new ProgressEvent('error'));
    await new Promise((resolve) => setTimeout(resolve));

    expect(store.summary().spent).toBe(50);
  });

  it('resposta de resumo antiga não sobrescreve uma mais nova', async () => {
    const first = store.refreshSummary();
    const second = store.refreshSummary();
    const [old, fresh] = http.match(SUMMARY_URL);
    fresh.flush(summary(80));
    old.flush(summary(50));
    await Promise.all([first, second]);

    expect(store.summary().spent).toBe(80);
  });

  it('diz o hábito de cada item de rotina', async () => {
    const loading = store.load();
    http.expectOne(URL).flush([]);
    await loading;
    const habit = { entryId: 'a', itemIndex: 1, habit: 'HYDRATION', habitName: 'Hidratação', polarity: 'GOOD', status: 'ACHIEVED' } as const;
    await answerSummary(summary(0, 1, [habit]));

    expect(store.habitFor('a', 1)).toEqual(habit);
    expect(store.habitFor('a', 0)).toBeUndefined();
  });

  describe('resolução de itens', () => {
    const RESOLVE_URL = '/api/v1/entries/p/items/0/resolution';
    const CONFIRM = { action: 'CONFIRM_FINANCE', amount: 8.5, direction: 'EXPENSE', category: 'FOOD' } as const;

    async function loadPending(): Promise<void> {
      const loading = store.load();
      http.expectOne(URL).flush([pending('p')]);
      await loading;
    }

    it('mostra a confirmação na hora e fica com a resposta da API', async () => {
      await loadPending();

      const resolving = store.resolve('p', 0, CONFIRM);
      const optimistic = store.find('p');
      expect(optimistic?.status).toBe('CONFIRMED');
      expect(optimistic?.items[0]).toMatchObject({
        amount: 8.5,
        needsConfirmation: false,
        resolvedByUser: true,
        state: 'ACCEPTED_BY_USER',
        canReopen: true,
      });

      const req = http.expectOne(RESOLVE_URL);
      expect(req.request.headers.get('If-Match')).toBe('"0"');
      req.flush(confirmed('p', 1));

      expect(await resolving).toBe('ok');
      expect(store.find('p')?.version).toBe(1);
      await answerSummary(summary(8.5));
      expect(store.summary().spent).toBe(8.5);
    });

    it('"Não foi isso" volta o item a pendente na hora e recarrega o resumo', async () => {
      const loading = store.load();
      http.expectOne(URL).flush([saved('s', 'Gastei 50 reais na farmácia')]);
      await loading;
      await answerSummary(summary(50));

      const reopening = store.resolve('s', 0, { action: 'REOPEN' });
      expect(store.find('s')?.status).toBe('NEEDS_INPUT');
      expect(store.find('s')?.items[0]).toMatchObject({
        needsConfirmation: true,
        state: 'PENDING',
        revision: 2,
        canReopen: false,
      });
      const req = http.expectOne('/api/v1/entries/s/items/0/resolution');
      expect(req.request.body).toEqual({ action: 'REOPEN' });
      req.flush(pending('s', 1));
      expect(await reopening).toBe('ok');

      await answerSummary(summary(0));
      expect(store.summary().spent).toBe(0);
    });

    it('lançamento desatualizado (412) é recarregado e o resultado avisa a tela', async () => {
      await loadPending();

      const resolving = store.resolve('p', 0, CONFIRM);
      http.expectOne(RESOLVE_URL).flush(null, { status: 412, statusText: 'Precondition Failed' });
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v1/entries/p').flush(pending('p', 4));

      expect(await resolving).toBe('stale');
      expect(store.find('p')).toEqual(pending('p', 4));
    });

    it('se nem o recarregamento funciona, volta ao estado anterior', async () => {
      await loadPending();

      const resolving = store.resolve('p', 0, { action: 'DISMISS' });
      http.expectOne(RESOLVE_URL).flush(null, { status: 409, statusText: 'Conflict' });
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v1/entries/p').error(new ProgressEvent('error'));

      expect(await resolving).toBe('stale');
      expect(store.find('p')).toEqual(pending('p'));
    });

    it('em outro erro, desfaz a mudança', async () => {
      await loadPending();

      const resolving = store.resolve('p', 0, { action: 'DISMISS' });
      expect(store.find('p')?.items[0].dismissed).toBe(true);
      http.expectOne(RESOLVE_URL).error(new ProgressEvent('error'));

      expect(await resolving).toBe('error');
      expect(store.find('p')).toEqual(pending('p'));
    });

    it('lançamento desconhecido não é enviado', async () => {
      expect(await store.resolve('inexistente', 0, { action: 'DISMISS' })).toBe('error');
      http.expectNone(() => true);
    });
  });

  describe('applyResolution', () => {
    it('confirmar como rotina troca o tipo e marca como cumprida', () => {
      const result = applyResolution(pending('p'), 0, { action: 'CONFIRM_ROUTINE' });

      expect(result.items[0]).toMatchObject({
        type: 'ROUTINE',
        outcome: 'DONE',
        direction: null,
        category: null,
        amount: null,
        resolvedByUser: true,
      });
    });

    it('só muda o item indicado e mantém o status enquanto houver pendência', () => {
      const two: EntryResponse = { ...pending('p'), items: [...pending('p').items, ...pending('p').items] };

      const result = applyResolution(two, 1, { action: 'DISMISS' });

      expect(result.items[0].needsConfirmation).toBe(true);
      expect(result.items[1].dismissed).toBe(true);
      expect(result.status).toBe('NEEDS_INPUT');
      expect(result.needsConfirmation).toBe(true);
    });
  });

  it('marca erro quando não consegue carregar', async () => {
    const loading = store.load();
    http.expectOne(URL).error(new ProgressEvent('error'));
    await loading;

    expect(store.loadState()).toBe('error');
  });
});
