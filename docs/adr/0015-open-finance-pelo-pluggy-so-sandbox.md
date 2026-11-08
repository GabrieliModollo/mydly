# ADR 0015 — Open Finance pelo Pluggy, só no sandbox

- **Status:** Aceita
- **Data:** 2026-11-07
- **Complementa:** [ADR 0005](0005-integracoes-porta-adaptador.md) e [ADR 0014](0014-importacao-de-extrato-e-reconciliacao.md)

## Contexto

O ADR 0005 decidiu ligar o Open Finance por um agregador regulado, só no sandbox: acessar o Open Finance Brasil direto exige autorização do Banco Central, e os planos pagos dos agregadores não fazem sentido num projeto de portfólio. A etapa 11 criou a porta `TransactionSource`, a importação com id externo único e a reconciliação com os lançamentos manuais. A etapa 12 traz o segundo adaptador.

Era preciso decidir:

- qual agregador;
- como o usuário conecta o banco sem que o segredo do agregador vá para o app;
- como garantir que só bancos de teste entram;
- quando sincronizar e o que fazer ao desconectar.

## Decisão

### Agregador

- **Pluggy**, escolhido pela autora: brasileiro, com sandbox gratuito (o banco de teste "Pluggy Bank") e um widget pronto, o Pluggy Connect. A API foi lida na documentação oficial e nos tipos do SDK oficial (`pluggy-sdk`); a chamada de verdade depende das chaves do time da autora, que ainda não existem neste ambiente.
- **Chaves só no servidor:** `OPEN_FINANCE_CLIENT_ID` e `OPEN_FINANCE_CLIENT_SECRET`. Vazias, o Open Finance fica desligado (`GET /api/v1/open-finance` responde `enabled: false`, e o app esconde o cartão). O resto do app não muda.
- **Cliente HTTP próprio, com o `RestClient` do Spring**, em vez de um SDK Java de terceiros: são cinco chamadas (`POST /auth`, `POST /connect_token`, `GET` e `DELETE /items/{id}`, `GET /accounts` e `GET /v2/transactions`). A chave de API (`X-API-KEY`) vale 2 horas e é reaproveitada por 100 minutos. As transações vêm de `/v2/transactions`, com cursor: o `next` é anexado ao caminho como veio, sem montar nem decodificar o cursor, como a documentação pede. A versão antiga, com páginas, deixa de existir em 2026-12-31.

### Conectar

1. O app pede ao servidor um **connect token** (`POST /api/v1/open-finance/connect-token`), válido por 30 minutos e criado com `clientUserId` igual ao id do usuário no MyDly.
2. O app abre o **Pluggy Connect** (`pluggy-connect-sdk` 2.14.2, carregado só nessa hora) com `includeSandbox: true`, em português. As credenciais do banco vão do widget direto para o Pluggy: o MyDly nunca as vê.
3. O widget devolve o id do **item** criado, e o app o manda ao servidor (`POST /api/v1/open-finance/connections`).
4. O servidor lê o item no Pluggy e **recusa**:
   - o item criado para outro usuário (`clientUserId` diferente);
   - o item cujo conector não é de sandbox (`connector.isSandbox` diferente de `true`). Esse item também é apagado no Pluggy, para os dados de um banco real não ficarem guardados lá.
   - o item já ligado a outra conta do MyDly (chave única).
5. Com o item aceito, a conexão é gravada em `open_finance_connection` e as transações são importadas.

### Sincronizar

- **Mesma importação e reconciliação do extrato** (ADR 0014), com `source = OPEN_FINANCE` e id externo `pluggy:<id da transação>`. Sincronizar de novo não duplica nada, e o que já foi lançado no app não conta duas vezes.
- **Janela:** 90 dias na primeira vez; depois, uma semana antes da última sincronização, porque o banco pode lançar com atraso.
- **Ficam de fora:** transações pendentes (`PENDING`), cujo id pode mudar quando forem lançadas e que voltariam duplicadas; transações em outra moeda sem o valor na moeda da conta; e valor zero.
- **Direção pelo `type`:** `DEBIT` é saída e `CREDIT` é entrada, em conta e em cartão.
- **Dia:** uma data à meia-noite UTC é só um dia e vale como está; com horário, vale o dia no fuso do usuário.
- **Item que não está pronto** (`UPDATING`, `LOGIN_ERROR`, `OUTDATED`...): só o status é guardado, e o app diz o que fazer ("O banco ainda está enviando os dados", "O banco pediu para entrar de novo").
- **Sob demanda:** ao conectar e no botão "Sincronizar". Sem tarefa agendada nem webhook nesta etapa.

### Desconectar

- Apaga o item no Pluggy e a conexão no MyDly. As transações que já entraram ficam, como as de um extrato importado.

## Consequências

- O Open Finance funciona de ponta a ponta sem dado real: o banco de teste do Pluggy gera as transações, e elas passam pela mesma reconciliação do extrato.
- **Não foi verificado contra o Pluggy de verdade:** os testes simulam a API (um servidor HTTP falso para o cliente, e o cliente trocado por um dublê no fluxo da API). Com as chaves do time, falta conectar o Pluggy Bank uma vez e conferir.
- O app ganha uma dependência, o `pluggy-connect-sdk` (MIT, do próprio Pluggy), na versão 2.14.2, fixa: a 2.15.0 saiu um dia antes desta etapa. Ele só é carregado na tela Extrato.
- O id do usuário no MyDly vai para o Pluggy como `clientUserId`. É um id sem significado fora do MyDly, e é o que impede um usuário de ligar o item de outro.
- Sincronização automática (agendada ou por webhook do Pluggy) e o aviso de "reconecte o banco" fora da tela Extrato ficam como ideias no backlog.
