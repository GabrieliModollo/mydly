# ADR 0014 — Importação de extrato OFX e CSV, com reconciliação nos dois sentidos

- **Status:** Aceita
- **Data:** 2026-11-07
- **Complementa:** [ADR 0005](0005-integracoes-porta-adaptador.md) e [ADR 0008](0008-eventos-com-registro-e-revisao-por-item.md)

## Contexto

O ADR 0005 decidiu que transações de fora do app entram por uma porta `TransactionSource`, no módulo `integrations`, e passam por reconciliação: se a pessoa escreveu "50 reais farmácia" e o banco trouxe "DROGASIL R$ 50,00", as duas viram uma só. A etapa 11 traz a primeira fonte: o extrato que a pessoa exporta do banco, em OFX ou CSV.

Era preciso decidir:

- quais formatos ler e como não duplicar ao importar o mesmo arquivo de novo;
- onde ficam as transações importadas e como elas entram nos números;
- a regra de reconciliação, e o que acontece quando o lançamento é escrito depois da importação ou retirado com "Não foi isso";
- como os módulos conversam sem criar um ciclo.

## Decisão

### Leitura

- **OFX 1.x (SGML) e 2.x (XML)**, de conta e de cartão, sem biblioteca: só os campos usados (`DTPOSTED`, `TRNAMT`, `FITID`, `MEMO` ou `NAME`, e a conta em `ACCTID`). Valor com vírgula ou ponto decimal.
- **CSV com cabeçalho**, com as colunas achadas pelo nome (data, valor, descrição e, se houver, um identificador), separador vírgula, ponto e vírgula ou tabulação, datas `dd/MM/aaaa` ou `aaaa-MM-dd` e valores no formato brasileiro. Valor negativo é saída. A exceção é a fatura de cartão no formato `date,title,amount`, em que a compra vem positiva.
- **Texto em UTF-8 ou Windows-1252**, que muitos bancos ainda usam.
- **Limites:** 2 MB e 5.000 transações por arquivo. Linha inválida recusa o arquivo inteiro com o número da linha, sem mostrar o conteúdo dela. Valor zero é ignorado.
- **Id externo estável:** a conta e o `FITID` no OFX, o identificador da linha no CSV, ou um hash de data, valor e descrição quando não há id. Duas linhas iguais no mesmo arquivo continuam sendo duas (`#2`). A tabela tem chave única por usuário, origem e id externo: reimportar o mesmo arquivo, ou um período que se sobrepõe, não duplica nada.

### Onde ficam e como contam

- **`integrations` guarda cada transação importada** em `external_transaction`, com dono, data, valor, direção, a descrição do banco (até 200 caracteres) e um status: `IMPORTED` (conta nos números), `MATCHED` (é o mesmo de um lançamento manual e não conta de novo) ou `IGNORED` (a pessoa pediu para não contar, como numa transferência entre contas dela).
- **Só a `IMPORTED` entra nos números**, como uma linha de `finance_transaction` com `source = STATEMENT_IMPORT`, o id da transação externa em `entry_id` e `item_index` zero, ao meio-dia do dia dela no fuso do usuário. Assim os cartões, o resumo da semana e os orçamentos contam a transação sem mudar nada em `insights`. A categoria fica vazia.
- **`integrations` chama a API pública de `finance`** (`ExternalTransactions`) na mesma transação do banco: a importação inteira entra ou nada entra. `finance` não conhece `integrations`, e não há ciclo entre os módulos. A mudança de "conta" para "não conta" segue a regra de revisão e lápide do ADR 0008: a linha nunca é apagada, e só uma revisão maior a muda.

### Reconciliação

- **Mesmo gasto** quando o usuário, a direção e o valor exato são iguais e a data do banco vai de **um dia antes a três dias depois** da data do lançamento: a compra no cartão costuma aparecer no extrato um ou dois dias depois, e um lançamento perto da meia-noite pode cair no dia seguinte no banco. Com mais de um candidato, vale o de data mais próxima e, no empate, o mais antigo. Cada lançamento casa com uma transação só (índice único).
- **Na importação:** cada linha procura um lançamento manual livre. Se achar, fica `MATCHED` e não entra nos números de novo.
- **Lançamento escrito depois:** `integrations` escuta o `EntryItemAccepted` (ADR 0008). Um gasto ou entrada com valor procura uma transação `IMPORTED` na janela e casa com ela, que sai dos números.
- **"Não foi isso" ou correção do valor:** no `EntryItemRetracted`, ou num `EntryItemAccepted` com outro valor, a ligação é desfeita. A transação procura outro lançamento livre ou volta a contar sozinha.
- **"Não contar" e "Contar de novo"** pela API (`PUT /api/v1/imports/transactions/{id}`). Contar de novo também procura um lançamento para casar.

### App

- **Tela Extrato**, aberta pela aba Metas: escolhe o arquivo, importa, mostra o resumo ("5 transações lidas: 2 entraram nos seus números, 2 já estavam lançadas e não contam duas vezes e 1 já tinha sido importada.") e lista os últimos 60 dias, com o que cada transação faz nos números e o botão "Não contar" ou "Contar de novo".

## Consequências

- Gastos do cartão e da conta entram nos números sem a pessoa digitar, e o que ela já lançou não conta duas vezes.
- Uma transação importada sem lançamento aparece nos cartões e no resumo, mas não na lista de lançamentos do Hoje, que mostra o que a pessoa escreveu. A tela Extrato é onde ela aparece.
- **Falso positivo:** dois gastos diferentes com o mesmo valor em dias próximos (o café de R$ 5 de todo dia) podem casar com a transação errada. O total continua certo, porque cada lado conta uma vez só; o que pode ficar trocado é qual lançamento está ligado a qual linha do extrato.
- **Eventos fora de ordem:** se um `EntryItemAccepted` antigo for reenviado depois de um `EntryItemRetracted` mais novo, a ligação pode ser refeita para um lançamento já retirado. O registro de publicação reenvia na ordem em quase todos os casos; guardar a revisão do item na ligação fica para quando isso aparecer.
- A descrição do banco é dado financeiro: fica só na tabela da pessoa, nunca vai para log nem para o LLM.
- O Open Finance (etapa 12) entra como outro adaptador da mesma porta, com `source = OPEN_FINANCE`, e reaproveita a importação e a reconciliação.
