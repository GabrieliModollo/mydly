# ADR 0004 — Stack tecnológica

- **Status:** Aceita
- **Data:** 2026-11-05

## Contexto

O projeto precisa de um backend com autenticação e jobs agendados, um banco relacional e um app que rode na web, no Android e no iOS.

## Decisão

| Camada | Escolha |
| --- | --- |
| Backend | Java 21 + Spring Boot, Spring Modulith, Spring Security (JWT) |
| Banco | PostgreSQL (JSONB para o resultado do parser), migrações com Flyway |
| Testes | JUnit 5, testes parametrizados, Testcontainers, JaCoCo |
| Front web + mobile | Angular + Ionic/Capacitor |
| Infra local | Docker Compose |
| CI | GitHub Actions, com Dependabot para dependências |

### Por que Angular + Ionic/Capacitor e não Flutter

- Um único código para web, Android e iOS.
- Aproveita o ecossistema Angular (TypeScript, RxJS, injeção de dependência), próximo do estilo do backend Spring.
- Flutter traria UI nativa mais performática, mas exigiria Dart e não entregaria a mesma experiência web.

## Consequências

- Backend e front seguem padrões parecidos de organização (módulos, serviços, injeção de dependência).
- Funcionalidades nativas (notificações push, armazenamento offline) vêm de plugins do Capacitor.
- A performance de animações complexas fica abaixo de um app nativo, o que não é crítico para o MyDly.
