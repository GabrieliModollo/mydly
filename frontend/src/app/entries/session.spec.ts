import { TestBed } from '@angular/core/testing';
import { LoginResponse } from './auth.models';
import { NOW } from './now';
import { AuthSession, SESSION_STORAGE } from './session';

const KEY = 'mydly.session.v1';

const LOGIN: LoginResponse = {
  accessToken: 'token-1',
  tokenType: 'Bearer',
  expiresAt: '2026-11-12T12:00:00Z',
  user: { id: 'user-1', username: 'demo' },
};

describe('AuthSession', () => {
  let now: string;

  function create(): AuthSession {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: SESSION_STORAGE, useValue: localStorage },
        { provide: NOW, useValue: () => new Date(now) },
      ],
    });
    return TestBed.inject(AuthSession);
  }

  beforeEach(() => {
    now = '2026-11-05T12:00:00Z';
    localStorage.clear();
  });
  afterEach(() => localStorage.clear());

  it('sem sessão guardada, ninguém está logado', () => {
    const session = create();

    expect(session.isLoggedIn()).toBe(false);
    expect(session.token()).toBeNull();
    expect(session.user()).toBeNull();
  });

  it('o login começa a sessão, que sobrevive a reabrir o app', () => {
    create().start(LOGIN);

    const reopened = create();
    expect(reopened.isLoggedIn()).toBe(true);
    expect(reopened.token()).toBe('token-1');
    expect(reopened.userId()).toBe('user-1');
    expect(reopened.user()?.username).toBe('demo');
  });

  it('token vencido não vale, mas o usuário continua conhecido', () => {
    const session = create();
    session.start(LOGIN);

    now = '2026-11-12T12:00:00Z';

    expect(session.token()).toBeNull();
    expect(session.isLoggedIn()).toBe(false);
    expect(session.userId()).toBe('user-1');
  });

  it('"Sair" apaga a sessão do aparelho', () => {
    const session = create();
    session.start(LOGIN);

    session.end();

    expect(session.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(KEY)).toBeNull();
  });

  it.each([
    ['JSON quebrado', '{'],
    ['sem token', JSON.stringify({ ...LOGIN, accessToken: undefined })],
    ['data inválida', JSON.stringify({ token: 't', expiresAt: 'amanhã', user: LOGIN.user })],
    ['sem usuário', JSON.stringify({ token: 't', expiresAt: LOGIN.expiresAt })],
  ])('sessão guardada com %s é ignorada', (_, raw) => {
    localStorage.setItem(KEY, raw);

    expect(create().isLoggedIn()).toBe(false);
  });
});
