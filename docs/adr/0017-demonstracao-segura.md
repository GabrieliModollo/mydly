# ADR 0017 — Demonstração segura: reset diário, limite de login e cota do LLM

- **Status:** Aceita
- **Data:** 2026-11-08
- **Complementa:** [ADR 0012](0012-login-com-jwt-e-dados-por-usuario.md) e [ADR 0013](0013-llm-de-fallback-com-claude.md)

## Contexto

A etapa 15 vai publicar a demonstração. Quem visitar entra com o usuário `demo`, que é compartilhado (ADR 0012), e três problemas aparecem quando o endereço fica público:

- o que um visitante escreve fica para o próximo, e com o tempo a demonstração vira uma bagunça, ou guarda algo pessoal que alguém escreveu sem ler o aviso;
- o login aceita tentativas sem fim, de graça para quem quer adivinhar uma senha;
- cada frase que as regras não entendem vira uma chamada paga ao LLM (ADR 0013). Um script que mande frases estranhas sem parar gasta a conta da autora.

Além disso, uma demonstração vazia não mostra o que o MyDly tem de melhor: o Resumo da semana, as metas e o cruzamento de sono e gasto só aparecem com alguns dias de dados.

## Decisão

### Reset diário da demonstração

- O usuário `demo` volta ao exemplo ao subir a aplicação e todo dia às 4h de São Paulo, quando `mydly.demo.reset-enabled` (variável `MYDLY_DEMO_RESET`) está ligado. **Desligado por padrão:** o reset apaga tudo o que está no usuário demo, e no computador de quem desenvolve ele é o usuário de teste do dia a dia.
- O módulo `accounts` publica o evento `DemoReset` (usuário e dia de hoje) numa transação. Cada módulo escuta o evento de forma síncrona, na mesma transação, e cuida só das suas tabelas: apaga as linhas daquele usuário e, quando tem, grava a sua parte do exemplo. Nenhum módulo mexe nas tabelas de outro (ADR 0002), e se um falhar a demonstração fica como estava.
  - `entries` apaga os lançamentos e escreve as três semanas antes de hoje, com frases fictícias que passam pelo parser como as de qualquer pessoa. Os eventos dos itens aceitos saem depois do commit, então `finance` e `routines` se preenchem pelo caminho de sempre (ADR 0008), já com as tabelas limpas.
  - `routines` apaga os registros de hábito e grava as metas de exemplo (água 2 L por dia, academia 4 dias por semana, delivery no máximo 2 dias).
  - `finance` apaga as transações e grava os limites de exemplo (Alimentação e Transporte por semana, Lazer por mês).
  - `preferences`, `integrations` e `notifications` só apagam: a semana volta a começar na segunda, os extratos e a conexão de Open Finance somem, e o celular de um visitante deixa de receber os avisos da demonstração.
- O exemplo foi montado para o Resumo da semana anterior mostrar o cruzamento de sono e gasto (ADR 0009): em quaisquer 7 dias seguidos há 3 noites de sono ruim, e nelas o gasto é maior. Um teste confere isso a cada mudança no exemplo. Hoje fica vazio, para quem visita escrever o primeiro lançamento.
- As frases do exemplo são todas entendidas pelas regras, menos "Comprei pão", que fica para o visitante ver a confirmação. Com o LLM ligado, ele é a única frase do exemplo que gasta uma chamada por dia.

### Limite de tentativas de login

- Cinco senhas erradas para o mesmo usuário em 15 minutos bloqueiam o login dele até a janela acabar, e vinte senhas erradas do mesmo endereço de origem bloqueiam o endereço. Usuário inexistente conta como senha errada, para a resposta continuar igual nos dois casos (ADR 0012).
- Bloqueado, o login responde **429** com o header `Retry-After` em segundos, e a senha nem chega a ser conferida. O app mostra "Tente de novo em 15 minutos". O CORS expõe o `Retry-After` para o app nativo.
- O usuário de demonstração não tem bloqueio por usuário, só o por endereço. A senha dele é pública, então o bloqueio não protege nada, e serviria para qualquer pessoa trancar a demonstração para todos os visitantes errando a senha cinco vezes.
- Um login certo esquece as falhas do usuário; as do endereço ficam até a janela acabar.
- As contagens ficam **em memória**. A aplicação roda numa instância só, e reiniciar zera as contagens, o que só favorece quem errou a senha. Com mais de uma instância, a contagem precisaria ir para o banco.
- O endereço de origem é o que o servidor vê. Atrás de um proxy reverso, a etapa 15 precisa ligar o `server.forward-headers-strategy` para o endereço ser o do visitante, e não o do proxy.

### Cota diária do LLM

- Cada usuário pode fazer 30 chamadas ao LLM por dia, e todos os usuários somados, 300 (`mydly.llm.daily-limit`). O dia é o de São Paulo, como o dos lançamentos.
- A reserva acontece só quando o lançamento iria mesmo ao LLM (o LLM está ligado e as regras ficaram em dúvida), numa instrução só na tabela `llm_usage` (usuário, dia, chamadas). Sem vaga, o lançamento fica com o que as regras entenderam e o app pergunta ao usuário, como acontece com o LLM desligado. O visitante não vê erro.
- A cota de cada usuário é exata, porque a linha dele fica travada durante a atualização. A de todos pode passar por uma ou outra chamada quando dois pedidos chegam juntos, o que não muda o custo.
- `llm_usage` guarda só a contagem: nem o texto nem a resposta do LLM (LGPD). O `userId` serve à cota e nunca vai para o LLM.

## Consequências

- A demonstração publicada se arruma sozinha todo dia e já abre com três semanas de dados.
- Ligar o reset no lugar errado apaga os dados do usuário demo. Por isso ele é desligado por padrão e só se liga por variável de ambiente.
- Um ataque de força bruta no login fica lento, mas um atacante com muitos endereços ainda consegue tentar contas diferentes. Para uma demonstração de portfólio, sem contas com dados reais, é o bastante.
- O custo do LLM tem teto: no pior caso, 300 chamadas por dia.
- O parser ganhou `parse(text, userId)`, só para a cota. Os testes que mockam o parser passam a mockar essa versão.
- Cada módulo novo com dados por usuário precisa escutar o `DemoReset`, senão os dados dele sobrevivem ao reset.
