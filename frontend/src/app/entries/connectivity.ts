import { DestroyRef, DOCUMENT, inject, Injectable, signal } from '@angular/core';

/**
 * Estado da conexão do aparelho, pelos eventos {@code online} e {@code offline} do navegador.
 *
 * {@code navigator.onLine} falso é confiável: não há rede. Verdadeiro só diz que há uma rede, não que
 * a API responde (Wi-Fi sem internet, servidor fora do ar). Por isso quem envia trata a falha de envio
 * como a palavra final e usa este estado só para não tentar quando com certeza não há rede.
 */
@Injectable({ providedIn: 'root' })
export class Connectivity {
  private readonly state = signal(true);
  private readonly listeners = new Set<() => void>();

  /** Há uma rede, até onde o navegador sabe. */
  readonly online = this.state.asReadonly();

  constructor() {
    const document = inject(DOCUMENT);
    const view = document.defaultView;
    if (!view) {
      return;
    }
    this.state.set(view.navigator.onLine);

    const onOnline = () => {
      this.state.set(true);
      this.notify();
    };
    const onOffline = () => this.state.set(false);
    // O app volta para a frente (celular desbloqueado, aba reaberta): bom momento para tentar de novo.
    const onVisible = () => {
      if (document.visibilityState === 'visible' && this.state()) {
        this.notify();
      }
    };
    view.addEventListener('online', onOnline);
    view.addEventListener('offline', onOffline);
    document.addEventListener('visibilitychange', onVisible);
    inject(DestroyRef).onDestroy(() => {
      view.removeEventListener('online', onOnline);
      view.removeEventListener('offline', onOffline);
      document.removeEventListener('visibilitychange', onVisible);
    });
  }

  /** Chama {@code listener} quando a rede volta ou o app volta para a frente com rede. */
  whenBackOnline(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    this.listeners.forEach((listener) => listener());
  }
}
