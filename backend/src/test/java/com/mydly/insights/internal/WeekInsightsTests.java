package com.mydly.insights.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Category;
import com.mydly.finance.Budget;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import com.mydly.insights.internal.WeekInsights.WeekFacts;
import com.mydly.insights.internal.WeekSummary.DayMark;
import com.mydly.insights.internal.WeekSummary.HabitWeek;
import com.mydly.insights.internal.WeekSummary.Insight;
import com.mydly.insights.internal.WeekSummary.Previous;
import com.mydly.routines.GoalKind;
import com.mydly.routines.Polarity;
import com.mydly.routines.WeekGoalScore;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Regras das mensagens da semana (etapas 6 e 7, ADR 0009 e ADR 0010): cada uma dentro e fora do
 * limiar, a prioridade, os três tons e a regra de conteúdo.
 */
class WeekInsightsTests {

    private static final DayMark A = DayMark.ACHIEVED;
    private static final DayMark S = DayMark.SLIPPED;
    private static final DayMark M = DayMark.MISSED;
    private static final DayMark N = DayMark.NONE;
    private static final DayMark F = DayMark.FUTURE;

    @Test
    void habitoRuimEmTresDiasEDeslizeRecorrente() {
        Insight insight = only(facts(List.of(bad("JUNK_FOOD", "Besteira", S, N, S, N, S, N, N)), "0", null));

        assertThat(insight.kind()).isEqualTo(InsightKind.RECURRING_SLIP);
        assertThat(insight.numbers()).containsEntry("habit", "JUNK_FOOD").containsEntry("days", 3);
        assertThat(insight.text()).isEqualTo("Besteira em 3 dias esta semana. Vale planejar como evitar nos próximos.");
    }

    @Test
    void habitoRuimEmDoisDiasNaoEDeslizeRecorrente() {
        assertThat(kinds(facts(List.of(bad("JUNK_FOOD", "Besteira", S, N, S, N, N, N, N)), "0", null)))
                .doesNotContain(InsightKind.RECURRING_SLIP);
    }

    @Test
    void tresDiasSeguidosDeHabitoBomESequencia() {
        Insight insight = only(facts(List.of(good("HYDRATION", "Hidratação", A, N, A, A, A, N, N)), "0", null));

        assertThat(insight.kind()).isEqualTo(InsightKind.STREAK);
        assertThat(insight.numbers()).containsEntry("days", 3);
        assertThat(insight.text()).isEqualTo("Hidratação: 3 dias seguidos. Mantenha a sequência.");
    }

    @Test
    void diasCumpridosSemSequenciaNaoContam() {
        assertThat(kinds(facts(List.of(good("HYDRATION", "Hidratação", A, N, A, N, A, N, A)), "0", null)))
                .containsExactly(InsightKind.OVERVIEW);
    }

    @Test
    void gastoVinteProcentoAcimaDaSemanaAnterior() {
        Insight insight = only(facts(List.of(), "120", new Previous(new BigDecimal("100"), 0, 7)));

        assertThat(insight.kind()).isEqualTo(InsightKind.SPENDING_UP);
        assertThat(insight.numbers()).containsEntry("percent", 20L);
        assertThat(insight.text()).isEqualTo("Gastos: R$ 120,00. Na semana passada: R$ 100,00 (20% a mais agora).");
    }

    @Test
    void semanaEmAndamentoComparaComOsMesmosDiasDaAnteriorEDizAteQuando() {
        WeekFacts facts = new WeekFacts(List.of(), new BigDecimal("300"), new Previous(new BigDecimal("100"), 0, 3),
                Collections.nCopies(3, BigDecimal.ZERO), true, "quarta");

        Insight insight = only(facts);

        assertThat(insight.numbers()).containsEntry("percent", 200L).containsEntry("comparedDays", 3);
        assertThat(insight.text()).isEqualTo(
                "Gastos até quarta: R$ 300,00. Na semana passada até quarta: R$ 100,00 (200% a mais agora).");
    }

    @Test
    void gastoDezenovePorCentoAcimaNaoDispara() {
        assertThat(kinds(facts(List.of(), "119", new Previous(new BigDecimal("100"), 0, 7))))
                .doesNotContain(InsightKind.SPENDING_UP);
    }

    @Test
    void semSemanaAnteriorOuSemGastoNelaNaoCompara() {
        assertThat(kinds(facts(List.of(), "500", null))).doesNotContain(InsightKind.SPENDING_UP);
        assertThat(kinds(facts(List.of(), "500", new Previous(BigDecimal.ZERO, 3, 7))))
                .doesNotContain(InsightKind.SPENDING_UP);
    }

    @Test
    void sonoRuimComGastoMaiorCitaOsNumerosReais() {
        // Os outros dias ficam sem registro de sono, para a sequência de sono bom não disparar junto.
        HabitWeek sleep = good("SLEEP", "Sono", M, M, M, N, N, N, N);
        WeekFacts facts = new WeekFacts(List.of(sleep), new BigDecimal("300"), null,
                spent("60", "60", "60", "30", "30", "30", "30"), true, null);

        Insight insight = only(facts);

        assertThat(insight.kind()).isEqualTo(InsightKind.SLEEP_AND_SPENDING);
        assertThat(insight.numbers()).containsEntry("badDays", 3).containsEntry("restDays", 4)
                .containsEntry("avgBad", new BigDecimal("60.00")).containsEntry("avgRest", new BigDecimal("30.00"))
                .containsEntry("percent", 100L);
        assertThat(insight.text()).isEqualTo(
                "Dias de sono ruim: R$ 60,00 de gasto médio (3 dias). Nos outros 4 dias: R$ 30,00.");
    }

    @Test
    void cruzamentoComSoDoisDiasDeSonoRuimNaoAparece() {
        HabitWeek sleep = good("SLEEP", "Sono", M, M, A, A, A, A, A);
        WeekFacts facts = new WeekFacts(List.of(sleep), new BigDecimal("270"), null,
                spent("60", "60", "30", "30", "30", "30", "30"), true, null);

        assertThat(kinds(facts)).doesNotContain(InsightKind.SLEEP_AND_SPENDING);
    }

    @Test
    void cruzamentoComMenosDeTresDiasNoOutroGrupoNaoAparece() {
        HabitWeek sleep = good("SLEEP", "Sono", M, M, M, M, M, A, A);
        WeekFacts facts = new WeekFacts(List.of(sleep), new BigDecimal("360"), null,
                spent("60", "60", "60", "60", "60", "30", "30"), true, null);

        assertThat(kinds(facts)).doesNotContain(InsightKind.SLEEP_AND_SPENDING);
    }

    @Test
    void cruzamentoComDiferencaMenorQueVintePorCentoNaoAparece() {
        HabitWeek sleep = good("SLEEP", "Sono", M, M, M, A, A, A, A);
        WeekFacts facts = new WeekFacts(List.of(sleep), new BigDecimal("229"), null,
                spent("35", "35", "35", "31", "31", "31", "31"), true, null);

        assertThat(kinds(facts)).doesNotContain(InsightKind.SLEEP_AND_SPENDING);
    }

    @Test
    void semanaSemDadosRecebeBoasVindasEmTodoTom() {
        WeekFacts empty = new WeekFacts(List.of(), BigDecimal.ZERO, null, List.of(), false, null);

        for (Tone tone : Tone.values()) {
            List<Insight> insights = WeekInsights.evaluate(empty, tone);
            assertThat(insights).singleElement().extracting(Insight::kind).isEqualTo(InsightKind.WELCOME);
        }
    }

    @Test
    void prioridadeLimitaATresMensagensComADoDeslizeNaFrente() {
        List<HabitWeek> habits = List.of(
                good("SLEEP", "Sono", M, M, M, A, A, A, A),
                good("HYDRATION", "Hidratação", A, A, A, A, A, A, A),
                bad("ENERGY_DRINK", "Energético", S, S, S, N, N, N, N));
        WeekFacts facts = new WeekFacts(habits, new BigDecimal("300"), new Previous(new BigDecimal("100"), 5, 7),
                spent("60", "60", "60", "30", "30", "30", "30"), true, null);

        assertThat(kinds(facts)).containsExactly(
                InsightKind.RECURRING_SLIP, InsightKind.SPENDING_UP, InsightKind.SLEEP_AND_SPENDING);
    }

    @ParameterizedTest
    @EnumSource(Tone.class)
    void todoTipoEVarianteTemTextoNosTresTons(Tone tone) {
        Map<String, String> values = Map.ofEntries(Map.entry("Habit", "Besteira"), Map.entry("habit", "besteira"),
                Map.entry("days", "3"), Map.entry("spent", "R$ 1,00"), Map.entry("previous", "R$ 1,00"),
                Map.entry("percent", "20"), Map.entry("badDays", "3"), Map.entry("restDays", "4"),
                Map.entry("avgBad", "R$ 1,00"), Map.entry("avgRest", "R$ 1,00"), Map.entry("upTo", " até quarta"),
                Map.entry("achieved", "2"), Map.entry("targetDays", "4 dias"), Map.entry("slips", "1"),
                Map.entry("slipsDays", "1 dia"), Map.entry("limitDays", "2 dias"), Map.entry("Category", "Lazer"),
                Map.entry("period", "da semana"), Map.entry("limit", "R$ 100,00"));
        for (InsightKind kind : InsightKind.values()) {
            assertThat(InsightTemplates.render(kind, tone, values)).as("%s/%s", kind, tone).isNotBlank();
        }
        assertThat(InsightTemplates.keys()).contains("GOAL_MET/LIMIT", "GOAL_AT_RISK/LIMIT");
        for (String key : InsightTemplates.keys()) {
            String[] parts = key.split("/");
            String variant = parts.length > 1 ? parts[1] : null;
            assertThat(InsightTemplates.render(InsightKind.valueOf(parts[0]), variant, tone, values))
                    .as("%s/%s", key, tone).isNotBlank();
        }
    }

    // Etapa 7: orçamentos e metas

    @Test
    void ordemCompletaDePrioridade() {
        assertThat(InsightKind.values()).containsExactly(
                InsightKind.BUDGET_OVER, InsightKind.RECURRING_SLIP, InsightKind.GOAL_AT_RISK, InsightKind.SPENDING_UP,
                InsightKind.BUDGET_NEAR, InsightKind.SLEEP_AND_SPENDING, InsightKind.GOAL_MET, InsightKind.STREAK,
                InsightKind.OVERVIEW, InsightKind.WELCOME);
    }

    /**
     * Uma semana em que as oito regras disparam ao mesmo tempo: a lista completa sai na ordem de
     * prioridade, e só as três primeiras viram mensagem.
     */
    @Test
    void todasAsRegrasJuntasSaemNaOrdemDePrioridade() {
        List<HabitWeek> habits = List.of(
                weekly(good("HYDRATION", "Hidratação", 6, A, A, A, N, N, N, F), 3),
                weekly(good("GYM", "Academia", 6, N, N, N, N, N, N, F), 7),
                good("SLEEP", "Sono", 6, M, M, M, A, N, A, F),
                bad("ENERGY_DRINK", "Energético", 6, S, S, S, N, N, N, F));
        WeekFacts facts = new WeekFacts(habits, new BigDecimal("270"), new Previous(new BigDecimal("100"), 0, 6),
                spent("60", "60", "60", "30", "30", "30"), true, "sábado", true,
                List.of(budget(Category.FOOD, "100", "120"), budget(Category.LEISURE, "100", "85")));

        assertThat(WeekInsights.findings(facts)).extracting(WeekInsights.Finding::kind).containsExactly(
                InsightKind.BUDGET_OVER, InsightKind.RECURRING_SLIP, InsightKind.GOAL_AT_RISK, InsightKind.SPENDING_UP,
                InsightKind.BUDGET_NEAR, InsightKind.SLEEP_AND_SPENDING, InsightKind.GOAL_MET, InsightKind.STREAK);
        assertThat(kinds(facts)).containsExactly(
                InsightKind.BUDGET_OVER, InsightKind.RECURRING_SLIP, InsightKind.GOAL_AT_RISK);
    }

    @Test
    void resumoSimplesEBoasVindasSoQuandoNadaDisparou() {
        WeekFacts quiet = new WeekFacts(List.of(good("GYM", "Academia", A, N, N, N, N, N, N)), BigDecimal.ZERO,
                null, Collections.nCopies(7, BigDecimal.ZERO), true, null);
        WeekFacts empty = new WeekFacts(List.of(), BigDecimal.ZERO, null, List.of(), false, null);

        assertThat(kinds(quiet)).containsExactly(InsightKind.OVERVIEW);
        assertThat(kinds(empty)).containsExactly(InsightKind.WELCOME);
    }

    @Test
    void orcamentoEm85PorCentoEAviso() {
        Insight insight = only(withBudgets(budget(Category.FOOD, "100", "85")));

        assertThat(insight.kind()).isEqualTo(InsightKind.BUDGET_NEAR);
        assertThat(insight.numbers()).containsEntry("category", "FOOD").containsEntry("percent", 85)
                .containsEntry("period", "WEEKLY");
        assertThat(insight.text()).isEqualTo("Alimentação: 85% do limite da semana (R$ 85,00 de R$ 100,00).");
    }

    @Test
    void orcamentoAcimaDoLimiteEEstouroEMensalDizMes() {
        BudgetProgress monthly = BudgetProgress.of(new Budget(Category.LEISURE, LocalDate.of(2026, 1, 1),
                BudgetPeriod.MONTHLY, new BigDecimal("300")), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8),
                new BigDecimal("310"));

        Insight insight = only(withBudgets(monthly));

        assertThat(insight.kind()).isEqualTo(InsightKind.BUDGET_OVER);
        assertThat(insight.text()).isEqualTo("Lazer: R$ 310,00 de R$ 300,00 no limite do mês (103%). Passou do limite.");
    }

    @Test
    void entreVariosOrcamentosNoAvisoValeOMaisAlto() {
        List<Insight> insights = WeekInsights.evaluate(withBudgets(budget(Category.FOOD, "100", "81"),
                budget(Category.TRANSPORT, "50", "49"), budget(Category.LEISURE, "100", "79")), Tone.DIRECT);

        assertThat(insights).singleElement().satisfies(insight ->
                assertThat(insight.numbers()).containsEntry("category", "TRANSPORT").containsEntry("percent", 98));
    }

    @Test
    void orcamentoAbaixoDeOitentaPorCentoNaoAparece() {
        assertThat(kinds(withBudgets(budget(Category.FOOD, "100", "79.99")))).containsExactly(InsightKind.OVERVIEW);
    }

    /**
     * Meta de 4 dias com a semana começando na segunda e nenhum dia cumprido. Na quinta ainda dá:
     * hoje e os três dias seguintes somam 4. Na sexta não dá mais.
     */
    @Test
    void metaEmRiscoNaoDisparaNoUltimoDiaPossivelEDisparaNoSeguinte() {
        HabitWeek thursday = weekly(good("GYM", "Academia", 4, N, N, N, N, F, F, F), 4);
        HabitWeek friday = weekly(good("GYM", "Academia", 5, N, N, N, N, N, F, F), 4);

        assertThat(WeekInsights.remainingDays(thursday)).isEqualTo(4);
        assertThat(kinds(inProgress(thursday))).doesNotContain(InsightKind.GOAL_AT_RISK);

        Insight risk = WeekInsights.evaluate(inProgress(friday), Tone.DIRECT).getFirst();
        assertThat(risk.kind()).isEqualTo(InsightKind.GOAL_AT_RISK);
        assertThat(risk.numbers()).containsEntry("achieved", 0).containsEntry("target", 4).containsEntry("remaining", 3);
        assertThat(risk.text()).isEqualTo(
                "Academia: 0 de 4 dias. Mesmo cumprindo os dias que faltam, a meta desta semana não fecha.");
    }

    @Test
    void hojeJaCumpridoNaoContaComoDiaRestante() {
        HabitWeek habit = weekly(good("GYM", "Academia", 5, N, N, N, N, A, F, F), 4);

        assertThat(WeekInsights.remainingDays(habit)).isEqualTo(2);
        assertThat(kinds(inProgress(habit))).contains(InsightKind.GOAL_AT_RISK);
    }

    @Test
    void semanaEncerradaNaoTemMetaEmRiscoSoMetaCumprida() {
        WeekFacts missed = new WeekFacts(List.of(weekly(good("GYM", "Academia", A, N, A, N, N, N, N), 4)),
                BigDecimal.ZERO, null, Collections.nCopies(7, BigDecimal.ZERO), true, null);
        WeekFacts met = new WeekFacts(List.of(weekly(good("GYM", "Academia", A, N, A, N, A, N, A), 4)),
                BigDecimal.ZERO, null, Collections.nCopies(7, BigDecimal.ZERO), true, null);

        assertThat(kinds(missed)).containsExactly(InsightKind.OVERVIEW);
        Insight insight = only(met);
        assertThat(insight.kind()).isEqualTo(InsightKind.GOAL_MET);
        assertThat(insight.text()).isEqualTo("Academia: 4 de 4 dias. Meta da semana cumprida.");
    }

    @Test
    void metaSemanalAtingidaComASemanaEmCurso() {
        HabitWeek habit = weekly(good("GYM", "Academia", 3, A, N, A, F, F, F, F), 2);

        assertThat(kinds(inProgress(habit))).containsExactly(InsightKind.GOAL_MET);
    }

    @Test
    void metaDiariaNaoGeraMensagemDeMeta() {
        HabitWeek habit = goal(good("HYDRATION", "Hidratação", 5, N, N, N, N, N, F, F),
                new WeekGoalScore("HYDRATION", "Hidratação", Polarity.GOOD, GoalKind.DAILY_QUANTITY, 0, 7, null, null));

        assertThat(kinds(inProgress(habit))).doesNotContain(InsightKind.GOAL_AT_RISK, InsightKind.GOAL_MET);
    }

    /** Limite de 2 dias de delivery na semana em curso: dentro (1), no limite (2) e acima (3). */
    @ParameterizedTest(name = "{0} deslizes: em risco = {1}")
    @CsvSource({"1, false", "2, false", "3, true"})
    void limiteDeHabitoRuimNaSemanaEmCurso(int slips, boolean atRisk) {
        List<InsightKind> kinds = kinds(inProgress(limit(slips, 2, 6)));

        assertThat(kinds.contains(InsightKind.GOAL_AT_RISK)).isEqualTo(atRisk);
        assertThat(kinds).doesNotContain(InsightKind.GOAL_MET);
    }

    @Test
    void limiteEstouradoTemTextoDeLimite() {
        // Três deslizes também são deslize recorrente, que vem antes na prioridade.
        List<Insight> insights = WeekInsights.evaluate(inProgress(limit(3, 2, 6)), Tone.DIRECT);
        assertThat(insights).extracting(Insight::kind)
                .containsExactly(InsightKind.RECURRING_SLIP, InsightKind.GOAL_AT_RISK);
        Insight insight = insights.get(1);

        assertThat(insight.numbers()).containsEntry("slips", 3).containsEntry("limit", 2);
        assertThat(insight.text()).isEqualTo("Delivery: 3 dias nesta semana, acima do limite de 2 dias.");
    }

    /** Na semana encerrada, dentro do limite (1) e no limite (2) é meta cumprida; acima (3) não é. */
    @ParameterizedTest(name = "{0} deslizes: cumprida = {1}")
    @CsvSource({"1, true", "2, true", "3, false"})
    void limiteDeHabitoRuimNaSemanaEncerrada(int slips, boolean met) {
        WeekFacts facts = new WeekFacts(List.of(limit(slips, 2, 7)), BigDecimal.ZERO, null,
                Collections.nCopies(7, BigDecimal.ZERO), true, null);

        assertThat(kinds(facts).contains(InsightKind.GOAL_MET)).isEqualTo(met);
        assertThat(kinds(facts)).doesNotContain(InsightKind.GOAL_AT_RISK);
    }

    @Test
    void limiteRespeitadoTemTextoDeLimite() {
        WeekFacts atLimit = new WeekFacts(List.of(limit(2, 2, 7)), BigDecimal.ZERO, null,
                Collections.nCopies(7, BigDecimal.ZERO), true, null);
        WeekFacts single = new WeekFacts(List.of(limit(1, 1, 7)), BigDecimal.ZERO, null,
                Collections.nCopies(7, BigDecimal.ZERO), true, null);

        assertThat(only(atLimit).text()).isEqualTo("Delivery: 2 de no máximo 2 dias. Dentro do limite da semana.");
        assertThat(only(single).text()).isEqualTo("Delivery: 1 de no máximo 1 dia. Dentro do limite da semana.");
    }

    /**
     * Regra fixa de conteúdo: nenhuma mensagem, em nenhum tom, fala de corpo, peso, aparência ou
     * quantidade de comida. A busca ignora acentos e maiúsculas e cobre todos os textos, inclusive os
     * de orçamento e de meta da etapa 7 e as variantes de limite.
     */
    @Test
    void nenhumTextoFalaDeCorpoPesoOuAparencia() {
        List<String> forbidden = List.of("peso", "pesad", "quilo", "kg", "gord", "magr", "corpo", "aparencia",
                "barriga", "emagre", "engord", "caloria", "comer demais", "comeu demais", "comida demais",
                "comendo demais", "feio", "bonit", "forma fisica", "balanca", "porcao", "porcoes", "medida",
                "silhueta", "dieta restrit");
        assertThat(InsightTemplates.all()).hasSize(InsightTemplates.keys().size() * Tone.values().length);
        for (String template : InsightTemplates.all()) {
            String normalized = Normalizer.normalize(template, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            for (String term : forbidden) {
                assertThat(normalized).as("\"%s\" em: %s", term, template).doesNotContain(term);
            }
        }
    }

    private static WeekFacts facts(List<HabitWeek> habits, String spent, Previous previous) {
        return new WeekFacts(habits, new BigDecimal(spent), previous,
                Collections.nCopies(7, BigDecimal.ZERO), true, null);
    }

    private static WeekFacts inProgress(HabitWeek habit) {
        return new WeekFacts(List.of(habit), BigDecimal.ZERO, null,
                Collections.nCopies(habit.daysElapsed(), BigDecimal.ZERO), true, "hoje", true, List.of());
    }

    private static WeekFacts withBudgets(BudgetProgress... budgets) {
        return new WeekFacts(List.of(), new BigDecimal("50"), null, Collections.nCopies(7, BigDecimal.ZERO), true,
                null, false, List.of(budgets));
    }

    private static BudgetProgress budget(Category category, String limit, String spent) {
        return BudgetProgress.of(new Budget(category, LocalDate.of(2026, 1, 1), BudgetPeriod.WEEKLY,
                new BigDecimal(limit)), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), new BigDecimal(spent));
    }

    /** Uma meta de {@code days} dias por semana num hábito bom, com o placar tirado dos pontos. */
    private static HabitWeek weekly(HabitWeek habit, int days) {
        return goal(habit, new WeekGoalScore(habit.code(), habit.name(), habit.polarity(), GoalKind.WEEKLY_FREQUENCY,
                habit.achievedDays(), days, null, null));
    }

    private static HabitWeek goal(HabitWeek habit, WeekGoalScore score) {
        return new HabitWeek(habit.code(), habit.name(), habit.polarity(), habit.days(), habit.achievedDays(),
                habit.daysElapsed(), score);
    }

    /** Delivery com {@code slips} deslizes nos primeiros dias e limite {@code limit}. */
    private static HabitWeek limit(int slips, int limit, int elapsed) {
        DayMark[] days = new DayMark[7];
        for (int day = 0; day < 7; day++) {
            days[day] = day >= elapsed ? F : day < slips ? S : N;
        }
        HabitWeek habit = habit("DELIVERY", "Delivery", Polarity.BAD, elapsed, days);
        return goal(habit, new WeekGoalScore("DELIVERY", "Delivery", Polarity.BAD, GoalKind.WEEKLY_FREQUENCY, 0, null,
                limit, slips));
    }

    private static Insight only(WeekFacts facts) {
        List<Insight> insights = WeekInsights.evaluate(facts, Tone.DIRECT);
        assertThat(insights).hasSize(1);
        return insights.getFirst();
    }

    private static List<InsightKind> kinds(WeekFacts facts) {
        return WeekInsights.evaluate(facts, Tone.DIRECT).stream().map(Insight::kind).toList();
    }

    private static List<BigDecimal> spent(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private static HabitWeek good(String code, String name, DayMark... days) {
        return habit(code, name, Polarity.GOOD, 7, days);
    }

    private static HabitWeek good(String code, String name, int elapsed, DayMark... days) {
        return habit(code, name, Polarity.GOOD, elapsed, days);
    }

    private static HabitWeek bad(String code, String name, DayMark... days) {
        return habit(code, name, Polarity.BAD, 7, days);
    }

    private static HabitWeek bad(String code, String name, int elapsed, DayMark... days) {
        return habit(code, name, Polarity.BAD, elapsed, days);
    }

    private static HabitWeek habit(String code, String name, Polarity polarity, int elapsed, DayMark... days) {
        int achieved = (int) Arrays.stream(days).filter(day -> day == DayMark.ACHIEVED).count();
        return new HabitWeek(code, name, polarity, List.of(days), achieved, elapsed);
    }
}
