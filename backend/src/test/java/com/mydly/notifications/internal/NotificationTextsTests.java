package com.mydly.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Category;
import com.mydly.finance.Budget;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Os textos das notificações, nas palavras do app (ADR 0016). */
class NotificationTextsTests {

    private static final LocalDate MONDAY = LocalDate.of(2039, 5, 9);

    @Test
    void alertaDeOrcamentoPertoDoLimite() {
        PushSender.Message message = NotificationTexts.budget(progress(Category.FOOD, BudgetPeriod.WEEKLY, "100", "85"));

        assertThat(message.title()).isEqualTo("Alimentação: 85% do limite da semana");
        assertThat(message.body()).isEqualTo("Gasto até agora: R$ 85,00 de R$ 100,00.");
        assertThat(message.data()).containsEntry("route", "/");
    }

    @Test
    void alertaDeOrcamentoAcimaDoLimite() {
        PushSender.Message message = NotificationTexts.budget(progress(Category.LEISURE, BudgetPeriod.MONTHLY, "300",
                "1250.5"));

        assertThat(message.title()).isEqualTo("Lazer passou do limite do mês");
        assertThat(message.body()).isEqualTo("Gasto até agora: R$ 1.250,50 de R$ 300,00.");
    }

    @Test
    void lembreteEResumo() {
        assertThat(NotificationTexts.dailyReminder().title()).isEqualTo("Como foi o seu dia?");
        PushSender.Message weekly = NotificationTexts.weeklySummary("Você bebeu água em 6 de 7 dias.");
        assertThat(weekly.title()).isEqualTo("Sua semana no MyDly");
        assertThat(weekly.body()).isEqualTo("Você bebeu água em 6 de 7 dias.");
        assertThat(weekly.data()).containsEntry("route", "/resumo");
    }

    @Test
    void nenhumTextoFixoFalaDeCorpoPesoOuAparencia() {
        String texts = (NotificationTexts.dailyReminder().title() + NotificationTexts.dailyReminder().body()
                + NotificationTexts.weeklySummary("").title()).toLowerCase(Locale.ROOT);

        assertThat(texts).doesNotContain("peso", "corpo", "gord", "magr", "aparência");
    }

    private static BudgetProgress progress(Category category, BudgetPeriod period, String limit, String spent) {
        return BudgetProgress.of(new Budget(category, MONDAY, period, new BigDecimal(limit)), MONDAY,
                MONDAY.plusDays(6), new BigDecimal(spent));
    }
}
