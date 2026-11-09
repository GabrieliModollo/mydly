import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeOutline } from 'ionicons/icons';
import { CATEGORY_LABELS, parseAmount, parseQuantity } from '../entries/entry-format';
import { Category, EntryResponse, ResolveItemRequest, RoutineOutcome } from '../entries/entry.models';

/** O que o usuário diz que o item é. Itens não entendidos começam sem escolha. */
export type ConfirmKind = 'EXPENSE' | 'INCOME' | 'ROUTINE';

/** Unidades que a API aceita numa rotina, com o rótulo do seletor. */
export const ROUTINE_UNITS: readonly [string, string][] = [
  ['L', 'litros (L)'],
  ['mL', 'mililitros (mL)'],
  ['h', 'horas (h)'],
  ['min', 'minutos (min)'],
  ['km', 'quilômetros (km)'],
  ['kg', 'quilos (kg)'],
  ['copo', 'copos'],
];

/**
 * Painel de confirmação e correção (docs/design.md, tela Confirmar lançamento). Mostra a frase
 * original e deixa dizer o que o item é: um gasto, uma entrada ou uma rotina. Num gasto ou numa
 * entrada pede o valor e a categoria; numa rotina, se aconteceu e, se quiser, a quantidade. Um item
 * reaberto com "Não foi isso" já vem com o que o MyDly tinha entendido, para corrigir sem perder nada.
 * Só emite a resposta; quem envia à API é a tela.
 */
@Component({
  selector: 'app-confirm-sheet',
  imports: [IonIcon],
  templateUrl: './confirm-sheet.html',
  styleUrl: './confirm-sheet.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmSheet {
  readonly entry = input.required<EntryResponse>();
  readonly index = input.required<number>();

  readonly resolved = output<ResolveItemRequest>();
  readonly closed = output<void>();

  protected readonly categories = Object.entries(CATEGORY_LABELS) as [Category, string][];
  protected readonly units = ROUTINE_UNITS;
  protected readonly item = computed(() => this.entry().items[this.index()]);
  /** A frase citada no painel: num lançamento com vários itens, só o trecho do item. */
  protected readonly quote = computed(() =>
    this.entry().items.length > 1 && this.item().description ? this.item().description : this.entry().text,
  );

  protected readonly kind = signal<ConfirmKind | null>(null);
  protected readonly amountText = signal('');
  protected readonly category = signal<Category | null>(null);
  protected readonly outcome = signal<RoutineOutcome>('DONE');
  protected readonly quantityText = signal('');
  protected readonly unit = signal<string | null>(null);

  protected readonly amount = computed(() => parseAmount(this.amountText()));
  protected readonly quantity = computed(() => parseQuantity(this.quantityText()));
  protected readonly isFinance = computed(() => this.kind() === 'EXPENSE' || this.kind() === 'INCOME');
  protected readonly canSave = computed(
    () =>
      (this.kind() === 'ROUTINE' && this.quantity() !== undefined) || (this.isFinance() && this.amount() !== null),
  );
  protected readonly showAmountError = computed(() => this.amountText().trim() !== '' && this.amount() === null);
  protected readonly showQuantityError = computed(() => this.quantity() === undefined);

  protected readonly title = computed(() => {
    switch (this.kind()) {
      case 'EXPENSE':
        return 'Quanto foi?';
      case 'INCOME':
        return 'Quanto entrou?';
      case 'ROUTINE':
        return 'Como foi?';
      default:
        return 'O que foi isso?';
    }
  });
  protected readonly saveLabel = computed(() => {
    switch (this.kind()) {
      case 'INCOME':
        return 'Salvar entrada';
      case 'ROUTINE':
        return 'Salvar rotina';
      default:
        return 'Salvar gasto';
    }
  });
  protected readonly dismissLabel = computed(() => {
    switch (this.kind()) {
      case 'EXPENSE':
        return 'Não foi um gasto';
      case 'INCOME':
        return 'Não foi uma entrada';
      case 'ROUTINE':
        return 'Não foi uma rotina';
      default:
        return 'Não era nada';
    }
  });

  constructor() {
    addIcons({ closeOutline });
    // Ao abrir (ou reabrir com dados novos), parte do que o MyDly entendeu.
    effect(() => {
      const item = this.item();
      this.kind.set(item.type === 'FINANCE' ? (item.direction ?? 'EXPENSE') : item.type === 'ROUTINE' ? 'ROUTINE' : null);
      this.category.set(item.category);
      // Item reaberto com "Não foi isso" já traz o valor ou a quantidade, para corrigir em vez de digitar de novo.
      this.amountText.set(item.amount !== null ? item.amount.toFixed(2).replace('.', ',') : '');
      this.outcome.set(item.outcome ?? 'DONE');
      this.quantityText.set(item.quantity !== null ? String(item.quantity).replace('.', ',') : '');
      this.unit.set(item.unit);
    });
  }

  protected chooseKind(kind: ConfirmKind): void {
    this.kind.set(kind);
  }

  protected chooseOutcome(outcome: RoutineOutcome): void {
    this.outcome.set(outcome);
  }

  protected onAmountInput(event: Event): void {
    this.amountText.set((event.target as HTMLInputElement).value);
  }

  protected onQuantityInput(event: Event): void {
    this.quantityText.set((event.target as HTMLInputElement).value);
  }

  protected onUnitChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.unit.set(value === '' ? null : value);
  }

  protected toggleCategory(category: Category): void {
    this.category.set(this.category() === category ? null : category);
  }

  protected save(event: Event): void {
    event.preventDefault();
    if (!this.canSave()) {
      return;
    }
    const kind = this.kind();
    const amount = this.amount();
    if (kind === 'ROUTINE') {
      const quantity = this.quantity() ?? null;
      this.resolved.emit({
        action: 'CONFIRM_ROUTINE',
        outcome: this.outcome(),
        quantity,
        unit: quantity === null ? null : this.unit(),
      });
    } else if (kind !== null && amount !== null) {
      this.resolved.emit({ action: 'CONFIRM_FINANCE', amount, direction: kind, category: this.category() });
    }
  }

  protected dismiss(): void {
    this.resolved.emit({ action: 'DISMISS' });
  }
}
