import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  LOCALE_ID,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import {
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonModal,
  IonRefresher,
  IonRefresherContent,
  IonSpinner,
  IonToolbar,
  RefresherCustomEvent,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { arrowUp, cloudOfflineOutline, refreshOutline } from 'ionicons/icons';
import { EntriesStore, NOW } from '../entries/entries-store';
import { chipsForItem, formatMoney } from '../entries/entry-format';
import { EntryItemResponse, EntryResponse, ResolveItemRequest } from '../entries/entry.models';
import { ConfirmSheet } from './confirm-sheet';
import { formatDayLabel } from './day-label';

/** Frases do README, para quem abre o app pela primeira vez saber o que escrever. */
const EXAMPLES = [
  'Gastei 50 reais na farmácia',
  'Bebi 2 litros de água',
  'Estudei durante 2 horas',
  'Recebi 100 reais',
  'Dormi bem',
];

@Component({
  selector: 'app-today',
  imports: [
    DatePipe,
    IonContent,
    IonFooter,
    IonHeader,
    IonIcon,
    IonModal,
    ConfirmSheet,
    IonRefresher,
    IonRefresherContent,
    IonSpinner,
    IonToolbar,
  ],
  templateUrl: './today.page.html',
  styleUrl: './today.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodayPage implements OnInit {
  protected readonly store = inject(EntriesStore);
  protected readonly todayLabel = formatDayLabel(inject(NOW)(), inject(LOCALE_ID));
  protected readonly examples = EXAMPLES;
  protected readonly chipsForItem = chipsForItem;
  protected readonly formatMoney = formatMoney;

  /** Item aberto no painel "Quanto foi?", ou {@code null} com o painel fechado. */
  protected readonly confirming = signal<{ entry: EntryResponse; index: number } | null>(null);
  /** Aviso curto no topo da lista (lançamento desatualizado, falha ao salvar). */
  protected readonly notice = signal<string | null>(null);
  protected readonly maxLength = 1000;

  protected readonly draft = signal('');
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('entryInput');

  constructor() {
    addIcons({ arrowUp, cloudOfflineOutline, refreshOutline });
  }

  ngOnInit(): void {
    void this.store.load();
  }

  protected onInput(event: Event): void {
    this.draft.set((event.target as HTMLInputElement).value);
  }

  /**
   * Envia o que está no campo. Lê o valor direto do elemento, e não do signal nem do estado do
   * botão: sem zone.js, o [disabled] do botão só é atualizado depois, e um Enter logo após a
   * digitação seria ignorado pelo envio implícito do formulário.
   */
  protected send(event: Event): void {
    event.preventDefault();
    if (event instanceof KeyboardEvent && event.isComposing) {
      return;
    }
    const field = this.input().nativeElement;
    const text = field.value;
    if (!text.trim()) {
      return;
    }
    field.value = '';
    this.draft.set('');
    void this.store.submit(text);
    field.focus();
  }

  protected useExample(text: string): void {
    const field = this.input().nativeElement;
    field.value = text;
    this.draft.set(text);
    field.focus();
  }

  protected openConfirm(entry: EntryResponse, index: number): void {
    this.notice.set(null);
    this.confirming.set({ entry, index });
  }

  /**
   * Fecha o painel na hora (a lista já mostra a resposta) e envia. Se o lançamento tinha mudado,
   * reabre o painel com a versão nova quando o item ainda precisa de confirmação.
   */
  protected async resolve(entryId: string, index: number, request: ResolveItemRequest): Promise<void> {
    this.confirming.set(null);
    const result = await this.store.resolve(entryId, index, request);
    if (result === 'ok') {
      // O aviso de "Não foi isso" já cumpriu o papel quando a correção é salva.
      this.notice.set(null);
    } else if (result === 'stale') {
      const fresh = this.store.find(entryId);
      if (fresh?.items[index]?.needsConfirmation) {
        this.notice.set('Este lançamento mudou. Confira os dados e salve de novo.');
        this.confirming.set({ entry: fresh, index });
      } else {
        this.notice.set('Este lançamento já tinha sido confirmado.');
      }
    } else if (result === 'error') {
      this.notice.set('Não foi possível salvar a confirmação. Tente de novo.');
    }
  }

  /** Rótulo para leitor de tela: a ação e o trecho do item, para cada botão dizer de qual item é. */
  protected reopenLabel(item: EntryItemResponse): string {
    const action = item.dismissed ? 'Desfazer descarte' : 'Não foi isso';
    return item.description ? `${action}: ${item.description}` : action;
  }

  /**
   * "Não foi isso" tira o item dos totais e abre o painel para corrigir ou descartar; fechar o painel
   * deixa o item pendente. "Desfazer" só traz de volta o item descartado, que fica pedindo confirmação.
   */
  protected async reopen(entry: EntryResponse, index: number): Promise<void> {
    this.notice.set(null);
    const wasDismissed = entry.items[index]?.dismissed ?? false;
    const result = await this.store.resolve(entry.id, index, { action: 'REOPEN' });
    if (result === 'ok') {
      const fresh = this.store.find(entry.id);
      if (!wasDismissed && fresh) {
        this.notice.set('Removido dos seus números. Corrija ou descarte.');
        this.confirming.set({ entry: fresh, index });
      }
    } else if (result === 'stale') {
      this.notice.set('Este lançamento mudou. Confira e tente de novo.');
    } else {
      this.notice.set('Não foi possível desfazer. Tente de novo.');
    }
  }

  protected async refresh(event: RefresherCustomEvent): Promise<void> {
    await this.store.load();
    await event.target.complete();
  }
}
