import { Tone } from './week.models';

const KEY = 'mydly.tone';
const TONES: readonly Tone[] = ['gentle', 'direct', 'scold'];

/** Tom padrão das mensagens da semana. */
export const DEFAULT_TONE: Tone = 'direct';

/**
 * O tom escolhido neste aparelho. Até existir usuário (etapa 9 do backlog), a escolha fica só aqui.
 * Sem armazenamento (aba anônima, bloqueio do navegador), vale o padrão.
 */
export function loadTone(storage?: Storage): Tone {
  try {
    // Ler o localStorage já lança erro quando o navegador bloqueia o armazenamento; por isso fica aqui dentro.
    const saved = (storage ?? globalThis.localStorage)?.getItem(KEY);
    return TONES.includes(saved as Tone) ? (saved as Tone) : DEFAULT_TONE;
  } catch {
    return DEFAULT_TONE;
  }
}

/** Guarda o tom neste aparelho; se o navegador não deixar, a escolha vale só até fechar o app. */
export function saveTone(tone: Tone, storage?: Storage): void {
  try {
    (storage ?? globalThis.localStorage)?.setItem(KEY, tone);
  } catch {
    // Sem armazenamento: o tom continua valendo na tela aberta.
  }
}
