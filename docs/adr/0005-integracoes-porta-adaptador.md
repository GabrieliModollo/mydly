# ADR 0005 — Fontes de transação via porta/adaptador

- **Status:** Aceita
- **Data:** 2026-11-05

## Contexto

Transações podem vir de vários lugares: digitação manual, importação de extrato e Open Finance. O acesso direto ao Open Finance Brasil exige autorização do Banco Central, então o caminho é um agregador regulado, cujos planos pagos não fazem sentido para um projeto de portfólio. Os agregadores oferecem sandbox com dados fictícios.

## Decisão

O módulo `integrations` define uma porta `TransactionSource`, com adaptadores:

- `ManualSource` — lançamentos digitados;
- `StatementImportSource` — arquivos OFX/CSV;
- `OpenFinanceSource` — agregador, usando apenas o sandbox.

Toda transação externa passa por **reconciliação**: se o usuário digitou "50 reais farmácia" e o banco trouxe "DROGASIL R$ 50,00" na mesma data, o sistema vincula as duas em vez de duplicar.

## Consequências

- Uma nova fonte entra sem mexer em `finance` nem em `insights`.
- A integração com Open Finance é real, mas demonstrativa.
- A reconciliação é a parte mais delicada e terá testes próprios.
