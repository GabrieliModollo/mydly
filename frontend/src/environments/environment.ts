/**
 * Configuração do app na web: a API fica na mesma origem (proxy do `ng serve` ou nginx), então os
 * pedidos usam caminhos relativos. O build nativo troca este arquivo pelo gerado em
 * `scripts/native-env.mjs` (ADR 0016).
 */
export const environment = {
  /** Endereço da API; vazio usa a mesma origem do app. */
  apiBaseUrl: '',
};
