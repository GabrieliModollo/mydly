import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Apps Android e iOS (ADR 0016). O endereço da API vem de MYDLY_API_URL no build nativo
 * (`npm run build:native`). Com um endereço `http://` (o backend no computador, durante o
 * desenvolvimento), o Android precisa deixar a página `https://localhost` chamar um endereço sem TLS;
 * com HTTPS, isso fica desligado.
 */
const apiUrl = process.env['MYDLY_API_URL'] ?? 'http://10.0.2.2:8080';
const insecureApi = apiUrl.startsWith('http://');

const config: CapacitorConfig = {
  appId: 'com.mydly.app',
  appName: 'MyDly',
  webDir: 'dist/mydly/browser',
  android: {
    allowMixedContent: insecureApi,
  },
  plugins: {
    PushNotifications: {
      // Com o app aberto, a notificação também aparece na barra, com som.
      presentationOptions: ['badge', 'sound', 'alert'],
    },
  },
};

export default config;
