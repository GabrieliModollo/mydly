import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { IonContent } from '@ionic/angular';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../entries/auth-api';
import { DEMO_CREDENTIALS } from '../entries/auth.models';
import { AuthSession } from '../entries/session';
import { loginErrorMessage } from './login-error';

/**
 * Tela de login (ADR 0012). Daqui se cria uma conta (ADR 0018) ou se entra na demonstração, com o
 * usuário público {@code demo}.
 */
@Component({
  selector: 'app-login',
  imports: [IonContent, RouterLink],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly api = inject(AuthApi);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  protected readonly demo = DEMO_CREDENTIALS;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly username = viewChild.required<ElementRef<HTMLInputElement>>('username');
  private readonly password = viewChild.required<ElementRef<HTMLInputElement>>('password');

  /** Lê os campos direto do elemento: sem zone.js, um Enter logo depois da digitação não espera a tela. */
  protected submit(event: Event): void {
    event.preventDefault();
    const username = this.username().nativeElement.value.trim();
    const password = this.password().nativeElement.value;
    if (!username || !password) {
      this.error.set('Informe o usuário e a senha.');
      (username ? this.password() : this.username()).nativeElement.focus();
      return;
    }
    void this.login(username, password);
  }

  protected enterDemo(): void {
    this.username().nativeElement.value = this.demo.username;
    this.password().nativeElement.value = this.demo.password;
    void this.login(this.demo.username, this.demo.password);
  }

  private async login(username: string, password: string): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      this.session.start(await firstValueFrom(this.api.login(username, password)));
      this.password().nativeElement.value = '';
      await this.router.navigateByUrl('/', { replaceUrl: true });
    } catch (error) {
      this.error.set(loginErrorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
