# ADR 0001 — Registrar decisões com ADRs

- **Status:** Aceita
- **Data:** 2026-11-05

## Contexto

O MyDly é um projeto de portfólio. Quem avalia o repositório quer entender não só o código, mas o raciocínio por trás das escolhas.

## Decisão

Toda decisão de arquitetura relevante é registrada como um ADR curto em `docs/adr/`, numerado em sequência, com contexto, decisão e consequências. Um ADR não é editado depois de aceito: se a decisão mudar, um novo ADR a substitui e o antigo passa a "Substituída por ADR XXXX".

## Consequências

- O histórico de decisões fica visível e versionado junto com o código.
- Exige disciplina para escrever o ADR antes ou junto com a mudança.
