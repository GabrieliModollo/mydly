# ADR 0018 — Cadastro de conta pela API, com entrada imediata

- **Status:** Aceita
- **Data:** 2026-11-08
- **Complementa:** [ADR 0012](0012-login-com-jwt-e-dados-por-usuario.md)

## Contexto

O ADR 0012 separou os dados por usuário, mas deixou o cadastro de fora: só existe o usuário `demo`, criado pela migração, e os dados dele são compartilhados por quem visita. A etapa 19 do backlog ("Contas de verdade") começa pelo cadastro, para cada pessoa ter os próprios lançamentos, metas e limites.

Era preciso decidir:

- que regras valem para o nome de login e para a senha;
- como a API recusa um cadastro e como o app mostra o motivo;
- se a pessoa entra logo depois de criar a conta;
- onde o cadastro fica no app, e o que acontece com a fila offline de quem estava no aparelho;
- o que fica para depois.

## Decisão

### API

- **`POST /api/v1/auth/register`**, aberto como o login, com `{username, password}`. Responde **201 com a mesma resposta do login** (token, validade e usuário): a pessoa já entra, sem digitar a senha de novo e sem um segundo pedido.
- **Fica no módulo `accounts`**, num `RegistrationService` próprio, que usa o mesmo `PasswordEncoder` (BCrypt) e a mesma emissão de token do `LoginService`. O controlador do cadastro é separado do login, para os dois mudarem sem um atrapalhar o outro.
- **Sem migração nova.** A tabela `app_user` da V11 já tem o nome único e em minúsculas; o cadastro só insere nela. Os dados já têm dono (ADR 0012), então uma conta nova começa vazia e não vê nada de ninguém.

### Regras

- **Usuário:** de 3 a 40 caracteres entre letras sem acento, números, ponto, hífen e sublinhado, começando por letra ou número. É guardado sem espaços nas pontas e em minúsculas, como o login já procura: `Ana` e `ana` são a mesma conta. Sem acento e sem espaço, o nome não tem duas formas Unicode nem confunde na hora de digitar.
- **Senha:** pelo menos 8 caracteres (contados como letras, não bytes) e no máximo 72 bytes em UTF-8. O BCrypt só usa os primeiros 72 bytes; uma senha maior seria cortada sem aviso, e o Spring Security recusa codificá-la. Não há regra de composição (maiúscula, símbolo): o tamanho mínimo pesa mais, como recomenda o NIST SP 800-63B.
- **Nome já usado:** 409. A consulta antes de inserir dá a resposta comum; a restrição única do banco resolve dois cadastros simultâneos com o mesmo nome, e a exceção vira o mesmo 409.
- **As regras são conferidas no serviço, não com Bean Validation**, para toda recusa sair no mesmo formato e com um motivo que o app entende.

### Recusas

- **Problema RFC 9457** (o `ProblemDetail` que a API já usa), com a mensagem em `detail` e o motivo em `code`: `INVALID_USERNAME` e `PASSWORD_TOO_SHORT` ou `PASSWORD_TOO_LONG` (400) e `USERNAME_TAKEN` (409).
- **O app traduz o `code`**, não o texto: a mensagem pode mudar sem quebrar o app, e o campo com problema é marcado com `aria-invalid`.
- **O cadastro revela que um nome existe.** É inevitável quando o nome é escolhido pela pessoa: ela precisa saber que deve escolher outro. O login continua sem revelar (ADR 0012).

### App

- **Tela própria em `/cadastro`**, com a mesma aparência do login e um link de ida e volta entre as duas. Separada da tela de login para cada uma ter um só formulário e um só caminho de erro.
- **Campos com rótulo:** usuário, senha e "Repita a senha", com as regras ligadas aos campos por `aria-describedby`. A confirmação existe porque ainda não há recuperação de senha: um erro de digitação perderia a conta.
- **O app confere as mesmas regras antes de enviar**, em funções puras com teste, e põe o foco no campo com problema. A API confere de novo; as duas listas de regras andam juntas.
- **Mensagens:** usuário já existe, usuário inválido, senha curta ou longa, senhas diferentes, sem conexão e muitas tentativas (429, para quando houver limite também no cadastro).
- **Entrada imediata:** a resposta do cadastro começa a sessão como a do login. Como a fila offline é de um usuário por vez (ADR 0012), entrar com a conta nova descarta a fila de quem estava antes no aparelho, do mesmo jeito que entrar com outra conta.
- **O interceptador não põe token no cadastro**, como no login.

### Fora desta etapa

Continuam na etapa 19: troca e recuperação de senha, refresh token curto com revogação, fuso horário por usuário, consentimento explícito no cadastro com a política de privacidade, exportação e exclusão de conta (LGPD) e limite de cadastros por origem.

## Consequências

- Qualquer pessoa cria uma conta e tem os próprios dados; o `RegistrationIntegrationTests` cobre o cadastro com o filtro de segurança de verdade e confere que duas contas cadastradas não veem os lançamentos, as transações, os hábitos, as metas e os limites uma da outra.
- Com o cadastro aberto, alguém pode criar contas em massa. Até existir limite por origem, o custo é só espaço no banco: uma conta vazia não chama o LLM nem integrações.
- Sem recuperação de senha, quem esquece a senha perde a conta. A confirmação na tela evita o erro de digitação no cadastro, mas a recuperação precisa de um canal (e-mail) que o MyDly ainda não tem.
- Mudar uma regra de usuário ou de senha exige mudar a API e o app juntos; o app se defende de um `code` desconhecido com a mensagem genérica.
