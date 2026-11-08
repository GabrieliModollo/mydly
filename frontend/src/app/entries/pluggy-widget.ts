import { Injectable } from '@angular/core';

/** Como terminou o widget: o item criado, um erro do Pluggy ou o usuário fechou. */
export type PluggyWidgetResult =
  | { kind: 'success'; itemId: string }
  | { kind: 'error'; message: string }
  | { kind: 'closed' };

/**
 * O widget Pluggy Connect (ADR 0015), carregado só quando o usuário vai conectar um banco, para não
 * pesar na abertura do app. Mostra só os bancos de teste ({@code includeSandbox}), em português, e
 * devolve uma promessa com o resultado. Substituível nos testes.
 */
@Injectable({ providedIn: 'root' })
export class PluggyWidget {
  async open(connectToken: string, theme: 'light' | 'dark'): Promise<PluggyWidgetResult> {
    const { PluggyConnect } = await import('pluggy-connect-sdk');
    return new Promise<PluggyWidgetResult>((resolve) => {
      let settled = false;
      const finish = (result: PluggyWidgetResult) => {
        if (!settled) {
          settled = true;
          resolve(result);
        }
      };
      const widget = new PluggyConnect({
        connectToken,
        includeSandbox: true,
        language: 'pt',
        theme,
        onSuccess: ({ item }) => finish({ kind: 'success', itemId: item.id }),
        onError: (error) => finish({ kind: 'error', message: error.message }),
        onClose: () => finish({ kind: 'closed' }),
      });
      widget.init().catch(() => finish({ kind: 'error', message: 'Não foi possível abrir o Pluggy.' }));
    });
  }
}
