/**
 * UUID v4 para o id do lançamento, gerado no app (ADR 0006).
 *
 * {@code crypto.randomUUID} só existe em contexto seguro (HTTPS ou localhost). Ao abrir o app
 * pela rede local, por exemplo no celular via {@code http://192.168...}, ele não está disponível,
 * então montamos o UUID com {@code crypto.getRandomValues}, que funciona em qualquer contexto.
 */
export function randomId(source: Crypto = globalThis.crypto): string {
  if (typeof source.randomUUID === 'function') {
    return source.randomUUID();
  }
  const bytes = source.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // versão 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variante RFC 4122
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
