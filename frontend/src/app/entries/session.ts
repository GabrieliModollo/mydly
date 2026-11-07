import { computed, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { LoginResponse, SessionUser } from './auth.models';
import { NOW } from './now';

const KEY = 'mydly.session.v1';

/** Onde a sessão fica guardada. Substituível nos testes; sem armazenamento, vale só com o app aberto. */
export const SESSION_STORAGE = new InjectionToken<Storage | undefined>('SESSION_STORAGE', {
  providedIn: 'root',
  factory: () => {
    try {
      return globalThis.localStorage;
    } catch {
      return undefined;
    }
  },
});

interface StoredSession {
  token: string;
  expiresAt: string;
  user: SessionUser;
}

/**
 * Quem está logado neste aparelho e o token de acesso (ADR 0012).
 *
 * A sessão fica no armazenamento do aparelho para o app abrir logado e funcionar offline: sem rede, o
 * token guardado continua valendo até a data de validade, e a fila de lançamentos espera por ele. Um
 * token vencido ou recusado pela API (401) encerra a sessão e leva ao login.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly storage = inject(SESSION_STORAGE);
  private readonly now = inject(NOW);
  private readonly state = signal<StoredSession | null>(read(this.storage));

  /** O usuário da sessão, mesmo que o token já tenha vencido; {@code null} sem sessão. */
  readonly user = computed(() => this.state()?.user ?? null);

  /** O id de quem está logado; dono dos lançamentos guardados no aparelho. */
  readonly userId = computed(() => this.state()?.user.id ?? null);

  /** O token, se ainda vale. */
  token(): string | null {
    const session = this.state();
    if (!session || Date.parse(session.expiresAt) <= this.now().getTime()) {
      return null;
    }
    return session.token;
  }

  isLoggedIn(): boolean {
    return this.token() !== null;
  }

  /** Começa a sessão com a resposta do login. */
  start(response: LoginResponse): void {
    this.set({ token: response.accessToken, expiresAt: response.expiresAt, user: response.user });
  }

  /** Encerra a sessão: "Sair", token vencido ou recusado pela API. */
  end(): void {
    this.set(null);
  }

  private set(session: StoredSession | null): void {
    this.state.set(session);
    try {
      if (session) {
        this.storage?.setItem(KEY, JSON.stringify(session));
      } else {
        this.storage?.removeItem(KEY);
      }
    } catch {
      // Sem armazenamento: a sessão vale enquanto o app está aberto.
    }
  }
}

function read(storage: Storage | undefined): StoredSession | null {
  try {
    const raw = storage?.getItem(KEY);
    if (!raw) {
      return null;
    }
    const value = JSON.parse(raw) as Partial<StoredSession> | null;
    const valid =
      typeof value?.token === 'string' &&
      typeof value.expiresAt === 'string' &&
      !Number.isNaN(Date.parse(value.expiresAt)) &&
      typeof value.user?.id === 'string' &&
      typeof value.user.username === 'string';
    return valid ? (value as StoredSession) : null;
  } catch {
    return null;
  }
}
