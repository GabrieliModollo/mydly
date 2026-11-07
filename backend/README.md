# Backend

API do MyDly em Java 21 + Spring Boot, organizada como monólito modular com Spring Modulith ([ADR 0002](../docs/adr/0002-monolito-modular.md)).

## Módulos

| Pacote | Responsabilidade |
| --- | --- |
| `com.mydly.accounts` | Usuários, login e token de acesso (JWT) |
| `com.mydly.entries` | Lançamentos em texto livre e interpretação |
| `com.mydly.finance` | Transações, categorias, orçamentos |
| `com.mydly.routines` | Hábitos, registros diários, metas |
| `com.mydly.preferences` | Preferências do usuário e o calendário dele |
| `com.mydly.insights` | Estatísticas, streaks, mensagens |
| `com.mydly.integrations` | Importação de extrato OFX e CSV (e Open Finance depois) e reconciliação com os lançamentos |
| `com.mydly.notifications` | Push e lembretes |

As fronteiras são verificadas pelo teste `ModularityTests`, que também gera a documentação dos módulos em `target/spring-modulith-docs`.

## Rodando

Requisitos: Java 21, Maven 3.9+ e Docker.

```bash
# banco local
docker compose up -d postgres   # na raiz do repositório

# aplicação
cd backend
mvn spring-boot:run
```

Saúde da aplicação: <http://localhost:8080/actuator/health>. API e exemplos: <http://localhost:8080/swagger-ui.html>.

O parser usa um LLM de fallback (Claude) só quando `LLM_API_KEY` está definida; sem ela, só as regras locais ([ADR 0013](../docs/adr/0013-llm-de-fallback-com-claude.md)). Os testes nunca chamam a API: o cliente é simulado.

A API pede login ([ADR 0012](../docs/adr/0012-login-com-jwt-e-dados-por-usuario.md)). Entre com `POST /api/v1/auth/login` (usuário `demo`, senha `demo`) e mande o `accessToken` no header `Authorization: Bearer`; no Swagger, pelo botão Authorize. Sem `MYDLY_JWT_SECRET`, a API gera um segredo a cada subida, e os tokens deixam de valer quando ela reinicia.

Para subir tudo em containers: `docker compose up --build` na raiz.

## Testes

```bash
mvn verify
```

Os testes de integração usam Testcontainers (PostgreSQL real), então o Docker precisa estar rodando. Os pedidos do MockMvc saem autenticados como o usuário demo (`TestSecurityConfiguration`); um teste escolhe outro usuário com `.with(TestUsers.as(id))`. O relatório de cobertura fica em `target/site/jacoco/index.html`.
