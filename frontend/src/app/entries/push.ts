import { inject, Injectable, InjectionToken, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Capacitor } from '@capacitor/core';
import { PushNotifications } from '@capacitor/push-notifications';
import { firstValueFrom } from 'rxjs';
import { DevicePlatform, NotificationsApi } from './notifications-api';

const KEY = 'mydly.push.v1';

/** O pedaço do plugin de push que o app usa; substituível nos testes. */
export type PushPlugin = Pick<
  typeof PushNotifications,
  'checkPermissions' | 'requestPermissions' | 'register' | 'unregister' | 'addListener' | 'removeAllListeners'
>;

export const PUSH_PLUGIN = new InjectionToken<PushPlugin>('PUSH_PLUGIN', {
  providedIn: 'root',
  factory: () => PushNotifications,
});

/** {@code android}, {@code ios} ou {@code web}. Substituível nos testes. */
export const PLATFORM = new InjectionToken<string>('PLATFORM', {
  providedIn: 'root',
  factory: () => Capacitor.getPlatform(),
});

/** Onde o app guarda o token deste aparelho, para tirá-lo do servidor ao sair. */
export const PUSH_STORAGE = new InjectionToken<Storage | undefined>('PUSH_STORAGE', {
  providedIn: 'root',
  factory: () => {
    try {
      return globalThis.localStorage;
    } catch {
      return undefined;
    }
  },
});

/** Como terminou o pedido para ativar. */
export type EnableResult = 'enabled' | 'denied' | 'unavailable' | 'error';

/**
 * Notificações push no app nativo (ADR 0016), pelo plugin oficial do Capacitor. Na web nada disso
 * existe: {@link available} é falso e os métodos não fazem nada.
 *
 * Ativar pede a permissão do sistema, registra o aparelho no Firebase e manda o token para a API. O
 * token fica no aparelho para ser tirado do servidor ao sair da conta, e o aparelho é registrado de
 * novo a cada abertura do app, porque o Firebase pode trocar o token.
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly plugin = inject(PUSH_PLUGIN);
  private readonly platform = inject(PLATFORM);
  private readonly storage = inject(PUSH_STORAGE);
  private readonly api = inject(NotificationsApi);
  private readonly router = inject(Router);
  private listening = false;

  /** O aparelho recebe notificações deste usuário. */
  readonly enabled = signal(this.storedToken() !== null);

  /** Só o app Android e iOS recebe push. */
  get available(): boolean {
    return this.platform === 'android' || this.platform === 'ios';
  }

  async enable(): Promise<EnableResult> {
    if (!this.available) {
      return 'unavailable';
    }
    try {
      let permission = await this.plugin.checkPermissions();
      if (permission.receive === 'prompt' || permission.receive === 'prompt-with-rationale') {
        permission = await this.plugin.requestPermissions();
      }
      if (permission.receive !== 'granted') {
        return 'denied';
      }
      const token = await this.register();
      await firstValueFrom(this.api.registerDevice(token, this.devicePlatform()));
      this.store(token);
      this.enabled.set(true);
      return 'enabled';
    } catch {
      return 'error';
    }
  }

  /** Para de receber: tira o aparelho do servidor (enquanto ainda há sessão) e do Firebase. */
  async disable(): Promise<void> {
    const token = this.storedToken();
    this.store(null);
    this.enabled.set(false);
    if (!this.available) {
      return;
    }
    try {
      if (token) {
        await firstValueFrom(this.api.unregisterDevice(token));
      }
    } catch {
      // Sem rede: o servidor tira o token quando o Firebase disser que ele não vale mais.
    }
    try {
      await this.plugin.unregister();
      await this.plugin.removeAllListeners();
      this.listening = false;
    } catch {
      // O plugin já estava sem registro.
    }
  }

  /** Ao abrir o app logado: registra de novo, se a pessoa já tinha ativado, porque o token pode mudar. */
  async resume(): Promise<void> {
    if (!this.available || this.storedToken() === null) {
      return;
    }
    try {
      const permission = await this.plugin.checkPermissions();
      if (permission.receive !== 'granted') {
        this.store(null);
        this.enabled.set(false);
        return;
      }
      const token = await this.register();
      await firstValueFrom(this.api.registerDevice(token, this.devicePlatform()));
      this.store(token);
    } catch {
      // Fica para a próxima abertura.
    }
  }

  /** Registra no Firebase e espera o token, uma vez. */
  private async register(): Promise<string> {
    await this.listenToTaps();
    return new Promise<string>((resolve, reject) => {
      const handles: { remove: () => Promise<void> }[] = [];
      const done = () => handles.forEach((handle) => void handle.remove());
      void Promise.all([
        this.plugin.addListener('registration', (token) => {
          done();
          resolve(token.value);
        }),
        this.plugin.addListener('registrationError', (error) => {
          done();
          reject(new Error(error.error));
        }),
      ]).then((added) => {
        handles.push(...added);
        return this.plugin.register();
      });
    });
  }

  /** Tocar na notificação abre a tela dela ("/" ou "/resumo"). */
  private async listenToTaps(): Promise<void> {
    if (this.listening) {
      return;
    }
    this.listening = true;
    await this.plugin.addListener('pushNotificationActionPerformed', (action) => {
      const route = (action.notification.data as Record<string, unknown> | undefined)?.['route'];
      if (route === '/' || route === '/resumo') {
        void this.router.navigateByUrl(route);
      }
    });
  }

  private devicePlatform(): DevicePlatform {
    return this.platform === 'ios' ? 'IOS' : 'ANDROID';
  }

  private storedToken(): string | null {
    try {
      return this.storage?.getItem(KEY) ?? null;
    } catch {
      return null;
    }
  }

  private store(token: string | null): void {
    try {
      if (token) {
        this.storage?.setItem(KEY, token);
      } else {
        this.storage?.removeItem(KEY);
      }
    } catch {
      // Sem armazenamento: o aparelho é registrado de novo ao ativar.
    }
  }
}
