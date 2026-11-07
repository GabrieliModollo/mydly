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
2. Dois cartões de resumo, vindos do `GET /api/v1/days/{date}/summary`: "Gasto hoje" (soma das saídas que valem) e "Hábitos cumpridos" (hábitos bons cumpridos mais hábitos ruins evitados). Quando há metas diárias, o cartão de hábitos lista cada uma com o nome e o progresso por escrito ("Hidratação" e "1,5 / 2 L", 12 px) e uma barra fina verde-água (`--mydly-routine-text` sobre `--mydly-line`, 6 px), com `role="progressbar"` e o texto "1,5 de 2 L" para leitor de tela.
3. Alerta de orçamento, logo abaixo dos cartões, quando uma categoria chega a 80% do limite no período ("Alimentação: 85% do limite da semana (R$ 85,00 de R$ 100,00)"). É discreto: pílula larga com o fundo e o texto neutros (`--mydly-neutral-bg` e `--mydly-neutral-text`), 13 px, com um ícone de alerta decorativo; acima do limite ganha o laranja das saídas ("Lazer passou do limite do mês: ..."). A região tem `role="status"` e existe mesmo vazia, para o leitor de tela anunciar o alerta quando ele aparece, sem roubar o foco.
4. Lista do dia, mais recente primeiro: texto original do lançamento, horário à direita e uma linha de chips por item. Um item pendente mostra o link "Quanto foi? →". Um item aceito mostra a ação discreta "Não foi isso" (cinza, peso 500), que o tira dos números e abre o painel para corrigir; um item descartado mostra "Desfazer".
5. Campo de lançamento fixo embaixo, estilo chat ("O que aconteceu?"), com botão redondo no acento; envio também com Enter.
6. Barra de navegação: Hoje, Resumo e Metas.

### Confirmar lançamento (painel inferior sobre "Hoje")

Chip "Precisa confirmar", título "Quanto foi?", a frase original, campo de valor grande com "R$", valores rápidos, categoria sugerida em pílulas selecionáveis, botão principal "Salvar gasto" (acento) e ação secundária "Não foi um gasto".

### Resumo da semana

1. Cabeçalho com o período ("2 a 8 de novembro"), setas para a semana anterior e a próxima (a próxima fica desativada na semana atual) e o título "Sua semana" ou, nas anteriores, "Resumo da semana".
2. Cartão escuro (`--mydly-hero-bg`, texto `--mydly-hero-text`; `#15171C` no claro e `#2C2F36` no escuro, contraste acima de 11:1) com o seletor de tom (Gentil / Direto / Bronca, o escolhido no acento) e a mensagem principal. O tom fica guardado no aparelho. As mensagens de orçamento e de meta (estourado, em risco, perto do limite, cumprida) entram na prioridade do [ADR 0010](adr/0010-metas-e-orcamentos-versionados.md).
3. Cartão no acento com o cruzamento de sono e gastos, quando ele existe e não é a mensagem principal; as outras mensagens vêm em cartões simples.
4. Hábitos: uma linha por hábito com 7 pontos sob as iniciais dos dias ("S T Q Q S S D" quando a semana começa na segunda; as iniciais seguem o dia de início configurado) e o placar "cumpridos/dias decorridos". Com meta, o placar contra ela aparece sob o nome do hábito, em 12 px no `--mydly-muted`: "Meta: 3 de 4 dias", "Meta diária: 2 de 7 dias" ou, num hábito para evitar, "Limite: 1 de no máximo 2 dias". Um hábito com meta aparece mesmo sem registro na semana. Cumprido (ou ruim evitado) é ponto cheio verde-água; deslize é ponto cheio rosa; não cumprido é só contorno; sem registro e dias que não chegaram têm contorno tracejado. Cada linha tem uma descrição para leitor de tela ("Hidratação, 2 de 3 dias. Meta diária: 2 de 7 dias. segunda: cumprido...").
5. Gastos por categoria com barras horizontais proporcionais à maior categoria (ou ao maior limite semanal), total, entradas e a linha da semana anterior, comparada nos mesmos dias ("Semana anterior, até quarta: ..."). Uma categoria com limite semanal ganha a **marca do limite**: um traço de 2 × 16 px na tinta, mais alto que a barra, com um contorno de 2 px na cor do cartão para se separar do preenchimento laranja. Sob a barra, uma linha diz o mesmo por escrito ("Limite da semana: R$ 100,00 (85%, perto do limite)"); a marca nunca é a única pista. O limite mensal não vira marca, porque a barra é da semana: a linha diz "No mês: R$ 300,00 de R$ 1.000,00 (30%)".
6. A barra de navegação inferior tem Hoje, Resumo e Metas.

### Metas

1. Cabeçalho "Metas e limites" e título "Metas".
2. Cartão "Hábitos": os hábitos do catálogo, primeiro os para cumprir e depois os para evitar, com a explicação de que neles a meta é um limite. Cada linha tem o nome (15 px 500), a meta atual por escrito (13 px 600 no `--mydly-subtle`: "2 L por dia", "4 dias por semana", "No máximo 2 dias por semana") ou "Sem meta" e "Sem limite" no `--mydly-muted`, e um botão em pílula "Definir" ou "Editar" (44 px), com o nome do hábito no `aria-label` ("Editar meta de Hidratação").
3. Cartão "Orçamentos": as categorias fixas com o limite ("R$ 100,00 por semana") ou "Sem limite", uma barra de 8 px (verde-água abaixo de 80%, laranja a partir dele) com `role="progressbar"` e o gasto por escrito ("R$ 85,00 de R$ 100,00 · 85%, perto do limite").
4. Cartão "Semana": `select` com label "Início da semana" e a nota de que as semanas passadas também passam a ser contadas assim.
5. Estados vazios explicativos ("Nenhuma meta ainda. Escolha um hábito, como 2 L de água por dia ...", "Nenhum limite ainda. ... o MyDly avisa quando o gasto chega a 80%"). Depois de salvar, uma região `role="status"` anuncia "Meta de Hidratação salva.".

### Painéis de meta e de limite (sobre "Metas")

Mesmo formato do "Quanto foi?": chip neutro ("Hábito", "Hábito para evitar" ou "Orçamento"), título do diálogo ("Meta de Hidratação", "Limite de Delivery", "Limite de Alimentação"), a dica "Vale de hoje em diante" e os campos:

- meta de hábito: tipo em pílulas ("Quantidade por dia" ou "Dias por semana", só quando o hábito aceita os dois), quantidade com label e unidade em pílulas (`L` e `mL`, ou `h` e `min`), ou os dias em pílulas de 44 × 44 px (1 a 7; 0 a 7 no limite de hábito ruim);
- orçamento: valor grande com "R$" e o período em pílulas ("Por semana" ou "Por mês");
- botão principal no acento ("Salvar meta" ou "Salvar limite") e a ação secundária "Tirar meta" ou "Tirar limite" quando há uma.

Ao abrir, o foco vai para o primeiro campo; Esc e o "x" fecham, e o foco volta ao botão que abriu. O erro de digitação aparece sob o campo (`role="alert"`, ligado por `aria-describedby`), e a recusa da API aparece no painel com a mensagem dela.

## Acessibilidade

- Contraste mínimo de 4,5:1 em todo texto (as cores acima já passam).
- Progresso nunca só por cor: toda barra tem o valor por escrito ao lado ("1,5 / 2 L", "85%") e `role="progressbar"` com `aria-valuetext`.

### Contraste medido dos componentes da etapa 7

Razões calculadas pela fórmula de luminância relativa do WCAG 2.2. Texto precisa de 4,5:1; barras, marca do limite e bordas de controle precisam de 3:1 (componentes não textuais).

| Componente | Par | Claro | Razão | Escuro | Razão | Mínimo |
| --- | --- | --- | --- | --- | --- | --- |
| Nome da meta no Hoje, detalhe da linha em Metas, dica do painel | `--mydly-subtle` × `--mydly-surface` | `#3F434C` × `#FFFFFF` | 9,91:1 | `#C4C7CF` × `#1B1D22` | 9,97:1 | 4,5:1 |
| Valor "1,5 / 2 L", nome da linha | `--mydly-ink` × `--mydly-surface` | `#15171C` × `#FFFFFF` | 17,93:1 | `#F2F1EC` × `#1B1D22` | 14,91:1 | 4,5:1 |
| Placar contra a meta, linha do limite, progresso do orçamento, notas | `--mydly-muted` × `--mydly-surface` | `#5B5F6A` × `#FFFFFF` | 6,38:1 | `#A4A8B3` × `#1B1D22` | 7,09:1 | 4,5:1 |
| Alerta de orçamento perto do limite, chip dos painéis | `--mydly-neutral-text` × `--mydly-neutral-bg` | `#3F434C` × `#F1F0EA` | 8,68:1 | `#C4C7CF` × `#23252B` | 9,06:1 | 4,5:1 |
| Alerta de orçamento acima do limite | `--mydly-expense-text` × `--mydly-expense-bg` | `#9A3412` × `#FFEDD5` | 6,38:1 | `#FDBA74` × `#431407` | 9,28:1 | 4,5:1 |
| Erro do campo nos painéis | `--mydly-expense-text` × `--mydly-surface` | `#9A3412` × `#FFFFFF` | 7,31:1 | `#FDBA74` × `#1B1D22` | 10,00:1 | 4,5:1 |
| Pílula escolhida (dias, unidade, período) | `--mydly-surface` × `--mydly-ink` | `#FFFFFF` × `#15171C` | 17,93:1 | `#1B1D22` × `#F2F1EC` | 14,91:1 | 4,5:1 |
| Botão principal dos painéis | `--mydly-accent-ink` × `--mydly-accent` | `#15171C` × `#D4F25C` | 14,22:1 | `#15171C` × `#D4F25C` | 14,22:1 | 4,5:1 |
| Barra verde-água (meta diária, orçamento abaixo de 80%) | `--mydly-routine-text` × `--mydly-line` | `#115E59` × `#E4E2DA` | 5,85:1 | `#5EEAD4` × `#2C2F36` | 9,06:1 | 3:1 |
| Barra laranja (gasto, orçamento a partir de 80%) | `--mydly-expense-text` × `--mydly-line` | `#9A3412` × `#E4E2DA` | 5,63:1 | `#FDBA74` × `#2C2F36` | 7,95:1 | 3:1 |
| Marca do limite × trilho da barra | `--mydly-ink` × `--mydly-line` | `#15171C` × `#E4E2DA` | 13,82:1 | `#F2F1EC` × `#2C2F36` | 11,85:1 | 3:1 |
| Marca do limite × cartão (pontas acima e abaixo da barra) | `--mydly-ink` × `--mydly-surface` | `#15171C` × `#FFFFFF` | 17,93:1 | `#F2F1EC` × `#1B1D22` | 14,91:1 | 3:1 |
| Contorno da marca × barra laranja (gasto acima do limite) | `--mydly-surface` × `--mydly-expense-text` | `#FFFFFF` × `#9A3412` | 7,31:1 | `#1B1D22` × `#FDBA74` | 10,00:1 | 3:1 |
| Contorno da marca × barra verde-água | `--mydly-surface` × `--mydly-routine-text` | `#FFFFFF` × `#115E59` | 7,58:1 | `#1B1D22` × `#5EEAD4` | 11,40:1 | 3:1 |
| Borda do campo, do `select` e do botão "Editar" | `--mydly-field-border` × `--mydly-surface` | `#858993` × `#FFFFFF` | 3,50:1 | `#6B707B` × `#1B1D22` | 3,40:1 | 3:1 |

A marca do limite direto sobre o laranja não passaria (2,45:1 no claro e 1,49:1 no escuro); por isso ela tem o contorno na cor do cartão, que separa a marca da barra com folga nos dois temas.
- Botões de ícone com `aria-label`; campo de lançamento com `label` (pode ser visualmente oculto).
- Navegação completa por teclado.
