package com.mydly.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.MutableClock;
import com.mydly.TestUsers;
import com.mydly.TestcontainersConfiguration;
import com.mydly.accounts.internal.AccountRepository;
import com.mydly.insights.WeekHeadline;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Notificações pela API e pelas tarefas agendadas (etapa 13, ADR 0016), com o envio simulado e um
 * PostgreSQL real. Cada teste tem um usuário e uma semana próprios; as tarefas são chamadas direto.
 */
@SpringBootTest(properties = {
        "mydly.notifications.budget-check=PT1H",
        "mydly.notifications.daily-reminder-cron=-",
        "mydly.notifications.weekly-summary-cron=-"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationIntegrationTests {

    private static final AtomicInteger WEEKS = new AtomicInteger();
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2043-01-07T15:00:00Z"));

    @TestBean
    private Clock clock;

    @MockitoBean
    private PushSender sender;

    @MockitoBean
    private WeekHeadline headlines;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private NotificationJobs jobs;

    private UUID userId;
    private RequestPostProcessor user;
    /** Uma quarta-feira só deste teste: a semana vai de segunda a domingo. */
    private LocalDate wednesday;

    static Clock clock() {
        return CLOCK;
    }

    @BeforeEach
    void freshUser() throws Exception {
        reset(sender, headlines);
        when(sender.enabled()).thenReturn(true);
        when(sender.send(anyString(), any())).thenReturn(PushSender.Result.SENT);
        when(headlines.of(any(), any(), any())).thenReturn(Optional.empty());
        wednesday = LocalDate.of(2043, 1, 7).plusWeeks(WEEKS.incrementAndGet());
        at(wednesday, "15:00");
        userId = accounts.create("push-" + UUID.randomUUID().toString().substring(0, 8), "x").id();
        user = TestUsers.as(userId);
        register("aparelho-" + userId, "ANDROID");
    }

    @Test
    void alertaDeOrcamentoUmaVezPorNivel() throws Exception {
        budget("FOOD", 100);
        spend("Gastei 85 reais no restaurante");

        jobs.budgetAlerts();
        jobs.budgetAlerts();

        ArgumentCaptor<PushSender.Message> sent = ArgumentCaptor.forClass(PushSender.Message.class);
        verify(sender, times(1)).send(eq("aparelho-" + userId), sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("Alimentação: 85% do limite da semana");

        spend("Gastei 20 reais no lanche");
        jobs.budgetAlerts();

        verify(sender, times(2)).send(eq("aparelho-" + userId), sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("Alimentação passou do limite da semana");
    }

    @Test
    void alertaDesligadoNasPreferenciasNaoVai() throws Exception {
        settings(false, true, true, "direct");
        budget("FOOD", 100);
        spend("Gastei 90 reais no restaurante");

        jobs.budgetAlerts();

        verify(sender, never()).send(eq("aparelho-" + userId), any());
    }

    @Test
    void lembreteDoDiaSoParaQuemNaoLancouNada() throws Exception {
        at(wednesday, "21:00");
        jobs.dailyReminder();
        jobs.dailyReminder();
        verify(sender, times(1)).send(eq("aparelho-" + userId), any());

        at(wednesday.plusDays(1), "18:00");
        spend("Bebi 2 litros de água");
        at(wednesday.plusDays(1), "21:00");
        jobs.dailyReminder();
        verify(sender, times(1)).send(eq("aparelho-" + userId), any());
    }

    @Test
    void resumoDaSemanaNoUltimoDiaNoTomEscolhido() throws Exception {
        settings(true, true, true, "scold");
        when(headlines.of(userId, wednesday.minusDays(2), "scold")).thenReturn(Optional.of("Semana fraca de água."));

        at(wednesday, "20:00");
        jobs.weeklySummary();
        verify(sender, never()).send(eq("aparelho-" + userId), any());

        at(wednesday.plusDays(4), "20:00");
        jobs.weeklySummary();
        ArgumentCaptor<PushSender.Message> sent = ArgumentCaptor.forClass(PushSender.Message.class);
        verify(sender).send(eq("aparelho-" + userId), sent.capture());
        assertThat(sent.getValue().body()).isEqualTo("Semana fraca de água.");
        assertThat(sent.getValue().data()).containsEntry("route", "/resumo");
    }

    @Test
    void tokenInvalidoSaiDaListaEFalhaTentaDeNovo() throws Exception {
        when(sender.send(eq("aparelho-" + userId), any())).thenReturn(PushSender.Result.FAILED);
        at(wednesday, "21:00");

        jobs.dailyReminder();
        jobs.dailyReminder();
        verify(sender, times(2)).send(eq("aparelho-" + userId), any());

        when(sender.send(eq("aparelho-" + userId), any())).thenReturn(PushSender.Result.INVALID_TOKEN);
        jobs.dailyReminder();
        jobs.dailyReminder();
        verify(sender, times(3)).send(eq("aparelho-" + userId), any());
    }

    @Test
    void semFirebaseNadaEEnviado() {
        when(sender.enabled()).thenReturn(false);
        at(wednesday, "21:00");

        jobs.dailyReminder();

        verify(sender, never()).send(eq("aparelho-" + userId), any());
    }

    @Test
    void sairDaContaTiraOAparelho() throws Exception {
        mvc.perform(delete("/api/v1/notifications/devices").with(user).param("token", "aparelho-" + userId))
                .andExpect(status().isNoContent());
        at(wednesday, "21:00");

        jobs.dailyReminder();

        verify(sender, never()).send(eq("aparelho-" + userId), any());
    }

    @Test
    void aparelhoQuePassaParaOutraPessoaTrocaDeDono() throws Exception {
        UUID otherId = accounts.create("push2-" + UUID.randomUUID().toString().substring(0, 8), "x").id();
        mvc.perform(put("/api/v1/notifications/devices").with(TestUsers.as(otherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"aparelho-" + userId + "\", \"platform\": \"ANDROID\"}"))
                .andExpect(status().isNoContent());
        at(wednesday, "21:00");
        jobs.dailyReminder();

        // O aparelho recebe uma vez só, agora como do novo dono; o banco não guarda mais o antigo.
        verify(sender, times(1)).send(eq("aparelho-" + userId), any());
    }

    @Test
    void preferenciasComPadraoEValidacao() throws Exception {
        mvc.perform(get("/api/v1/notifications/settings").with(user))
                .andExpect(jsonPath("$.budgetAlerts").value(true))
                .andExpect(jsonPath("$.dailyReminder").value(true))
                .andExpect(jsonPath("$.weeklySummary").value(true))
                .andExpect(jsonPath("$.tone").value("direct"));
        settings(true, false, true, "gentle");
        mvc.perform(get("/api/v1/notifications/settings").with(user))
                .andExpect(jsonPath("$.dailyReminder").value(false))
                .andExpect(jsonPath("$.tone").value("gentle"));
        mvc.perform(put("/api/v1/notifications/settings").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetAlerts\": true, \"dailyReminder\": true, \"weeklySummary\": true, "
                                + "\"tone\": \"bravo\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/notifications/devices").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"x\", \"platform\": \"WINDOWS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsSoParaAsOrigensDoAppNativo() throws Exception {
        for (String origin : new String[] {"https://localhost", "capacitor://localhost"}) {
            mvc.perform(options("/api/v1/entries").header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
        mvc.perform(options("/api/v1/entries").header(HttpHeaders.ORIGIN, "https://site-qualquer.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private void at(LocalDate day, String time) {
        CLOCK.set(day.atTime(java.time.LocalTime.parse(time)).atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toInstant());
    }

    private void register(String token, String platform) throws Exception {
        mvc.perform(put("/api/v1/notifications/devices").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\", \"platform\": \"" + platform + "\"}"))
                .andExpect(status().isNoContent());
    }

    private void settings(boolean budget, boolean daily, boolean weekly, String tone) throws Exception {
        mvc.perform(put("/api/v1/notifications/settings").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetAlerts\": " + budget + ", \"dailyReminder\": " + daily
                                + ", \"weeklySummary\": " + weekly + ", \"tone\": \"" + tone + "\"}"))
                .andExpect(status().isOk());
    }

    private void budget(String category, int limit) throws Exception {
        mvc.perform(put("/api/v1/budgets/{category}", category).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\": \"WEEKLY\", \"limit\": " + limit + "}"))
                .andExpect(status().isOk());
    }

    private void spend(String text) throws Exception {
        mvc.perform(post("/api/v1/entries").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"" + UUID.randomUUID() + "\", \"text\": \"" + text + "\", \"occurredAt\": \""
                                + CLOCK.instant() + "\"}"))
                .andExpect(status().isCreated());
    }
}
