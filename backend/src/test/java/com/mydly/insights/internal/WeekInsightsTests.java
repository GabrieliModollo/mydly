package com.mydly.insights.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.insights.internal.WeekInsights.WeekFacts;
import com.mydly.insights.internal.WeekSummary.DayMark;
import com.mydly.insights.internal.WeekSummary.HabitWeek;
import com.mydly.insights.internal.WeekSummary.Insight;
import com.mydly.insights.internal.WeekSummary.Previous;
import com.mydly.routines.Polarity;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Regras das mensagens da semana (etapa 6, ADR 0009): cada uma dentro e fora do limiar, a
 * prioridade, os três tons e a regra de conteúdo.
 */
class WeekInsightsTests {

    private static final DayMark A = DayMark.ACHIEVED;
    private static final DayMark S = DayMark.SLIPPED;
    private static final DayMark M = DayMark.MISSED;
    private static final DayMark N = DayMark.NONE;

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
    void todoTipoTemTextoNosTresTons(Tone tone) {
        Map<String, String> withAchieved = Map.ofEntries(Map.entry("Habit", "Besteira"), Map.entry("habit", "besteira"),
                Map.entry("days", "3"), Map.entry("spent", "R$ 1,00"), Map.entry("previous", "R$ 1,00"),
                Map.entry("percent", "20"), Map.entry("badDays", "3"), Map.entry("restDays", "4"),
                Map.entry("avgBad", "R$ 1,00"), Map.entry("avgRest", "R$ 1,00"), Map.entry("upTo", " até quarta"),
                Map.entry("achieved", "2 hábitos cumpridos"));
        for (InsightKind kind : InsightKind.values()) {
            assertThat(InsightTemplates.render(kind, tone, withAchieved)).as("%s/%s", kind, tone).isNotBlank();
        }
    }

    /**
     * Regra fixa de conteúdo: nenhuma mensagem, em nenhum tom, fala de corpo, peso, aparência ou
     * quantidade de comida. A busca ignora acentos e maiúsculas.
     */
    @Test
    void nenhumTextoFalaDeCorpoPesoOuAparencia() {
        List<String> forbidden = List.of("peso", "pesad", "quilo", "kg", "gord", "magr", "corpo", "aparencia",
                "barriga", "emagre", "engord", "caloria", "comer demais", "comeu demais", "comida demais",
                "comendo demais", "feio", "bonit", "forma fisica", "balanca");
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
        return habit(code, name, Polarity.GOOD, days);
    }

    private static HabitWeek bad(String code, String name, DayMark... days) {
        return habit(code, name, Polarity.BAD, days);
    }

    private static HabitWeek habit(String code, String name, Polarity polarity, DayMark... days) {
        int achieved = (int) Arrays.stream(days).filter(day -> day == DayMark.ACHIEVED).count();
        return new HabitWeek(code, name, polarity, List.of(days), achieved, 7);
    }
}
