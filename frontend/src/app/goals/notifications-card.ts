import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NotificationsApi, NotificationSettings } from '../entries/notifications-api';
import { PushService } from '../entries/push';
import { loadTone } from '../entries/tone-storage';

type Toggle = 'budgetAlerts' | 'dailyReminder' | 'weeklySummary';

/** As três notificações, com o texto que a pessoa vê ao escolher (docs/design.md). */
const OPTIONS: { key: Toggle; label: string; hint: string }[] = [
  { key: 'budgetAlerts', label: 'Alerta de orçamento', hint: 'Quando uma categoria chega a 80% do limite e quando passa dele.' },
  { key: 'dailyReminder', label: 'Lembrete do dia', hint: 'Às 21h, se você não lançou nada no dia.' },
  { key: 'weeklySummary', label: 'Resumo da semana', hint: 'No último dia da semana, à noite, no tom que você escolheu.' },
];

/**
 * Cartão "Notificações" da aba Metas (ADR 0016). Só existe no app Android e iOS: na web o push não
 * está disponível e o cartão não aparece.
 */
@Component({
  selector: 'app-notifications-card',
  templateUrl: './notifications-card.html',
  styleUrl: './goals.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsCard implements OnInit {
  protected readonly push = inject(PushService);
  private readonly api = inject(NotificationsApi);

  protected readonly options = OPTIONS;
  protected readonly settings = signal<NotificationSettings | null>(null);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    if (this.push.available && this.push.enabled()) {
      void this.loadSettings();
    }
  }

  protected async enable(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    try {
      const result = await this.push.enable();
      if (result === 'enabled') {
        await this.loadSettings();
        this.message.set('Notificações ativadas.');
      } else if (result === 'denied') {
        this.error.set('O aparelho não deixou o MyDly mandar notificações. Libere nas configurações do sistema.');
      } else {
        this.error.set('Não foi possível ativar as notificações agora. Tente de novo.');
      }
    } finally {
      this.busy.set(false);
    }
  }

  protected async disable(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    try {
      await this.push.disable();
      this.settings.set(null);
      this.message.set('Notificações desativadas neste aparelho.');
    } finally {
      this.busy.set(false);
    }
  }

  /** Liga ou desliga um tipo; o tom vai junto, o mesmo escolhido no Resumo. */
  protected async toggle(key: Toggle, event: Event): Promise<void> {
    const current = this.settings();
    if (!current) {
      return;
    }
    const checked = (event.target as HTMLInputElement).checked;
    const wanted: NotificationSettings = { ...current, [key]: checked, tone: loadTone() };
    this.settings.set(wanted);
    this.error.set(null);
    try {
      this.settings.set(await firstValueFrom(this.api.saveSettings(wanted)));
    } catch {
      this.settings.set(current);
      (event.target as HTMLInputElement).checked = current[key];
      this.error.set('Não foi possível salvar. Tente de novo.');
    }
  }

  private async loadSettings(): Promise<void> {
    try {
      this.settings.set(await firstValueFrom(this.api.settings()));
    } catch {
      this.error.set('Não foi possível carregar as notificações.');
    }
  }
}
