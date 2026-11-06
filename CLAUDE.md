# CLAUDE.md

Contexto para o Claude Code trabalhar neste repositório.

## O projeto

MyDly é um app web e mobile de **controle de rotinas e finanças com lançamento em linguagem natural**. O usuário escreve "gastei 50 reais na farmácia" ou "bebi 2 litros de água" e o sistema decide se é finança ou rotina, estrutura os dados e gera estatísticas, incentivos e broncas.

É um **projeto de portfólio**: a prioridade é código limpo, testado e com decisões documentadas, não escala nem custo.

Leia antes de mudanças relevantes: `README.md`, `docs/arquitetura.md` e os ADRs em `docs/adr/`.

- **O que fazer a seguir**: `docs/backlog.md`, em ordem de prioridade. Ao concluir uma etapa, atualize o backlog no mesmo PR.
- **Visual do app**: `docs/design.md` (cores, tipografia, chips e telas, extraídos do protótipo).

## Estrutura

| Pasta | Conteúdo |
| --- | --- |
| `backend/` | Java 21 + Spring Boot 3.5, Spring Modulith, JPA, Flyway, PostgreSQL |
| `frontend/` | Angular 22 + Ionic 9 + Capacitor 8, componentes standalone e signals, sem zone.js |
| `docs/arquitetura.md` | Visão geral, fluxo de um lançamento, modelo de dados, LGPD |
| `docs/adr/` | Architecture Decision Records |
| `.github/` | CI (GitHub Actions) e Dependabot |

## Comandos

```bash
# subir banco, backend e app em containers (na raiz); app em http://localhost:8100
cp .env.example .env
docker compose up --build -d

# só o banco, para rodar o backend pela IDE ou Maven
docker compose up -d postgres

# backend (dentro de backend/)
mvn spring-boot:run   # sobe a API em http://localhost:8080
mvn verify            # build + testes + cobertura (precisa do Docker rodando)

# frontend (dentro de frontend/)
npm start                    # http://localhost:4200, proxy de /api para localhost:8080
npm test -- --watch=false    # testes unitários (Vitest)
npm run lint                 # ESLint, inclusive acessibilidade dos templates
npm run build
```

- Saúde da API: `GET /actuator/health`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Cobertura: `backend/target/site/jacoco/index.html`
- Documentação dos módulos: `backend/target/spring-modulith-docs`

## Regras de arquitetura

- **Monólito modular** (ADR 0002). Módulos em `com.mydly.<modulo>`: `entries`, `finance`, `routines`, `insights`, `integrations`, `notifications`.
- Um módulo **não acessa classes internas nem tabelas de outro**. Comunicação por eventos de domínio ou pela API pública do módulo (classes no pacote raiz dele). Tipos internos ficam em subpacotes (ex.: `com.mydly.finance.internal`).
- O teste `ModularityTests` verifica as fronteiras; ele precisa continuar passando.
- **Parser híbrido** (ADR 0003): regras locais primeiro, LLM só como fallback, pergunta ao usuário quando a confiança for baixa. O texto original (`entry.raw_text`) nunca é descartado. O app precisa funcionar sem chave de LLM.
- **Integrações via porta/adaptador** (ADR 0005): toda fonte externa de transação implementa `TransactionSource`; Open Finance só pelo sandbox do agregador.
- Schema do banco muda **somente por migração Flyway** (`backend/src/main/resources/db/migration/V<n>__<descricao>.sql`); nunca edite uma migração já enviada ao `main`. O Hibernate roda com `ddl-auto: validate`.

## Convenções

- **Idioma**: documentação, ADRs, mensagens de commit e nomes de testes em **português**; código (classes, métodos, variáveis) em **inglês**.
- **Commits**: Conventional Commits em português, ex.: `feat(entries): reconhece valores com "R$"`, `fix(finance): ...`, `docs: ...`, `test: ...`.
- **Testes**: toda regra do parser ganha caso em teste parametrizado. Testes de integração usam Testcontainers, nunca H2.
- **Decisões novas** de arquitetura viram um ADR (`docs/adr/NNNN-titulo.md`, com Status, Data, Contexto, Decisão, Consequências). ADR aceito não é editado; uma mudança gera um ADR novo que o substitui.
- Atualize o `README.md` e o `docs/arquitetura.md` quando uma mudança alterar o que eles descrevem.
- **Javadoc**: não use vírgula dentro de aspas nem de colchetes sem par (`"1,5"`, `[a, b)`). O Spring Modulith lê os Javadocs com um parser JSON simplificado que quebra com isso, e o `ModularityTests` falha. Para literais, use `{@code ...}`.

### Frontend

- Componentes standalone com `ChangeDetectionStrategy.OnPush` e estado em signals. Não há zone.js.
- Componentes do Ionic importados de `@ionic/angular`, que no Ionic 9 é o ponto de entrada standalone.
- Acesso à API só pelos serviços em `src/app/entries/`, com tipos que espelham os DTOs do backend. Formatação de texto em funções puras, com testes.
- O app gera o id (UUID) e o `occurredAt` de cada lançamento (ADR 0006), e o reenvio usa sempre o mesmo id.
- Cores só pelas variáveis de `src/theme/variables.css`, com contraste AA nos temas claro e escuro.
- Acessibilidade: todo campo tem label, ícones decorativos usam `aria-hidden` e o foco é visível. O lint verifica os templates.

## Privacidade (LGPD)

O app lida com dados financeiros e de saúde (sono, dieta). Nunca registre em log o texto dos lançamentos nem valores, e envie ao LLM só o texto do lançamento, sem dados que identifiquem o usuário.

## Ao trabalhar aqui

- Rode `mvn verify` antes de dar uma tarefa de backend por concluída, e `npm run lint`, `npm test -- --watch=false` e `npm run build` para o frontend.
- Ao fim de cada etapa revisada e com os testes passando, faça commit e push no `main` sem precisar pedir. Separe os commits por assunto.
- Commits só no nome da autora (a configuração do git dela), **sem** a linha `Co-Authored-By` do Claude.
- Nunca reescreva histórico já enviado (force push, rebase de commits publicados) sem pedir.
- Não adicione dependências sem explicar o motivo.
