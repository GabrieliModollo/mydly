# ADR 0002 — Monólito modular em vez de microsserviços

- **Status:** Aceita
- **Data:** 2026-11-05

## Contexto

O sistema tem domínios bem distintos (lançamentos, finanças, rotinas, insights, integrações, notificações), mas nasce pequeno, com um único desenvolvedor e sem necessidade de escalar partes de forma independente.

## Decisão

Um único deploy Spring Boot, organizado em módulos com fronteiras explícitas (Spring Modulith). Módulos se comunicam por eventos de domínio e não acessam as tabelas uns dos outros. As fronteiras são verificadas por teste.

## Consequências

- Deploy, observabilidade e transações simples.
- Fronteiras claras permitem extrair um módulo como serviço no futuro.
- Exige cuidado para não criar dependências diretas entre módulos; o teste de verificação do Modulith ajuda nisso.
