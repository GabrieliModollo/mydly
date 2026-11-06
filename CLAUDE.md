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
| `frontend/` | Angular + Ionic/Capacitor (ainda não criado) |
| `docs/arquitetura.md` | Visão geral, fluxo de um lançamento, modelo de dados, LGPD |
| `docs/adr/` | Architecture Decision Records |
| `.github/` | CI (GitHub Actions) e Dependabot |

## Comandos

```bash
# subir banco e backend em containers (na raiz)
cp .env.example .env
docker compose up --build -d

# só o banco, para rodar o backend pela IDE ou Maven
docker compose up -d postgres

# backend (dentro de backend/)
mvn spring-boot:run   # sobe a API em http://localhost:8080
mvn verify            # build + testes + cobertura (precisa do Docker rodando)
```

- Saúde da API: `GET /actuator/health`
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

## Privacidade (LGPD)

O app lida com dados financeiros e de saúde (sono, dieta). Nunca registre em log o texto dos lançamentos nem valores, e envie ao LLM só o texto do lançamento, sem dados que identifiquem o usuário.

## Ao trabalhar aqui

- Rode `mvn verify` antes de dar uma tarefa de backend por concluída.
- Não faça commit nem push sem a autora pedir.
- Não adicione dependências sem explicar o motivo.
