import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { Observable, of, throwError } from 'rxjs';
import { AuthApi } from '../entries/auth-api';
import { LoginResponse } from '../entries/auth.models';
import { AuthSession } from '../entries/session';
import { REGISTER_MESSAGES } from './register-error';
import { RegisterPage } from './register.page';

const RESPONSE: LoginResponse = {
  accessToken: 't',
  tokenType: 'Bearer',
  expiresAt: '2026-11-15T12:00:00Z',
  user: { id: 'u2', username: 'ana' },
};

describe('RegisterPage', () => {
  let fixture: ComponentFixture<RegisterPage>;
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
      imports: [RegisterPage],
      providers: [
        provideIonicAngular({ mode: 'md' }),
        provideRouter([]),
        {
          provide: AuthApi,
          useValue: {
            register: (username: string, password: string) => {
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
    fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
  });

  function element<T extends Element>(selector: string): T {
    return (fixture.nativeElement as HTMLElement).querySelector(selector) as T;
  }

  function fill(username: string, password: string, confirmation = password): void {
    element<HTMLInputElement>('#register-username').value = username;
    element<HTMLInputElement>('#register-password').value = password;
    element<HTMLInputElement>('#register-confirmation').value = confirmation;
  }

  function submit(): void {
    element<HTMLFormElement>('form').dispatchEvent(new Event('submit', { cancelable: true }));
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  function alert(): string {
    return element('[role="alert"]').textContent ?? '';
  }

  it('todo campo tem rótulo e as regras ficam ligadas aos campos', () => {
    expect(element('label[for="register-username"]').textContent).toContain('Usuário');
    expect(element('label[for="register-password"]').textContent).toContain('Senha');
    expect(element('label[for="register-confirmation"]').textContent).toContain('Repita a senha');
    expect(element('#register-username').getAttribute('aria-describedby')).toBe('register-username-hint');
    expect(element('#register-password-hint').textContent).toContain('8 caracteres');
    expect(element('#register-password').getAttribute('autocomplete')).toBe('new-password');
  });

  it('cria a conta, já entra e vai para o Hoje', async () => {
    fill(' ana ', 'uma-senha-boa');
    submit();
    await settle();

    expect(calls).toEqual([['ana', 'uma-senha-boa']]);
    expect(started).toEqual([RESPONSE]);
    expect(navigations).toEqual(['/']);
    expect(element<HTMLInputElement>('#register-password').value).toBe('');
    expect(element<HTMLInputElement>('#register-confirmation').value).toBe('');
  });

  it('senha curta é avisada sem chamar a API, com o foco na senha', async () => {
    fill('ana', '1234567');
    submit();
    await settle();

    expect(calls).toEqual([]);
    expect(alert()).toContain(REGISTER_MESSAGES.passwordTooShort);
    expect(element('#register-password').getAttribute('aria-invalid')).toBe('true');
    expect(element('#register-username').getAttribute('aria-invalid')).toBeNull();
    expect(document.activeElement).toBe(element('#register-password'));
  });

  it('senhas diferentes não chamam a API', async () => {
    fill('ana', 'uma-senha-boa', 'outra-senha-boa');
    submit();
    await settle();

    expect(calls).toEqual([]);
    expect(alert()).toContain(REGISTER_MESSAGES.mismatch);
    expect(element('#register-confirmation').getAttribute('aria-invalid')).toBe('true');
  });

  it('usuário que já existe mostra o erro e não entra', async () => {
    answer = () => throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'USERNAME_TAKEN' } }));
    fill('demo', 'uma-senha-boa');
    submit();
    await settle();

    expect(started).toEqual([]);
    expect(navigations).toEqual([]);
    expect(alert()).toContain(REGISTER_MESSAGES.taken);
    expect(element('#register-username').getAttribute('aria-invalid')).toBe('true');
  });

  it('sem conexão avisa que precisa de internet', async () => {
    answer = () => throwError(() => new HttpErrorResponse({ status: 0 }));
    fill('ana', 'uma-senha-boa');
    submit();
    await settle();

    expect(started).toEqual([]);
    expect(alert()).toContain(REGISTER_MESSAGES.offline);
    expect(element('#register-username').getAttribute('aria-invalid')).toBeNull();
  });

  it('tem o caminho de volta para o login', () => {
    expect(element('a[href="/login"]').textContent).toContain('Entrar');
  });
});
