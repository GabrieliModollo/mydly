import { ChangeDetectionStrategy, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonContent, IonFooter, IonHeader, IonIcon, IonSpinner, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { arrowBack, cloudOfflineOutline, documentAttachOutline } from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { ImportedTransaction } from '../entries/import.models';
import { ImportsApi } from '../entries/imports-api';
import { AppNav } from '../nav/app-nav';
import { amountChip, importErrorMessage, importSummary, shortDate, statusLabel } from './import-format';
import { OpenFinanceCard } from './open-finance-card';

type LoadState = 'loading' | 'loaded' | 'error';

/**
 * Tela "Extrato" (docs/design.md, ADR 0014 e ADR 0015): envia o extrato OFX ou CSV do banco, conecta
 * um banco de teste pelo Open Finance e mostra as transações importadas, com o que cada uma faz nos
 * números e a opção de não contar.
 */
@Component({
  selector: 'app-imports',
  imports: [AppNav, IonContent, IonFooter, IonHeader, IonIcon, IonSpinner, IonToolbar, OpenFinanceCard, RouterLink],
  templateUrl: './imports.page.html',
  styleUrl: './imports.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportsPage implements OnInit {
  private readonly api = inject(ImportsApi);

  protected readonly amountChip = amountChip;
  protected readonly statusLabel = statusLabel;
  protected readonly shortDate = shortDate;

  protected readonly loadState = signal<LoadState>('loading');
  protected readonly transactions = signal<ImportedTransaction[]>([]);
  protected readonly uploading = signal(false);
  /** Nome do arquivo escolhido, mostrado ao lado do botão. */
  protected readonly fileName = signal<string | null>(null);
  /** Resumo da última importação, anunciado ao leitor de tela. */
  protected readonly summary = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  /** A transação que está mudando entre "conta" e "não conta". */
  protected readonly changing = signal<string | null>(null);

  private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
  private entered = false;

  constructor() {
    addIcons({ arrowBack, cloudOfflineOutline, documentAttachOutline });
  }

  ngOnInit(): void {
    void this.load();
  }

  ionViewWillEnter(): void {
    if (this.entered) {
      void this.load();
    }
    this.entered = true;
  }

  protected async load(): Promise<void> {
    this.loadState.set('loading');
    try {
      this.transactions.set(await firstValueFrom(this.api.transactions()));
      this.loadState.set('loaded');
    } catch {
      this.loadState.set('error');
    }
  }

  protected onFileChosen(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.fileName.set(file?.name ?? null);
    this.error.set(null);
  }

  protected async upload(event: Event): Promise<void> {
    event.preventDefault();
    const input = this.fileInput().nativeElement;
    const file = input.files?.[0];
    if (!file) {
      this.error.set('Escolha o arquivo do extrato.');
      input.focus();
      return;
    }
    if (this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.error.set(null);
    this.summary.set(null);
    try {
      const response = await firstValueFrom(this.api.importStatement(file));
      this.summary.set(importSummary(response));
      input.value = '';
      this.fileName.set(null);
      await this.load();
    } catch (error) {
      this.error.set(importErrorMessage(error));
    } finally {
      this.uploading.set(false);
    }
  }

  /** "Não contar" ou "Contar de novo"; a lista troca a linha pela resposta da API. */
  protected async toggle(transaction: ImportedTransaction): Promise<void> {
    if (this.changing()) {
      return;
    }
    this.changing.set(transaction.id);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(this.api.setIgnored(transaction.id, transaction.status !== 'IGNORED'));
      this.transactions.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
    } catch {
      this.error.set('Não foi possível mudar a transação. Tente de novo.');
    } finally {
      this.changing.set(null);
    }
  }

  /** Rótulo do botão para leitor de tela, com a transação de que se trata. */
  protected toggleLabel(transaction: ImportedTransaction): string {
    const action = transaction.status === 'IGNORED' ? 'Contar de novo' : 'Não contar';
    return `${action}: ${transaction.description ?? 'transação'} de ${shortDate(transaction.date)}`;
  }
}
