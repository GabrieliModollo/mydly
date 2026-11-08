import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { OpenFinanceApi } from '../entries/open-finance-api';
import { OpenFinanceStatus, OpenFinanceSync } from '../entries/open-finance.models';
import { PluggyWidget, PluggyWidgetResult } from '../entries/pluggy-widget';
import { OpenFinanceCard } from './open-finance-card';

const CONNECTED: OpenFinanceSync = {
  connection: { id: 'c1', institution: 'Pluggy Bank', status: 'UPDATED', lastSyncedAt: '2026-11-07T21:30:00Z' },
  imported: { format: 'CSV', read: 2, added: 2, matched: 0, duplicates: 0, transactions: [] },
};

describe('OpenFinanceCard', () => {
  let fixture: ComponentFixture<OpenFinanceCard>;
  let root: HTMLElement;
  let status: OpenFinanceStatus;
  let widgetResult: PluggyWidgetResult;
  let tokens: string[];
  let connected: string[];
  let connectAnswer: () => Observable<OpenFinanceSync>;
  let disconnected: string[];
  let imported: number;

  async function create(): Promise<void> {
    TestBed.configureTestingModule({
      imports: [OpenFinanceCard],
      providers: [
        {
          provide: OpenFinanceApi,
          useValue: {
            status: () => of(status),
            connectToken: () => of('token-1'),
            connect: (itemId: string) => {
              connected.push(itemId);
              return connectAnswer();
            },
            sync: () => of({ ...CONNECTED, imported: { ...CONNECTED.imported!, added: 0, duplicates: 2, read: 2 } }),
            disconnect: (id: string) => {
              disconnected.push(id);
              return of(undefined);
            },
          },
        },
        {
          provide: PluggyWidget,
          useValue: {
            open: async (token: string) => {
              tokens.push(token);
              return widgetResult;
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(OpenFinanceCard);
    fixture.componentInstance.imported.subscribe(() => imported++);
    await fixture.whenStable();
    root = fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    status = { enabled: true, connections: [] };
    widgetResult = { kind: 'success', itemId: 'item-1' };
    tokens = [];
    connected = [];
    disconnected = [];
    imported = 0;
    connectAnswer = () => of(CONNECTED);
  });

  function button(label: string): HTMLButtonElement {
    const found = [...root.querySelectorAll('button')].find(
      (candidate) => candidate.textContent?.trim() === label || candidate.getAttribute('aria-label') === label,
    );
    if (!found) {
      throw new Error(`botão "${label}" não encontrado`);
    }
    return found;
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  it('sem as chaves do Pluggy no servidor, o cartão não aparece', async () => {
    status = { enabled: false, connections: [] };
    await create();

    expect(root.textContent?.trim()).toBe('');
  });

  it('conecta o banco de teste pelo widget e avisa a tela', async () => {
    await create();

    button('Conectar banco de teste').click();
    await settle();

    expect(tokens).toEqual(['token-1']);
    expect(connected).toEqual(['item-1']);
    expect(imported).toBe(1);
    expect(root.querySelector('[role="status"]')?.textContent).toContain(
      'Banco de teste conectado. 2 transações lidas: 2 entraram nos seus números.',
    );
    expect(root.textContent).toContain('Pluggy Bank');
    expect(root.textContent).toContain('Conectado');
  });

  it('fechar o widget não muda nada', async () => {
    widgetResult = { kind: 'closed' };
    await create();

    button('Conectar banco de teste').click();
    await settle();

    expect(connected).toEqual([]);
    expect(root.querySelector('[role="alert"]')?.textContent?.trim()).toBe('');
  });

  it('banco real recusado pela API mostra o motivo', async () => {
    connectAnswer = () =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { detail: 'O MyDly só aceita bancos de teste (sandbox). A conexão foi desfeita.' },
          }),
      );
    await create();

    button('Conectar banco de teste').click();
    await settle();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('só aceita bancos de teste');
    expect(imported).toBe(0);
  });

  it('sincroniza e desconecta uma conexão', async () => {
    status = { enabled: true, connections: [CONNECTED.connection] };
    await create();

    button('Sincronizar: Pluggy Bank').click();
    await settle();
    expect(root.querySelector('[role="status"]')?.textContent).toContain('2 já tinham sido importadas');

    button('Desconectar: Pluggy Bank').click();
    await settle();
    expect(disconnected).toEqual(['c1']);
    expect(root.textContent).not.toContain('Sincronizar');
    expect(root.querySelector('[role="status"]')?.textContent).toContain('As transações que já entraram continuam.');
  });
});
