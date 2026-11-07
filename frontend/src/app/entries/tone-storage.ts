import { Tone } from './week.models';

const KEY = 'mydly.tone';
const TONES: readonly Tone[] = ['gentle', 'direct', 'scold'];

/** Tom padrão das mensagens da semana. */
export const DEFAULT_TONE: Tone = 'direct';

/**
 * O tom escolhido neste aparelho. Até existir usuário (etapa 9 do backlog), a escolha fica só aqui.
 * Sem armazenamento (aba anônima, bloqueio do navegador), vale o padrão.
 */
export function loadTone(storage: Storage | undefined = globalThis.localStorage): Tone {
  try {
    const saved = storage?.getItem(KEY);
    return TONES.includes(saved as Tone) ? (saved as Tone) : DEFAULT_TONE;
  } catch {
    return DEFAULT_TONE;
  }
}

/** Guarda o tom neste aparelho; se o navegador não deixar, a escolha vale só até fechar o app. */
export function saveTone(tone: Tone, storage: Storage | undefined = globalThis.localStorage): void {
  try {
    storage?.setItem(KEY, tone);
  } catch {
    // Sem armazenamento: o tom continua valendo na tela aberta.
  }
}
