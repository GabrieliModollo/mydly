import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { IonContent, IonFooter, IonHeader, IonIcon, IonSpinner, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { chevronBackOutline, chevronForwardOutline, cloudOfflineOutline } from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { loadTone, saveTone } from '../entries/tone-storage';
import { Tone, WeekInsight, WeekSummaryResponse } from '../entries/week.models';
import { WeeksApi } from '../entries/weeks-api';
import { AppNav } from '../nav/app-nav';
import {
  categoryBars,
  dayInitials,
  formatWeekRange,
  habitDescription,
  markLabel,
  previousWeekLabel,
  shiftDate,
  TONE_OPTIONS,
} from './summary-format';
import { formatMoney } from '../entries/entry-format';

type LoadState = 'loading' | 'loaded' | 'error';

/**
 * Tela "Resumo" (docs/design.md, Resumo da semana): a mensagem da semana no tom escolhido, os hábitos
 * com 7 pontos, os gastos por categoria e o cruzamento de sono com gastos quando ele existe.
 */
@Component({
  selector: 'app-summary',
  imports: [AppNav, IonContent, IonFooter, IonHeader, IonIcon, IonSpinner, IonToolbar],
  templateUrl: './summary.page.html',
  styleUrl: './summary.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummaryPage implements OnInit {
  private readonly api = inject(WeeksApi);

  protected readonly toneOptions = TONE_OPTIONS;
  protected readonly habitDescription = habitDescription;
  protected readonly markLabel = markLabel;
  protected readonly formatMoney = formatMoney;

  protected readonly tone = signal<Tone>(loadTone());
  /** O primeiro dia da semana mostrada, ou {@code null} para a semana atual. */
  protected readonly start = signal<string | null>(null);
  /** O primeiro dia da semana atual, conhecido depois da primeira resposta. */
  private readonly currentStart = signal<string | null>(null);
  protected readonly week = signal<WeekSummaryResponse | null>(null);
  protected readonly loadState = signal<LoadState>('loading');

  protected readonly range = computed(() => {
    const week = this.week();
    return week ? formatWeekRange(week.weekStart, week.weekEnd) : '';
  });
  protected readonly isCurrentWeek = computed(() => {
    const week = this.week();
    return week !== null && week.weekStart === this.currentStart();
  });
  protected readonly main = computed(() => this.week()?.insights[0] ?? null);
  /** As mensagens secundárias; o cruzamento de sono com gastos tem cartão próprio, no acento. */
  protected readonly secondary = computed(() =>
    (this.week()?.insights.slice(1) ?? []).filter((insight) => insight.kind !== 'SLEEP_AND_SPENDING'),
  );
  protected readonly crossing = computed<WeekInsight | null>(
    () => this.week()?.insights.slice(1).find((insight) => insight.kind === 'SLEEP_AND_SPENDING') ?? null,
  );
  protected readonly bars = computed(() => categoryBars(this.week()?.money.spentByCategory ?? []));
  protected readonly previous = computed(() => {
    const week = this.week();
    return week ? previousWeekLabel(week.previous, week.weekStart) : null;
  });
  /** As iniciais dos dias a partir do primeiro dia da semana, que o servidor define. */
  protected readonly initials = computed(() => {
    const week = this.week();
    return week ? dayInitials(week.weekStart) : [];
  });

  constructor() {
    addIcons({ chevronBackOutline, chevronForwardOutline, cloudOfflineOutline });
  }

  ngOnInit(): void {
    void this.load();
  }

  protected chooseTone(tone: Tone): void {
    if (tone === this.tone()) {
      return;
    }
    this.tone.set(tone);
    saveTone(tone);
    void this.load();
  }

  protected previousWeek(): void {
    const week = this.week();
    if (week) {
      this.start.set(shiftDate(week.weekStart, -7));
      void this.load();
    }
  }

  protected nextWeek(): void {
    const week = this.week();
    if (week && !this.isCurrentWeek()) {
      const next = shiftDate(week.weekStart, 7);
      this.start.set(next === this.currentStart() ? null : next);
      void this.load();
    }
  }

  protected async load(): Promise<void> {
    this.loadState.set('loading');
    try {
      const week = await firstValueFrom(this.api.summary(this.start(), this.tone()));
      if (this.start() === null) {
        this.currentStart.set(week.weekStart);
      }
      this.week.set(week);
      this.loadState.set('loaded');
    } catch {
      this.loadState.set('error');
    }
  }
}
