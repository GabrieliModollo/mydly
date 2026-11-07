# ADR 0012 — Login com JWT emitido pela própria API e dados separados por usuário

- **Status:** Aceita
- **Data:** 2026-11-07
- **Complementa:** [ADR 0002](0002-monolito-modular.md), [ADR 0008](0008-eventos-com-registro-e-revisao-por-item.md), [ADR 0010](0010-metas-e-orcamentos-versionados.md) e [ADR 0011](0011-fila-offline-no-aparelho.md)

## Contexto

Até a etapa 8, a API não tinha usuário: qualquer pedido lia e mudava todos os dados, e as preferências eram uma linha só. Isso impedia publicar uma demonstração, porque cada visitante veria e mudaria os dados dos outros. A etapa 9 do backlog pede Spring Security com JWT, a coluna de usuário nos dados e um usuário `demo` com seed.

Era preciso decidir:

- quem emite e confere o token, e com que biblioteca;
- onde fica o conceito de usuário no monólito modular, e como os outros módulos descobrem de quem é um pedido;
- como os dados que já existiam ganham dono;
- como o app guarda o token e convive com a fila offline (ADR 0011);
- o que fica de fora desta etapa.

## Decisão

### Token

- **A própria API emite e confere o token**, um JWT assinado com **HS256**. Quem assina e quem confere é o mesmo processo, então um segredo simétrico basta, sem chave pública nem servidor de autorização separado.
- **Bibliotecas:** `spring-boot-starter-security` e `spring-boot-starter-oauth2-resource-server`, que traz o Nimbus JOSE para assinar e conferir. Nenhuma biblioteca de JWT de terceiros.
- **Conteúdo:** `sub` com o id do usuário, `preferred_username`, `iss = mydly`, `iat` e `exp`. A API confere a assinatura, o emissor e a validade, com o relógio da aplicação.
- **Validade de 7 dias e sem refresh token.** O app guarda o token para funcionar offline (abaixo), e 7 dias cobrem uma semana sem internet. Refresh token e revogação ficam para quando houver contas de verdade.
- **Segredo:** `MYDLY_JWT_SECRET`, com pelo menos 32 bytes; mais curto, a aplicação não sobe. Vazio, a API gera um segredo aleatório a cada subida e avisa no log, o que é prático no desenvolvimento e invalida os tokens a cada reinício.
- **Sem sessão e sem cookie:** o token vai no header `Authorization: Bearer`. Sem cookie, não há CSRF a proteger, e a proteção fica desligada.
- **Rotas abertas:** só `POST /api/v1/auth/login`, a saúde (`/actuator/health`) e o Swagger. Todo o resto responde 401 sem token válido. O Swagger documenta o esquema `bearer`, e o botão Authorize funciona.
- **Senha:** só o hash BCrypt fica no banco. Usuário inexistente e senha errada dão a mesma resposta (401, "Usuário ou senha incorretos.") e o mesmo tempo de resposta: quando o usuário não existe, a API confere a senha contra um hash qualquer. Nada registra em log o nome de login nem a senha.

### Módulo `accounts` e o id do usuário

- **Módulo novo `accounts`**, folha (não depende de ninguém): a tabela `app_user`, o login, a configuração de segurança e a API pública `CurrentUser`, que lê o `sub` do token validado.
- **Só os controladores chamam `CurrentUser`.** Daí para baixo o id do usuário vai como **parâmetro explícito** em todo serviço e em toda API pública dos módulos (`FinanceTotals.on(userId, dia, fuso)`, `WeekPreferences.firstDayOfWeek(userId)` etc.). Ler o usuário do contexto de segurança dentro dos serviços seria menos código, mas quebraria os eventos reenviados e as tarefas agendadas, que rodam sem pedido HTTP, e esconderia a dependência.
- **Os eventos levam o dono:** `EntryItemAccepted` e `EntryItemRetracted` ganham `userId`, e `finance` e `routines` gravam a coluna a partir do evento.

### Dados

- **Coluna `user_id` em toda tabela de dados do usuário:** `entry`, `finance_transaction`, `habit_log`, `habit_goal`, `budget` e `preferences`. Os índices por data passam a ser `(user_id, occurred_at)`. As chaves das versões passam a ser `(user_id, habit_code, valid_from)` e `(user_id, category, valid_from)`, e `preferences` passa a ter uma linha por usuário (chave `user_id`); quem não tem linha fica com a semana começando na segunda.
- **Sem chave estrangeira para `app_user`**, como já não há entre `finance_transaction` e `entry`: tabelas de módulos diferentes não se amarram no banco (ADR 0002).
- **O que existia é do usuário demo.** A migração V11 cria `app_user` com o usuário `demo` (id fixo, senha pública `demo`), e a V12 dá a ele os dados antigos. Os eventos ainda não concluídos no registro de publicação recebem o `userId` do demo na mesma migração, para o reenvio não falhar.
- **O lançamento de outro usuário não existe.** Ler, listar e resolver filtram pelo dono e respondem 404 para o resto. Como o id do lançamento é gerado no app (ADR 0006), um id que já é de outro usuário dá 409, sem devolver nada do lançamento dele.

### App

- **Tela de login** com usuário e senha e o botão "Entrar na demonstração", que usa o usuário público. Ainda não há cadastro.
- **A sessão (token, validade e usuário) fica no `localStorage`**, junto da fila offline. É o que deixa o app abrir logado e lançar sem internet. O risco é um script injetado na página ler o token; a defesa é não ter XSS (o Angular escapa o que mostra, e o app não usa HTML vindo de fora) e o prazo de 7 dias. Um cookie `HttpOnly` protegeria melhor, mas traria CSRF de volta e não combina com o app nativo da etapa 13.
- **Um interceptador** põe o token em todo pedido à API, menos no login. Um 401 encerra a sessão e leva ao login. As telas do app têm uma guarda que exige sessão válida.
- **A fila offline é de um usuário por vez.** Cada lançamento guardado leva o `userId` do dono. Sem sessão, ou com 401, o lançamento fica guardado (não é recusa) e a fila espera o login. Entrar com outro usuário descarta a fila do anterior. "Sair" apaga a fila de quem sai, e antes pede confirmação se há lançamentos não enviados, como o ADR 0011 previa para a LGPD.

### Fora desta etapa

Cadastro pela API, troca e recuperação de senha, refresh token, limite de tentativas de login, restaurar a demonstração todo dia e fuso por usuário. Ficam como ideias no backlog.

## Consequências

- Cada pessoa vê só os seus lançamentos, números, metas, limites e preferências. O teste `UserIsolationIntegrationTests` cobre os quatro caminhos com dois usuários, e o `AuthIntegrationTests` cobre o token de verdade (assinatura, emissor, validade e as rotas abertas).
- Os testes da API autenticam pelo `jwt()` do Spring Security Test: todo pedido do MockMvc sai como o usuário demo, e um teste escolhe outro usuário quando precisa.
- Os dados da demonstração são compartilhados por quem visita. A tela de login avisa para não escrever nada pessoal.
- Trocar o segredo invalida todos os tokens: todo mundo entra de novo, e a fila offline espera o login sem perder nada.
- Com um token roubado, alguém usa a conta até o token vencer, porque não há revogação. Aceitável para uma demonstração; contas reais pedem refresh token curto e revogação.
