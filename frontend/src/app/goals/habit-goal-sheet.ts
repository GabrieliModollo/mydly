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
import { parseAmount } from '../entries/entry-format';
import { GoalKind, GoalUnit, HabitGoalRequest, HabitGoalResponse } from '../entries/goal.models';

/**
 * Painel de edição da meta de um hábito (aba Metas). Só emite a meta; quem envia à API é a tela.
 * Num hábito ruim a meta é um limite: no máximo n dias por semana, de 0 a 7.
 *
 * Acessibilidade: título do diálogo, foco no primeiro campo ao abrir, Esc fecha, todo campo com
 * label e erros anunciados.
 */
@Component({
  selector: 'app-habit-goal-sheet',
  imports: [IonIcon],
  templateUrl: './habit-goal-sheet.html',
  styleUrl: './goal-sheet.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(keydown.escape)': 'close($event)' },
})
export class HabitGoalSheet {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly habit = input.required<HabitGoalResponse>();
  /** Erro da API (400), já em texto para o usuário. */
  readonly error = input<string | null>(null);
  readonly saving = input(false);

  readonly saved = output<HabitGoalRequest>();
  readonly removed = output<void>();
  readonly closed = output<void>();

  protected readonly kind = signal<GoalKind>('WEEKLY_FREQUENCY');
  protected readonly quantityText = signal('');
  protected readonly unit = signal<GoalUnit | null>(null);
  protected readonly days = signal<number | null>(null);

  protected readonly isLimit = computed(() => this.habit().polarity === 'BAD');
  protected readonly title = computed(() => `${this.isLimit() ? 'Limite' : 'Meta'} de ${this.habit().name}`);
  protected readonly dayOptions = computed(() =>
    Array.from({ length: this.isLimit() ? 8 : 7 }, (_, index) => (this.isLimit() ? index : index + 1)),
  );
  protected readonly quantity = computed(() => parseAmount(this.quantityText()));
  protected readonly showQuantityError = computed(
    () => this.quantityText().trim() !== '' && this.quantity() === null,
  );
  protected readonly canSave = computed(() =>
    this.kind() === 'DAILY_QUANTITY' ? this.quantity() !== null && this.unit() !== null : this.days() !== null,
  );

  constructor() {
    addIcons({ closeOutline });
    // Ao abrir (ou reabrir com outro hábito), parte da meta atual.
    effect(() => {
      const habit = this.habit();
      const goal = habit.goal;
      this.kind.set(goal?.kind ?? habit.allowedKinds[0] ?? 'WEEKLY_FREQUENCY');
      this.quantityText.set(goal?.targetQuantity != null ? String(goal.targetQuantity).replace('.', ',') : '');
      this.unit.set(goal?.unit ?? habit.allowedUnits[0] ?? null);
      this.days.set(goal?.daysPerWeek ?? null);
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

  protected chooseKind(kind: GoalKind): void {
    this.kind.set(kind);
  }

  protected onQuantityInput(event: Event): void {
    this.quantityText.set((event.target as HTMLInputElement).value);
  }

  protected save(event: Event): void {
    event.preventDefault();
    if (!this.canSave()) {
      return;
    }
    const quantity = this.quantity();
    const unit = this.unit();
    const days = this.days();
    if (this.kind() === 'DAILY_QUANTITY' && quantity !== null && unit !== null) {
      this.saved.emit({ kind: 'DAILY_QUANTITY', targetQuantity: quantity, unit });
    } else if (days !== null) {
      this.saved.emit({ kind: 'WEEKLY_FREQUENCY', daysPerWeek: days });
    }
  }
}
