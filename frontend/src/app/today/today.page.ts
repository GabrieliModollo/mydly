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
  IonRefresher,
  IonRefresherContent,
  IonSpinner,
  IonToolbar,
  RefresherCustomEvent,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { arrowUp, cloudOfflineOutline, refreshOutline } from 'ionicons/icons';
import { EntriesStore, NOW } from '../entries/entries-store';
import { chipsFor, formatMoney } from '../entries/entry-format';
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
  protected readonly chipsFor = chipsFor;
  protected readonly formatMoney = formatMoney;
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

  protected async refresh(event: RefresherCustomEvent): Promise<void> {
    await this.store.load();
    await event.target.complete();
  }
}
