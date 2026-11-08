package com.mydly.notifications.internal;

import com.mydly.entries.Category;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetStatus;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Os textos das notificações, nas mesmas palavras do app (docs/design.md). Funções puras, com testes.
 * Como as mensagens da semana (ADR 0009), nenhum texto fala de corpo, peso ou aparência.
 */
final class NotificationTexts {

    private static final Map<Category, String> CATEGORY_NAMES = new EnumMap<>(Map.of(
            Category.FOOD, "Alimentação",
            Category.GROCERIES, "Mercado",
            Category.PHARMACY, "Farmácia",
            Category.TRANSPORT, "Transporte",
            Category.HOUSING, "Moradia",
            Category.HEALTH, "Saúde",
            Category.LEISURE, "Lazer",
            Category.WORK, "Trabalho",
            Category.OTHER, "Outros"));

    private NotificationTexts() {
    }

    /** "Alimentação: 85% do limite da semana" ou "Lazer passou do limite do mês". */
    static PushSender.Message budget(BudgetProgress progress) {
        String name = CATEGORY_NAMES.get(progress.category());
        String period = progress.period() == BudgetPeriod.WEEKLY ? "da semana" : "do mês";
        String amounts = money(progress.spent()) + " de " + money(progress.limit());
        String title = progress.status() == BudgetStatus.OVER
                ? name + " passou do limite " + period
                : name + ": " + progress.percent() + "% do limite " + period;
        return new PushSender.Message(title, "Gasto até agora: " + amounts + ".", Map.of("route", "/"));
    }

    static PushSender.Message dailyReminder() {
        return new PushSender.Message("Como foi o seu dia?",
                "Ainda não tem nada lançado hoje. Um gasto, um hábito, uma frase: escreva como falaria.",
                Map.of("route", "/"));
    }

    static PushSender.Message weeklySummary(String headline) {
        return new PushSender.Message("Sua semana no MyDly", headline, Map.of("route", "/resumo"));
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
        // O Java separa "R$" do número com um espaço que não quebra; o app usa o espaço comum.
        return format.format(amount).replace(' ', ' ');
    }
}
