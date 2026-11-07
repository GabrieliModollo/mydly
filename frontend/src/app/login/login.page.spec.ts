import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { Observable, of, throwError } from 'rxjs';
import { AuthApi } from '../entries/auth-api';
import { LoginResponse } from '../entries/auth.models';
import { AuthSession } from '../entries/session';
import { LoginPage } from './login.page';

const RESPONSE: LoginResponse = {
  accessToken: 't',
  tokenType: 'Bearer',
  expiresAt: '2026-11-12T12:00:00Z',
  user: { id: 'u1', username: 'demo' },
};

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let calls: [string, string][];
  let answer: () => Observable<LoginResponse>;
  let started: LoginResponse[];
  let navigations: string[];

  beforeEach(async () => {
    calls = [];
    started = [];
    navigations = [];
    answer = () => of(RESPONSE);
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        provideRouter([]),
        {
          provide: AuthApi,
          useValue: {
            login: (username: string, password: string) => {
              calls.push([username, password]);
              return answer();
            },
          },
        },
        { provide: AuthSession, useValue: { start: (response: LoginResponse) => started.push(response) } },
      ],
    });
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockImplementation(async (url) => {
      navigations.push(String(url));
      return true;
    });
    fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
  });

  function element<T extends Element>(selector: string): T {
    return (fixture.nativeElement as HTMLElement).querySelector(selector) as T;
  }

  function fill(username: string, password: string): void {
    element<HTMLInputElement>('#login-username').value = username;
    element<HTMLInputElement>('#login-password').value = password;
  }

  function submit(): void {
    element<HTMLFormElement>('form').dispatchEvent(new Event('submit', { cancelable: true }));
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  it('todo campo tem rótulo', () => {
    expect(element('label[for="login-username"]').textContent).toContain('Usuário');
    expect(element('label[for="login-password"]').textContent).toContain('Senha');
  });

  it('entra com usuário e senha e vai para o Hoje', async () => {
    fill(' demo ', 'demo');
    submit();
    await settle();

    expect(calls).toEqual([['demo', 'demo']]);
    expect(started).toEqual([RESPONSE]);
    expect(navigations).toEqual(['/']);
    expect(element<HTMLInputElement>('#login-password').value).toBe('');
  });

  it('"Entrar na demonstração" usa o usuário público', async () => {
    const demo = [...fixture.nativeElement.querySelectorAll('button')].find((button: HTMLButtonElement) =>
      button.textContent?.includes('demonstração'),
    ) as HTMLButtonElement;
    demo.click();
    await settle();

    expect(calls).toEqual([['demo', 'demo']]);
    expect(navigations).toEqual(['/']);
  });

  it('campos vazios pedem o usuário e a senha sem chamar a API', async () => {
    submit();
    await settle();

    expect(calls).toEqual([]);
    expect(element('[role="alert"]').textContent).toContain('Informe o usuário e a senha.');
  });

  it('senha errada mostra o erro e não entra', async () => {
    answer = () => throwError(() => new HttpErrorResponse({ status: 401 }));
    fill('demo', 'errada');
    submit();
    await settle();

    expect(started).toEqual([]);
    expect(navigations).toEqual([]);
    expect(element('[role="alert"]').textContent).toContain('Usuário ou senha incorretos.');
    expect(element('#login-password').getAttribute('aria-invalid')).toBe('true');
  });
});
