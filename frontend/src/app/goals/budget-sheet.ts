import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeOutline } from 'ionicons/icons';
import { CATEGORY_LABELS, parseAmount } from '../entries/entry-format';
import { BudgetPeriod, BudgetRequest, BudgetResponse } from '../entries/goal.models';

/**
 * Painel de edição do limite de gasto de uma categoria (aba Metas). Só emite o limite; quem envia à
 * API é a tela. Mesma acessibilidade do painel de meta de hábito.
 */
@Component({
  selector: 'app-budget-sheet',
  imports: [IonIcon],
  templateUrl: './budget-sheet.html',
  styleUrl: './goal-sheet.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(keydown.escape)': 'close($event)' },
})
export class BudgetSheet {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly budget = input.required<BudgetResponse>();
  readonly error = input<string | null>(null);
  readonly saving = input(false);

  readonly saved = output<BudgetRequest>();
  readonly removed = output<void>();
  readonly closed = output<void>();

  protected readonly period = signal<BudgetPeriod>('WEEKLY');
  protected readonly limitText = signal('');

  protected readonly title = computed(() => `Limite de ${CATEGORY_LABELS[this.budget().category]}`);
  protected readonly limit = computed(() => parseAmount(this.limitText()));
  protected readonly showLimitError = computed(() => this.limitText().trim() !== '' && this.limit() === null);

  constructor() {
    addIcons({ closeOutline });
    effect(() => {
      const budget = this.budget();
      this.period.set(budget.period ?? 'WEEKLY');
      this.limitText.set(budget.limit !== null ? budget.limit.toFixed(2).replace('.', ',') : '');
    });
    afterNextRender(() => this.focusFirst());
  }

  /** Põe o foco no primeiro campo do painel (não no botão de fechar). */
  focusFirst(): void {
    this.host.nativeElement.querySelector<HTMLElement>('.sheet__body input, .sheet__body button')?.focus();
  }

  protected close(event: Event): void {
    event.preventDefault();
    this.closed.emit();
  }

  protected onLimitInput(event: Event): void {
    this.limitText.set((event.target as HTMLInputElement).value);
  }

  protected save(event: Event): void {
    event.preventDefault();
    const limit = this.limit();
    if (limit !== null) {
      this.saved.emit({ period: this.period(), limit });
    }
  }
}
