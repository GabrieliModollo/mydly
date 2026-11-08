// Gera src/environments/environment.native.ts com o endereço da API do app nativo (ADR 0016).
//
//   MYDLY_API_URL=https://api.exemplo.com npm run build:native
//
// Sem a variável, vale http://10.0.2.2:8080: o backend rodando no computador, visto de dentro do
// emulador Android. Num celular de verdade, use o IP do computador na rede (http://192.168.x.y:8080)
// ou um endereço HTTPS.
import { writeFileSync } from 'node:fs';

const DEFAULT_URL = 'http://10.0.2.2:8080';
const url = (process.env.MYDLY_API_URL ?? DEFAULT_URL).replace(/\/+$/, '');

if (!/^https?:\/\/[^/\s]+$/.test(url)) {
  console.error(`MYDLY_API_URL inválida: "${url}". Use só o endereço, como https://api.exemplo.com.`);
  process.exit(1);
}

writeFileSync(
  new URL('../src/environments/environment.native.ts', import.meta.url),
  `// Gerado por scripts/native-env.mjs. Não edite: rode npm run build:native com MYDLY_API_URL.\n` +
    `export const environment = {\n  apiBaseUrl: '${url}',\n};\n`,
);
console.log(`API do app nativo: ${url}`);
