package com.mydly.preferences;

import com.mydly.preferences.internal.PreferencesRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo preferences: como é a semana de cada usuário. Substitui a configuração fixa
 * {@code mydly.week.first-day-of-week} da etapa 6 (ADR 0009 e ADR 0010).
 *
 * <p>A semana é sempre calculada na hora a partir da preferência atual: quando o usuário troca o dia
 * de início, as semanas passadas também passam a ser vistas na definição nova. Nenhum código deve
 * supor que a semana começa na segunda.
 */
@Service
public class WeekPreferences {

    private final PreferencesRepository repository;
    private final UserCalendar calendar;

    WeekPreferences(PreferencesRepository repository, UserCalendar calendar) {
        this.repository = repository;
        this.calendar = calendar;
    }

    /** O dia em que a semana começa. */
    @Transactional(readOnly = true)
    public DayOfWeek firstDayOfWeek(UUID userId) {
        return repository.firstDayOfWeek(userId);
    }

    /** A semana que contém {@code day}, na definição atual. */
    @Transactional(readOnly = true)
    public Week weekContaining(UUID userId, LocalDate day) {
        return Week.containing(day, firstDayOfWeek(userId));
    }

    /** A semana de hoje, no fuso do usuário. */
    @Transactional(readOnly = true)
    public Week currentWeek(UUID userId) {
        return weekContaining(userId, calendar.today());
    }
}
