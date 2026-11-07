# Design do MyDly

Referência visual do app, extraída do protótipo navegável (canvas no claude.ai, acessível à autora). Esta é a fonte para implementar as telas no Angular + Ionic.

## Identidade

O MyDly é direto e um pouco bem-humorado: fundo claro e calmo, texto quase preto, e um verde-limão que marca as ações e as conquistas. Nada de gradientes, sombras pesadas ou emojis.

### Cores

| Token | Valor | Uso |
| --- | --- | --- |
| `--mydly-bg` | `#F5F4EF` | Fundo das telas |
| `--mydly-surface` | `#FFFFFF` | Cartões, campo de lançamento |
| `--mydly-ink` | `#15171C` | Texto principal, cartão de destaque escuro |
| `--mydly-muted` | `#5B5F6A` | Texto secundário, rótulos |
| `--mydly-subtle` | `#3F434C` | Texto de apoio sobre superfícies claras |
| `--mydly-line` | `#E4E2DA` | Divisórias |
| `--mydly-accent` | `#D4F25C` | Botão de enviar, ação principal, sequência de dias, insights |
| `--mydly-accent-ink` | `#15171C` | Texto sobre o acento |

### Chips de interpretação

Cada tipo tem fundo e texto próprios; as cores diferem também em luminosidade, e o rótulo sempre diz o tipo por escrito (a cor nunca é a única pista).

| Chip | Fundo | Texto | Rótulo |
| --- | --- | --- | --- |
| Saída | `#FFEDD5` | `#9A3412` | `Saída · R$ 50,00` |
| Entrada | `#DBEAFE` | `#1E40AF` | `Entrada · R$ 100,00` |
| Rotina | `#CCFBF1` | `#115E59` | `Hidratação · 2 L` ou `Evitou besteira` quando cai num hábito do catálogo; `Rotina · 2 L` ou `Rotina` quando é "outros" |
| Rotina não cumprida | `#E4E2DA` | `#2F323A` | `Rotina · não cumprido` ou `Academia · não cumprido` (texto riscado) |
| Deslize (hábito ruim consumido) | `#FFE4E6` | `#9F1239` | `Deslize · Energético` (6,7:1; no tema escuro `#4C0519` e `#FDA4AF`, 8,3:1) |
| Precisa confirmar | `#EDE9FE` | `#5B21B6` | `Precisa confirmar` |
| Categoria / hábito | `#F1F0EA` | `#3F434C` | `Farmácia`, `Hidratação` |

### Tipografia

- **Títulos e números**: Space Grotesk 700 (títulos de tela 32 px; valores 22 px).
- **Texto**: Inter 400/500/600 (lançamento 15 px; rótulos 12–13 px; chips 12 px 600).
- Moeda e datas em pt-BR (`R$ 1.250,90`, "Quinta, 5 de novembro").

### Forma

- Cartões com cantos de 18 px (telas) e 22 px (resumo); chips e botões em pílula.
- Espaçamento lateral de 20 px; 10 px entre cartões.
- Alvos de toque com no mínimo 44 px.

## Telas

### Hoje

1. Cabeçalho: data, título "Hoje" e pílula escura com a sequência ("6 dias", texto no acento).
2. Dois cartões de resumo, vindos do `GET /api/v1/days/{date}/summary`: "Gasto hoje" (soma das saídas que valem) e "Hábitos cumpridos" (hábitos bons cumpridos mais hábitos ruins evitados).
3. Lista do dia, mais recente primeiro: texto original do lançamento, horário à direita e uma linha de chips por item. Um item pendente mostra o link "Quanto foi? →". Um item aceito mostra a ação discreta "Não foi isso" (cinza, peso 500), que o tira dos números e abre o painel para corrigir; um item descartado mostra "Desfazer".
4. Campo de lançamento fixo embaixo, estilo chat ("O que aconteceu?"), com botão redondo no acento; envio também com Enter.
5. Barra de navegação: Hoje e Resumo (Metas entra quando a tela existir).

### Confirmar lançamento (painel inferior sobre "Hoje")

Chip "Precisa confirmar", título "Quanto foi?", a frase original, campo de valor grande com "R$", valores rápidos, categoria sugerida em pílulas selecionáveis, botão principal "Salvar gasto" (acento) e ação secundária "Não foi um gasto".

### Resumo da semana

1. Cabeçalho com o período ("2 a 8 de novembro"), setas para a semana anterior e a próxima (a próxima fica desativada na semana atual) e o título "Sua semana" ou, nas anteriores, "Resumo da semana".
2. Cartão escuro (`--mydly-hero-bg`, texto `--mydly-hero-text`; `#15171C` no claro e `#2C2F36` no escuro, contraste acima de 11:1) com o seletor de tom (Gentil / Direto / Bronca, o escolhido no acento) e a mensagem principal. O tom fica guardado no aparelho.
3. Cartão no acento com o cruzamento de sono e gastos, quando ele existe e não é a mensagem principal; as outras mensagens vêm em cartões simples.
4. Hábitos: uma linha por hábito com 7 pontos sob as iniciais dos dias ("S T Q Q S S D" quando a semana começa na segunda; as iniciais seguem o dia de início configurado) e o placar "cumpridos/dias decorridos". Cumprido (ou ruim evitado) é ponto cheio verde-água; deslize é ponto cheio rosa; não cumprido é só contorno; sem registro e dias que não chegaram têm contorno tracejado. Cada linha tem uma descrição para leitor de tela ("Hidratação, 2 de 3 dias. segunda: cumprido...").
5. Gastos por categoria com barras horizontais proporcionais à maior categoria, total, entradas e a linha da semana anterior, comparada nos mesmos dias ("Semana anterior, até quarta: ...").
6. A barra de navegação inferior tem Hoje e Resumo; Metas entra quando a tela existir.

## Acessibilidade

- Contraste mínimo de 4,5:1 em todo texto (as cores acima já passam).
- Botões de ícone com `aria-label`; campo de lançamento com `label` (pode ser visualmente oculto).
- Navegação completa por teclado.
