import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeOutline } from 'ionicons/icons';
import { CATEGORY_LABELS, parseAmount } from '../entries/entry-format';
import { Category, EntryResponse, ResolveItemRequest } from '../entries/entry.models';

/** O que o usuário diz que o item é. Itens não entendidos começam sem escolha. */
export type ConfirmKind = 'EXPENSE' | 'INCOME' | 'ROUTINE';

/**
 * Painel "Quanto foi?" (docs/design.md, tela Confirmar lançamento). Mostra a frase original e pede
 * o que falta: o valor e a categoria de um gasto, ou, num item não entendido, primeiro o tipo.
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
  protected readonly item = computed(() => this.entry().items[this.index()]);
  protected readonly unknown = computed(() => this.item().type === 'UNKNOWN');

  protected readonly kind = signal<ConfirmKind | null>(null);
  protected readonly amountText = signal('');
  protected readonly category = signal<Category | null>(null);

  protected readonly amount = computed(() => parseAmount(this.amountText()));
  protected readonly isFinance = computed(() => this.kind() === 'EXPENSE' || this.kind() === 'INCOME');
  protected readonly canSave = computed(() => this.kind() === 'ROUTINE' || (this.isFinance() && this.amount() !== null));
  protected readonly showAmountError = computed(() => this.amountText().trim() !== '' && this.amount() === null);

  protected readonly title = computed(() => (this.unknown() ? 'O que foi isso?' : 'Quanto foi?'));
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
  protected readonly dismissLabel = computed(() => (this.unknown() ? 'Não era nada' : 'Não foi um gasto'));

  constructor() {
    addIcons({ closeOutline });
    // Ao abrir (ou reabrir com dados novos), parte do que o parser sugeriu.
    effect(() => {
      const item = this.item();
      this.kind.set(item.type === 'FINANCE' ? (item.direction ?? 'EXPENSE') : item.type === 'ROUTINE' ? 'ROUTINE' : null);
      this.category.set(item.category);
      this.amountText.set('');
    });
  }

  protected chooseKind(kind: ConfirmKind): void {
    this.kind.set(kind);
  }

  protected onAmountInput(event: Event): void {
    this.amountText.set((event.target as HTMLInputElement).value);
  }

  protected toggleCategory(category: Category): void {
    this.category.set(this.category() === category ? null : category);
  }

  protected save(event: Event): void {
    event.preventDefault();
    const kind = this.kind();
    const amount = this.amount();
    if (kind === 'ROUTINE') {
      this.resolved.emit({ action: 'CONFIRM_ROUTINE' });
    } else if (kind !== null && amount !== null) {
      this.resolved.emit({ action: 'CONFIRM_FINANCE', amount, direction: kind, category: this.category() });
    }
  }

  protected dismiss(): void {
    this.resolved.emit({ action: 'DISMISS' });
  }
}
