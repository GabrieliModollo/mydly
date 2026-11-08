# ADR 0016 — Apps Android e iOS com Capacitor e notificações push pelo Firebase

- **Status:** Aceita
- **Data:** 2026-11-08
- **Complementa:** [ADR 0004](0004-stack-tecnologica.md), [ADR 0011](0011-fila-offline-no-aparelho.md) e [ADR 0012](0012-login-com-jwt-e-dados-por-usuario.md)

## Contexto

O ADR 0004 escolheu Angular com Ionic e Capacitor para ter o mesmo app na web, no Android e no iOS. Até a etapa 12, só a web existia. A etapa 13 pede o build com Capacitor e notificações push. A autora escolheu três notificações: o alerta de orçamento, o lembrete do dia e o resumo da semana.

Era preciso decidir:

- como o app nativo acha a API, que na web está na mesma origem;
- por onde mandar o push, para Android e iOS;
- quando cada notificação sai, e como não repetir;
- o que fica de fora por limite do ambiente (o iOS não compila no Windows).

## Decisão

### Apps

- **Projetos `android/` e `ios/` gerados pelo Capacitor 8** (`@capacitor/android` e `@capacitor/ios` 8.6.0, a mesma versão do núcleo), versionados no repositório como o Capacitor recomenda. O iOS usa Swift Package Manager, sem CocoaPods. O id do app é `com.mydly.app`, já definido na etapa 1.
- **Endereço da API no build:** na web, a API fica na mesma origem e os caminhos são relativos. No app, a página vem do próprio aparelho (`https://localhost` no Android, `capacitor://localhost` no iOS), então um interceptador põe o endereço da API na frente de `/api/...`. O endereço vem de `MYDLY_API_URL` no `npm run build:native`, que gera `environment.native.ts` (fora do repositório). Sem a variável, vale `http://10.0.2.2:8080`: o backend no computador, visto do emulador.
- **CORS só para o app nativo:** a API aceita as origens `https://localhost` e `capacitor://localhost` (`mydly.auth.allowed-origins`), sem cookie, com os headers `Authorization`, `Content-Type` e `If-Match`. Na web nada muda.
- **HTTP sem TLS só no desenvolvimento:** o build de debug do Android tem uma configuração de rede que aceita `http://`, e o Capacitor só libera conteúdo misto quando `MYDLY_API_URL` começa com `http://`. O build de release não tem essa configuração e só fala HTTPS.
- **O resto vem da web:** a fila offline (ADR 0011) e a sessão (ADR 0012) ficam no `localStorage` do WebView, que persiste no aparelho.

### Push

- **Firebase Cloud Messaging, API HTTP v1, para os dois sistemas.** O Firebase entrega no Android e, com a chave do APNs no projeto Firebase, no iOS. No app, o plugin oficial `@capacitor/push-notifications` (8.1.3, fixo) pede a permissão, registra o aparelho e entrega o token.
- **Sem SDK do Firebase no backend:** o envio é um `POST` para `fcm.googleapis.com/v1/projects/{id}/messages:send`. O token de acesso vem do fluxo de conta de serviço do Google: um JWT RS256 assinado com a chave da conta (pelo Nimbus, que já vem com o Spring Security), com o escopo `firebase.messaging`, trocado em `oauth2.googleapis.com/token` e reaproveitado por 50 minutos. A conta de serviço é um arquivo JSON fora do repositório (`FCM_SERVICE_ACCOUNT_FILE`). Sem ele, nada é enviado e o resto do app funciona igual.
- **Token inválido sai da lista:** `UNREGISTERED` (404) e `SENDER_ID_MISMATCH` (403) apagam o aparelho; outras falhas são tentadas de novo na próxima rodada.

### As três notificações

| Notificação | Quando | Uma vez por |
| --- | --- | --- |
| Alerta de orçamento ("Alimentação: 85% do limite da semana") | Tarefa a cada 5 minutos: categoria em aviso (80%) ou acima do limite | categoria, período e nível (o aviso e depois o estouro) |
| Lembrete do dia ("Como foi o seu dia?") | 21h em São Paulo, para quem não lançou nada no dia | dia |
| Resumo da semana (a mensagem principal do Resumo, no tom escolhido) | 20h do último dia da semana do usuário | semana |

- **Tarefa a cada 5 minutos para o orçamento**, e não um evento: o gasto chega por lançamento, extrato e Open Finance, e o orçamento já é calculado por `Budgets`. Uma tarefa olha o resultado, sem acoplar `notifications` a cada fonte.
- **Uma vez só:** `notification_sent` guarda usuário, tipo e assunto, gravado antes do envio. Duas rodadas ou duas instâncias não mandam em dobro. Se nenhum aparelho recebeu por falha do Firebase, a marca é desfeita e a próxima rodada tenta de novo.
- **Só para quem tem aparelho**, e cada tipo pode ser desligado na aba Metas. O tom do resumo vai junto das escolhas, porque é escolhido no aparelho.
- **O módulo `notifications` só lê APIs públicas:** `Budgets` e `BudgetWindow` (finance), `WeekPreferences` e `UserCalendar` (preferences), `EntryActivity`, nova, que diz se o usuário lançou algo num dia (entries), e `WeekHeadline`, nova, com a mensagem principal da semana (insights). Nenhum módulo depende de `notifications`.

### No app

- **Cartão "Notificações" na aba Metas, só no app nativo:** "Ativar notificações" pede a permissão do sistema e registra o aparelho; depois aparecem as três escolhas. "Desativar neste aparelho" tira o aparelho do servidor e do Firebase.
- **Sair da conta tira o aparelho** do servidor antes de encerrar a sessão, e um aparelho registrado por outra pessoa passa a ser dela. Ninguém recebe notificação de outra conta.
- **O token pode mudar:** o aparelho é registrado de novo a cada abertura do app, se a pessoa tinha ativado.
- **Tocar na notificação** abre o Hoje ou o Resumo, conforme o campo `route` da mensagem, que só aceita essas duas rotas.

## Consequências

- O APK de debug compila neste ambiente, com o Android SDK instalado em `C:\Users\gabri\tools\android-sdk`. O projeto iOS foi gerado, mas não compilado: isso exige macOS com Xcode.
- **Push no iOS fica incompleto:** o plugin do Capacitor entrega o token do APNs, e o FCM HTTP v1 precisa do token do Firebase. Falta, num Mac, adicionar o SDK do Firebase ao projeto iOS para trocar um pelo outro, como no guia do Capacitor, e a capacidade Push Notifications no Xcode.
- **Nada foi entregue de verdade:** sem um projeto Firebase, os testes simulam o Google (o JWT é conferido com a chave pública) e o envio. Com o projeto, falta pôr o `google-services.json` em `frontend/android/app/` e o JSON da conta de serviço no backend.
- As notificações levam valores de orçamento para a tela do aparelho da própria pessoa; nada disso vai para log.
- As tarefas agendadas rodam em cada instância do backend; com mais de uma, a marca de enviada evita duplicar, mas as consultas se repetem. Aceitável para o portfólio.
