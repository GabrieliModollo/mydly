# ADR 0004 — Stack tecnológica

- **Status:** Proposta (front-end em aberto)
- **Data:** 2026-11-05

## Contexto

O projeto precisa de um backend com autenticação e jobs agendados, um banco relacional e um app que rode na web, no Android e no iOS.

## Decisão

| Camada | Escolha |
| --- | --- |
| Backend | Java + Spring Boot, Spring Modulith, Spring Security (JWT) |
| Banco | PostgreSQL (JSONB para o resultado do parser) |
| Testes | JUnit 5, testes parametrizados, Testcontainers |
| Front web + mobile | **Em aberto:** Angular + Ionic/Capacitor ou Flutter |
| Infra local | Docker Compose |
| CI | GitHub Actions |

### Front-end: opções

- **Angular + Ionic/Capacitor** — um código para web, Android e iOS; aproveita o ecossistema Angular.
- **Flutter** — UI nativa de alta performance, mas exige aprender Dart.

## Consequências

- Backend e testes ficam no ecossistema Java, com ferramentas maduras.
- A escolha do front será fechada antes da criação de `frontend/`, e este ADR passará a "Aceita".
