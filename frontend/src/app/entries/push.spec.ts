import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { NotificationsApi } from './notifications-api';
import { PLATFORM, PUSH_PLUGIN, PUSH_STORAGE, PushPlugin, PushService } from './push';

type Listener = (payload: unknown) => void;

/** Plugin de push de mentira: guarda os ouvintes e deixa o teste disparar os eventos. */
class FakePlugin {
  permission = 'granted';
  afterRequest = 'granted';
  listeners = new Map<string, Listener[]>();
  registered = 0;
  unregistered = 0;
  token: string | null = 'token-1';

  async checkPermissions() {
    return { receive: this.permission };
  }

  async requestPermissions() {
    this.permission = this.afterRequest;
    return { receive: this.permission };
  }

  async register() {
    this.registered++;
    const name = this.token ? 'registration' : 'registrationError';
    const payload = this.token ? { value: this.token } : { error: 'sem Firebase' };
    queueMicrotask(() => this.emit(name, payload));
  }

  async unregister() {
    this.unregistered++;
  }

  async addListener(name: string, listener: Listener) {
    this.listeners.set(name, [...(this.listeners.get(name) ?? []), listener]);
    return {
      remove: async () => {
        this.listeners.set(name, (this.listeners.get(name) ?? []).filter((item) => item !== listener));
      },
    };
  }

  async removeAllListeners() {
    this.listeners.clear();
  }

  emit(name: string, payload: unknown) {
    (this.listeners.get(name) ?? []).forEach((listener) => listener(payload));
  }
}

describe('PushService', () => {
  let plugin: FakePlugin;
  let registered: [string, string][];
  let removed: string[];
  let registerAnswer: () => Observable<void>;

  function create(platform: string): PushService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PUSH_PLUGIN, useValue: plugin as unknown as PushPlugin },
        { provide: PLATFORM, useValue: platform },
        { provide: PUSH_STORAGE, useValue: localStorage },
        {
          provide: NotificationsApi,
          useValue: {
            registerDevice: (token: string, devicePlatform: string) => {
              registered.push([token, devicePlatform]);
              return registerAnswer();
            },
            unregisterDevice: (token: string) => {
              removed.push(token);
              return of(undefined);
            },
          },
        },
      ],
    });
    return TestBed.inject(PushService);
  }

  beforeEach(() => {
    localStorage.clear();
    plugin = new FakePlugin();
    registered = [];
    removed = [];
    registerAnswer = () => of(undefined);
  });
  afterEach(() => localStorage.clear());

  it('na web não existe push', async () => {
    const push = create('web');

    expect(push.available).toBe(false);
    expect(await push.enable()).toBe('unavailable');
    expect(plugin.registered).toBe(0);
  });

  it('ativar pede a permissão, registra e manda o token para a API', async () => {
    plugin.permission = 'prompt';
    const push = create('android');

    expect(await push.enable()).toBe('enabled');

    expect(registered).toEqual([['token-1', 'ANDROID']]);
    expect(push.enabled()).toBe(true);
    expect(create('android').enabled()).toBe(true);
  });

  it('no iOS o aparelho vai como IOS', async () => {
    const push = create('ios');

    await push.enable();

    expect(registered).toEqual([['token-1', 'IOS']]);
  });

  it('permissão negada não registra nada', async () => {
    plugin.permission = 'prompt';
    plugin.afterRequest = 'denied';
    const push = create('android');

    expect(await push.enable()).toBe('denied');
    expect(plugin.registered).toBe(0);
    expect(push.enabled()).toBe(false);
  });

  it('falha no Firebase ou na API não deixa ativado pela metade', async () => {
    plugin.token = null;
    expect(await create('android').enable()).toBe('error');

    plugin.token = 'token-1';
    registerAnswer = () => throwError(() => new Error('sem rede'));
    const push = create('android');
    expect(await push.enable()).toBe('error');
    expect(push.enabled()).toBe(false);
  });

  it('desativar tira o aparelho do servidor e do Firebase', async () => {
    const push = create('android');
    await push.enable();

    await push.disable();

    expect(removed).toEqual(['token-1']);
    expect(plugin.unregistered).toBe(1);
    expect(push.enabled()).toBe(false);
    expect(create('android').enabled()).toBe(false);
  });

  it('ao abrir o app registra de novo com o token novo, se estava ativado', async () => {
    await create('android').enable();
    plugin.token = 'token-2';

    await create('android').resume();

    expect(registered).toEqual([
      ['token-1', 'ANDROID'],
      ['token-2', 'ANDROID'],
    ]);
  });

  it('se a permissão foi tirada no sistema, deixa de estar ativado', async () => {
    const push = create('android');
    await push.enable();
    plugin.permission = 'denied';

    await push.resume();

    expect(push.enabled()).toBe(false);
  });

  it('tocar na notificação abre a tela dela', async () => {
    const push = create('android');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await push.enable();

    plugin.emit('pushNotificationActionPerformed', { notification: { data: { route: '/resumo' } } });
    plugin.emit('pushNotificationActionPerformed', { notification: { data: { route: 'https://site.example' } } });

    expect(navigate.mock.calls).toEqual([['/resumo']]);
  });
});
