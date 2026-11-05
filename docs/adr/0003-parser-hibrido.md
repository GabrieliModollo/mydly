# ADR 0003 — Parser híbrido: regras locais + LLM como fallback

- **Status:** Aceita
- **Data:** 2026-11-05

## Contexto

O lançamento é texto livre. Precisamos decidir se é finança ou rotina e extrair os dados estruturados. Regras puras falham em frases variadas; usar LLM em tudo é mais lento, custa por chamada e impede o uso offline.

## Decisão

1. **Regras locais primeiro.** Valor monetário é reconhecido por marcadores claros ("R$", "reais", "conto") ou por um número sozinho ("50"). Número acompanhado de unidade de medida ("2 litros", "2 horas") vai para rotinas.
2. **LLM como fallback** quando a confiança das regras é baixa, retornando um JSON validado contra um schema.
3. **Pergunta ao usuário** quando a confiança continua baixa, em vez de chutar.
4. **Frases compostas** ("fui à academia e gastei 30 no açaí") geram vários lançamentos.
5. **Aprendizado por usuário**: correções de categoria viram apelidos usados nas próximas interpretações.
6. **Modo sem LLM** configurável, para quem clonar o projeto rodar sem chave de API.

## Consequências

- A maioria dos lançamentos é resolvida sem rede e sem custo.
- O parser é o ponto mais testado do projeto (testes parametrizados com frases reais).
- O texto original é sempre guardado, permitindo reprocessar com versões melhores do parser.
