# Backend

API do MyDly em Java 21 + Spring Boot, organizada como monólito modular com Spring Modulith ([ADR 0002](../docs/adr/0002-monolito-modular.md)).

## Módulos

| Pacote | Responsabilidade |
| --- | --- |
| `com.mydly.entries` | Lançamentos em texto livre e interpretação |
| `com.mydly.finance` | Transações, categorias, orçamentos |
| `com.mydly.routines` | Hábitos, registros diários, metas |
| `com.mydly.insights` | Estatísticas, streaks, mensagens |
| `com.mydly.integrations` | Extrato, Open Finance, reconciliação |
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

Saúde da aplicação: <http://localhost:8080/actuator/health>.

Para subir tudo em containers: `docker compose up --build` na raiz.

## Testes

```bash
mvn verify
```

Os testes de integração usam Testcontainers (PostgreSQL real), então o Docker precisa estar rodando. O relatório de cobertura fica em `target/site/jacoco/index.html`.
