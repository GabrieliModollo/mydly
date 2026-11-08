import { ChangeDetectionStrategy, Component, inject, OnInit, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { OpenFinanceApi } from '../entries/open-finance-api';
import { OpenFinanceConnection, OpenFinanceSync } from '../entries/open-finance.models';
import { PluggyWidget } from '../entries/pluggy-widget';
import { importSummary } from './import-format';
import { connectionStatusLabel, lastSyncLabel, openFinanceErrorMessage } from './open-finance-format';

/**
 * Cartão "Conectar banco" da tela Extrato (ADR 0015): conecta um banco de teste pelo Pluggy, só no
 * sandbox, e sincroniza ou desconecta. Some quando o servidor não tem as chaves do Pluggy.
 */
@Component({
  selector: 'app-open-finance-card',
  templateUrl: './open-finance-card.html',
  styleUrl: './imports.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpenFinanceCard implements OnInit {
  private readonly api = inject(OpenFinanceApi);
  private readonly widget = inject(PluggyWidget);

  /** Avisa a tela quando transações entraram, para ela recarregar a lista. */
  readonly imported = output<void>();

  protected readonly connectionStatusLabel = connectionStatusLabel;
  protected readonly lastSyncLabel = lastSyncLabel;

  protected readonly enabled = signal(false);
  protected readonly connections = signal<OpenFinanceConnection[]>([]);
  /** O que está em andamento: "connect" ou o id da conexão sincronizando ou desconectando. */
  protected readonly busy = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  async load(): Promise<void> {
    try {
      const status = await firstValueFrom(this.api.status());
      this.enabled.set(status.enabled);
      this.connections.set(status.connections);
    } catch {
      // Sem resposta, o cartão fica escondido; a importação de arquivo continua funcionando.
      this.enabled.set(false);
    }
  }

  protected async connect(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.start('connect');
    try {
      const token = await firstValueFrom(this.api.connectToken());
      const result = await this.widget.open(token, prefersDark() ? 'dark' : 'light');
      if (result.kind === 'closed') {
        return;
      }
      if (result.kind === 'error') {
        this.error.set(`O Pluggy não conseguiu conectar: ${result.message}`);
        return;
      }
      this.done(await firstValueFrom(this.api.connect(result.itemId)), 'Banco de teste conectado.');
    } catch (error) {
      this.error.set(openFinanceErrorMessage(error));
    } finally {
      this.busy.set(null);
    }
  }

  protected async sync(connection: OpenFinanceConnection): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.start(connection.id);
    try {
      this.done(await firstValueFrom(this.api.sync(connection.id)), 'Sincronizado.');
    } catch (error) {
      this.error.set(openFinanceErrorMessage(error));
    } finally {
      this.busy.set(null);
    }
  }

  protected async disconnect(connection: OpenFinanceConnection): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.start(connection.id);
    try {
      await firstValueFrom(this.api.disconnect(connection.id));
      this.connections.update((list) => list.filter((item) => item.id !== connection.id));
      this.message.set('Banco desconectado. As transações que já entraram continuam.');
    } catch (error) {
      this.error.set(openFinanceErrorMessage(error));
    } finally {
      this.busy.set(null);
    }
  }

  protected label(action: string, connection: OpenFinanceConnection): string {
    return `${action}: ${connection.institution ?? 'banco conectado'}`;
  }

  private start(what: string): void {
    this.busy.set(what);
    this.message.set(null);
    this.error.set(null);
  }

  private done(sync: OpenFinanceSync, prefix: string): void {
    this.connections.update((list) =>
      list.some((item) => item.id === sync.connection.id)
        ? list.map((item) => (item.id === sync.connection.id ? sync.connection : item))
        : [...list, sync.connection],
    );
    if (sync.imported) {
      this.message.set(`${prefix} ${importSummary(sync.imported)}`);
      this.imported.emit();
    } else {
      this.message.set(`${prefix} ${connectionStatusLabel(sync.connection)}`);
    }
  }
}

function prefersDark(): boolean {
  try {
    return globalThis.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  } catch {
    return false;
  }
}
