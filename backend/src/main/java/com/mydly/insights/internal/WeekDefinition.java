package com.mydly.insights.internal;

import java.time.DayOfWeek;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Como é a semana do resumo. Hoje é uma configuração global ({@code mydly.week.first-day-of-week},
 * segunda por padrão); na etapa "Metas e preferências" ela vira uma preferência do usuário. Nenhum
 * código deve supor que a semana começa na segunda.
 *
 * @param firstDayOfWeek o dia em que a semana começa
 */
@ConfigurationProperties(prefix = "mydly.week")
public record WeekDefinition(@DefaultValue("MONDAY") DayOfWeek firstDayOfWeek) {

    public WeekDefinition {
        Objects.requireNonNull(firstDayOfWeek, "firstDayOfWeek");
    }
}
