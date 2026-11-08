import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { IonContent } from '@ionic/angular';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../entries/auth-api';
import { AuthSession } from '../entries/session';
import { MIN_PASSWORD_LENGTH, RegisterField, registerFailure, registerProblem } from './register-error';

/**
 * Tela de cadastro (ADR 0018). Confere as regras antes de enviar, cria a conta e já entra: a resposta
 * da API é a mesma do login. Os lançamentos guardados no aparelho são de um usuário por vez (ADR 0012),
 * então entrar com a conta nova descarta a fila de quem estava antes.
 */
@Component({
  selector: 'app-register',
  imports: [IonContent, RouterLink],
  templateUrl: './register.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly api = inject(AuthApi);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  protected readonly minPassword = MIN_PASSWORD_LENGTH;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** O campo com problema, marcado com {@code aria-invalid}. */
  protected readonly invalid = signal<RegisterField | null>(null);

  private readonly username = viewChild.required<ElementRef<HTMLInputElement>>('username');
  private readonly password = viewChild.required<ElementRef<HTMLInputElement>>('password');
  private readonly confirmation = viewChild.required<ElementRef<HTMLInputElement>>('confirmation');

  /** Lê os campos direto do elemento: sem zone.js, um Enter logo depois da digitação não espera a tela. */
  protected submit(event: Event): void {
    event.preventDefault();
    const username = this.value('username').trim();
    const password = this.value('password');
    const problem = registerProblem(username, password, this.value('confirmation'));
    if (problem) {
      this.fail(problem.message, problem.field);
      this.input(problem.field).focus();
      return;
    }
    void this.register(username, password);
  }

  private async register(username: string, password: string): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.fail(null, null);
    try {
      this.session.start(await firstValueFrom(this.api.register(username, password)));
      this.password().nativeElement.value = '';
      this.confirmation().nativeElement.value = '';
      await this.router.navigateByUrl('/', { replaceUrl: true });
    } catch (error) {
      const failure = registerFailure(error);
      this.fail(failure.message, failure.field);
    } finally {
      this.busy.set(false);
    }
  }

  private value(field: RegisterField): string {
    return this.input(field).value;
  }

  private input(field: RegisterField): HTMLInputElement {
    return this[field]().nativeElement;
  }

  private fail(message: string | null, field: RegisterField | null): void {
    this.error.set(message);
    this.invalid.set(field);
  }
}

