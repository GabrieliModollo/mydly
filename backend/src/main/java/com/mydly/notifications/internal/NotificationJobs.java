package com.mydly.notifications.internal;

import com.mydly.entries.EntryActivity;
import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetStatus;
import com.mydly.finance.BudgetWindow;
import com.mydly.finance.Budgets;
import com.mydly.insights.WeekHeadline;
import com.mydly.notifications.internal.NotificationRepository.Kind;
import com.mydly.notifications.internal.NotificationRepository.Settings;
import com.mydly.preferences.UserCalendar;
import com.mydly.preferences.Week;
import com.mydly.preferences.WeekPreferences;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * As três notificações (ADR 0016), em tarefas agendadas no fuso do usuário. Só olham quem tem um
 * aparelho cadastrado, e não fazem nada sem o Firebase configurado. Cada usuário é tratado à parte:
 * a falha de um não segura os outros.
 */
@Component
public class NotificationJobs {

    private static final Logger log = LoggerFactory.getLogger(NotificationJobs.class);

    private final NotificationRepository repository;
    private final Notifier notifier;
    private final Budgets budgets;
    private final WeekPreferences weeks;
    private final UserCalendar calendar;
    private final EntryActivity activity;
    private final WeekHeadline headlines;

    NotificationJobs(NotificationRepository repository, Notifier notifier, Budgets budgets, WeekPreferences weeks,
                     UserCalendar calendar, EntryActivity activity, WeekHeadline headlines) {
        this.repository = repository;
        this.notifier = notifier;
        this.budgets = budgets;
        this.weeks = weeks;
        this.calendar = calendar;
        this.activity = activity;
        this.headlines = headlines;
    }

    /**
     * Alerta de orçamento: uma categoria que chegou a 80% do limite, e de novo quando passa dele, uma
     * vez por período. Roda a cada 5 minutos, porque o gasto chega por lançamento, extrato ou Open Finance.
     */
    @Scheduled(fixedDelayString = "${mydly.notifications.budget-check:PT5M}",
            initialDelayString = "${mydly.notifications.budget-check:PT5M}")
    public void budgetAlerts() {
        forEachUser(userId -> {
            if (!repository.settings(userId).budgetAlerts()) {
                return;
            }
            LocalDate today = calendar.today();
            BudgetWindow window = BudgetWindow.wholePeriods(today, weeks.weekContaining(userId, today).start());
            for (BudgetProgress progress : budgets.progress(userId, window, calendar.zone())) {
                if (progress.status() != BudgetStatus.OK) {
                    String key = progress.category() + ":" + progress.periodStart() + ":" + progress.status();
                    notifier.notify(userId, Kind.BUDGET_ALERT, key, NotificationTexts.budget(progress));
                }
            }
        });
    }

    /** Lembrete do dia: às 21h, para quem não lançou nada hoje. */
    @Scheduled(cron = "${mydly.notifications.daily-reminder-cron:0 0 21 * * *}", zone = UserCalendar.ZONE_ID)
    public void dailyReminder() {
        forEachUser(userId -> {
            LocalDate today = calendar.today();
            if (repository.settings(userId).dailyReminder() && !activity.wroteOn(userId, today, calendar.zone())) {
                notifier.notify(userId, Kind.DAILY_REMINDER, today.toString(), NotificationTexts.dailyReminder());
            }
        });
    }

    /** Resumo da semana: às 20h do último dia da semana do usuário, a mensagem principal no tom dele. */
    @Scheduled(cron = "${mydly.notifications.weekly-summary-cron:0 0 20 * * *}", zone = UserCalendar.ZONE_ID)
    public void weeklySummary() {
        forEachUser(userId -> {
            Settings settings = repository.settings(userId);
            LocalDate today = calendar.today();
            Week week = weeks.weekContaining(userId, today);
            if (settings.weeklySummary() && today.equals(week.end())) {
                headlines.of(userId, week.start(), settings.tone()).ifPresent(headline -> notifier.notify(userId,
                        Kind.WEEKLY_SUMMARY, week.start().toString(), NotificationTexts.weeklySummary(headline)));
            }
        });
    }

    private void forEachUser(java.util.function.Consumer<UUID> job) {
        if (!notifier.enabled()) {
            return;
        }
        for (UUID userId : repository.usersWithDevices()) {
            try {
                job.accept(userId);
            } catch (RuntimeException failure) {
                log.warn("Notificação de um usuário falhou: {}", failure.getClass().getSimpleName());
            }
        }
    }
}
