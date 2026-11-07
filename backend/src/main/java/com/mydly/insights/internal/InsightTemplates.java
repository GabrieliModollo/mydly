package com.mydly.insights.internal;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Textos das mensagens da semana nos três tons, com marcadores {@code {nome}} trocados pelos números.
 * Cada tipo tem um texto padrão; as metas têm também a variante do limite de hábito ruim
 * ({@link #LIMIT}).
 *
 * <p>Regra fixa de conteúdo: as mensagens falam de comportamento (o que a pessoa fez ou deixou de
 * fazer), nunca de corpo, peso, aparência ou quantidade de comida, em nenhum tom. A semana sem dados
 * recebe boas-vindas, nunca bronca. Um teste varre todos os textos atrás de termos proibidos.
 */
final class InsightTemplates {

    /** Variante dos textos de meta para o limite de hábito ruim. */
    static final String LIMIT = "LIMIT";

    private static final Map<String, Map<Tone, String>> TEMPLATES = new LinkedHashMap<>();

    static {
        // {period} é "da semana" ou "do mês".
        put(InsightKind.BUDGET_OVER, null,
                "{Category} passou do limite {period}: {spent} de {limit}. Vale rever os próximos gastos com calma.",
                "{Category}: {spent} de {limit} no limite {period} ({percent}%). Passou do limite.",
                "{Category} estourou o limite {period}: {spent} de {limit}. Hora de fechar a carteira.");
        put(InsightKind.RECURRING_SLIP, null,
                "{Habit} apareceu em {days} dias desta semana. Que tal escolher um dia para fazer diferente?",
                "{Habit} em {days} dias esta semana. Vale planejar como evitar nos próximos.",
                "{days} dias de {habit} nesta semana? Dá para fazer melhor que isso.");
        put(InsightKind.GOAL_AT_RISK, null,
                "{Habit}: {achieved} de {targetDays} até agora. A meta desta semana não fecha mais, e tudo bem: a próxima é uma chance nova.",
                "{Habit}: {achieved} de {targetDays}. Mesmo cumprindo os dias que faltam, a meta desta semana não fecha.",
                "{Habit}: {achieved} de {targetDays}. Nem cumprindo todos os dias que faltam dá para bater a meta. Capricha na próxima.");
        put(InsightKind.GOAL_AT_RISK, LIMIT,
                "{Habit} passou do limite desta semana: {slipsDays}, para no máximo {limitDays}. Que tal planejar os próximos dias?",
                "{Habit}: {slipsDays} nesta semana, acima do limite de {limitDays}.",
                "{Habit} em {slipsDays}, e o limite era {limitDays}. Estourou o combinado.");
        // {upTo} é " até quarta" numa semana em andamento e vazio numa semana completa.
        put(InsightKind.SPENDING_UP, null,
                "Você gastou {spent}{upTo}; na semana passada{upTo}, foram {previous}. São {percent}% a mais. Vale olhar as categorias com calma.",
                "Gastos{upTo}: {spent}. Na semana passada{upTo}: {previous} ({percent}% a mais agora).",
                "{spent} em gastos{upTo}; na semana passada{upTo}, foram {previous}. {percent}% a mais. Segura a carteira.");
        put(InsightKind.BUDGET_NEAR, null,
                "{Category} já está em {percent}% do limite {period} ({spent} de {limit}). Ainda dá para ajustar com calma.",
                "{Category}: {percent}% do limite {period} ({spent} de {limit}).",
                "{Category} já em {percent}% do limite {period}. Freia antes de estourar.");
        put(InsightKind.SLEEP_AND_SPENDING, null,
                "Nos {badDays} dias em que você dormiu mal, gastou em média {avgBad} por dia; nos outros {restDays}, {avgRest}. Descansar bem pode ajudar o bolso também.",
                "Dias de sono ruim: {avgBad} de gasto médio ({badDays} dias). Nos outros {restDays} dias: {avgRest}.",
                "Nos {badDays} dias de sono ruim você gastou {avgBad} por dia, contra {avgRest} nos outros {restDays}. O cansaço está saindo caro.");
        put(InsightKind.GOAL_MET, null,
                "{Habit}: {achieved} de {targetDays} nesta semana. Meta cumprida, muito bem!",
                "{Habit}: {achieved} de {targetDays}. Meta da semana cumprida.",
                "{Habit}: {achieved} de {targetDays}. Meta batida; agora não vale relaxar.");
        put(InsightKind.GOAL_MET, LIMIT,
                "{Habit} ficou dentro do limite: {slips} de no máximo {limitDays}. Muito bem!",
                "{Habit}: {slips} de no máximo {limitDays}. Dentro do limite da semana.",
                "{Habit}: {slips} de no máximo {limitDays}. Dentro do limite, desta vez.");
        put(InsightKind.STREAK, null,
                "{Habit} por {days} dias seguidos. Muito bem, continue no seu ritmo.",
                "{Habit}: {days} dias seguidos. Mantenha a sequência.",
                "{days} dias seguidos de {habit}. Agora não vale quebrar, hein.");
        put(InsightKind.OVERVIEW, null,
                "Até agora: {achieved} e {spent} em gastos. Bom acompanhar de perto.",
                "Semana até agora: {achieved}, {spent} em gastos.",
                "{achieved} e {spent} em gastos. Dá para cumprir mais.");
        put(InsightKind.WELCOME, null,
                "Sua semana começa aqui. Conte o que fizer e o MyDly monta o resumo para você.",
                "Nada registrado nesta semana ainda. Escreva o que fizer e o resumo aparece aqui.",
                "Semana em branco por enquanto. Bora registrar o primeiro hábito?");
    }

    private InsightTemplates() {
    }

    private static void put(InsightKind kind, String variant, String gentle, String direct, String scold) {
        Map<Tone, String> byTone = new EnumMap<>(Tone.class);
        byTone.put(Tone.GENTLE, gentle);
        byTone.put(Tone.DIRECT, direct);
        byTone.put(Tone.SCOLD, scold);
        TEMPLATES.put(key(kind, variant), byTone);
    }

    private static String key(InsightKind kind, String variant) {
        return variant == null ? kind.name() : kind.name() + "/" + variant;
    }

    /** O texto padrão do tipo no tom, com os marcadores trocados. */
    static String render(InsightKind kind, Tone tone, Map<String, String> values) {
        return render(kind, null, tone, values);
    }

    /**
     * O texto do tipo (ou da variante) no tom, com os marcadores trocados. {@code {Habit}} é o nome do
     * hábito com maiúscula, para abrir a frase, e {@code {habit}} em minúsculas, no meio dela.
     */
    static String render(InsightKind kind, String variant, Tone tone, Map<String, String> values) {
        Map<Tone, String> byTone = TEMPLATES.get(key(kind, variant));
        if (byTone == null) {
            throw new IllegalArgumentException("sem texto para " + key(kind, variant));
        }
        String text = byTone.get(tone);
        for (Map.Entry<String, String> value : values.entrySet()) {
            text = text.replace("{" + value.getKey() + "}", value.getValue());
        }
        if (text.contains("{")) {
            throw new IllegalStateException("marcador sem valor em " + key(kind, variant) + "/" + tone);
        }
        return text;
    }

    /** As chaves dos textos: o nome do tipo e, nas variantes, o tipo com a variante. */
    static Set<String> keys() {
        return TEMPLATES.keySet();
    }

    /** Todos os textos, para os testes de conteúdo. */
    static List<String> all() {
        return TEMPLATES.values().stream().flatMap(byTone -> byTone.values().stream()).toList();
    }
}
