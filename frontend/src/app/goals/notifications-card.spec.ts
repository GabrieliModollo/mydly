import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NotificationSettings, NotificationsApi } from '../entries/notifications-api';
import { EnableResult, PushService } from '../entries/push';
import { NotificationsCard } from './notifications-card';

const SETTINGS: NotificationSettings = { budgetAlerts: true, dailyReminder: true, weeklySummary: true, tone: 'direct' };

describe('NotificationsCard', () => {
  let fixture: ComponentFixture<NotificationsCard>;
  let root: HTMLElement;
  let available: boolean;
  let enabled: ReturnType<typeof signal<boolean>>;
  let enableResult: EnableResult;
  let saved: NotificationSettings[];
  let saveFails: boolean;

  async function create(): Promise<void> {
    TestBed.configureTestingModule({
      imports: [NotificationsCard],
      providers: [
        {
          provide: PushService,
          useValue: {
            get available() {
              return available;
            },
            enabled,
            enable: async () => {
              if (enableResult === 'enabled') {
                enabled.set(true);
              }
              return enableResult;
            },
            disable: async () => enabled.set(false),
          },
        },
        {
          provide: NotificationsApi,
          useValue: {
            settings: () => of(SETTINGS),
            saveSettings: (settings: NotificationSettings) => {
              saved.push(settings);
              return saveFails ? throwError(() => new Error('x')) : of(settings);
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(NotificationsCard);
    await fixture.whenStable();
    root = fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    available = true;
    enabled = signal(false);
    enableResult = 'enabled';
    saved = [];
    saveFails = false;
    localStorage.clear();
  });

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  function button(label: string): HTMLButtonElement {
    return [...root.querySelectorAll('button')].find((item) => item.textContent?.trim() === label) as HTMLButtonElement;
  }

  it('na web o cartão não aparece', async () => {
    available = false;
    await create();

    expect(root.textContent?.trim()).toBe('');
  });

  it('ativar mostra as três escolhas, com rótulo e dica em cada uma', async () => {
    await create();

    button('Ativar notificações').click();
    await settle();

    const labels = [...root.querySelectorAll('label')].map((label) => label.textContent?.trim());
    expect(labels).toEqual(['Alerta de orçamento', 'Lembrete do dia', 'Resumo da semana']);
    const input = root.querySelector('#notify-dailyReminder') as HTMLInputElement;
    expect(input.checked).toBe(true);
    expect(root.querySelector('#' + input.getAttribute('aria-describedby'))?.textContent).toContain('21h');
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Notificações ativadas.');
  });

  it('permissão negada explica onde liberar', async () => {
    enableResult = 'denied';
    await create();

    button('Ativar notificações').click();
    await settle();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('configurações do sistema');
  });

  it('desligar uma escolha salva com o tom do aparelho', async () => {
    enabled.set(true);
    localStorage.setItem('mydly.tone', 'scold');
    await create();
    await settle();

    const input = root.querySelector('#notify-dailyReminder') as HTMLInputElement;
    input.checked = false;
    input.dispatchEvent(new Event('change'));
    await settle();

    expect(saved).toEqual([{ ...SETTINGS, dailyReminder: false, tone: 'scold' }]);
  });

  it('falha ao salvar desfaz a mudança', async () => {
    enabled.set(true);
    saveFails = true;
    await create();
    await settle();

    const input = root.querySelector('#notify-budgetAlerts') as HTMLInputElement;
    input.checked = false;
    input.dispatchEvent(new Event('change'));
    await settle();

    expect(input.checked).toBe(true);
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível salvar.');
  });

  it('desativar volta ao botão de ativar', async () => {
    enabled.set(true);
    await create();
    await settle();

    button('Desativar neste aparelho').click();
    await settle();

    expect(button('Ativar notificações')).toBeTruthy();
  });
});
