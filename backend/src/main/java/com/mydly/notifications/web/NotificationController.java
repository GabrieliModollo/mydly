package com.mydly.notifications.web;

import com.mydly.accounts.CurrentUser;
import com.mydly.notifications.internal.NotificationRepository;
import com.mydly.notifications.internal.NotificationRepository.Settings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificações push (ADR 0016): o app cadastra o aparelho e escolhe o que quer receber. O envio
 * acontece no servidor, pelo Firebase, quando ele está configurado.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificações")
class NotificationController {

    private final NotificationRepository repository;
    private final CurrentUser currentUser;

    NotificationController(NotificationRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @PutMapping("/devices")
    @Operation(summary = "Cadastra o aparelho para receber push",
            description = "O token é o do Firebase Cloud Messaging. Se o aparelho era de outro usuário (outra pessoa "
                    + "entrou no app), ele passa para quem está logado.")
    @Transactional
    ResponseEntity<Void> register(@Valid @RequestBody DeviceRequest request) {
        repository.saveDevice(currentUser.id(), request.token(), request.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices")
    @Operation(summary = "Para de mandar push para o aparelho",
            description = "Usado ao sair da conta e ao desligar as notificações.")
    @Transactional
    ResponseEntity<Void> unregister(@RequestParam @NotBlank String token) {
        repository.removeDevice(currentUser.id(), token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    @Operation(summary = "O que o usuário quer receber")
    @Transactional(readOnly = true)
    SettingsBody settings() {
        return SettingsBody.of(repository.settings(currentUser.id()));
    }

    @PutMapping("/settings")
    @Operation(summary = "Escolhe o que receber",
            description = "Alerta de orçamento (80% e acima do limite), lembrete das 21h quando nada foi lançado no dia e "
                    + "resumo da semana no último dia dela, no tom escolhido.")
    @Transactional
    SettingsBody saveSettings(@Valid @RequestBody SettingsBody request) {
        UUID userId = currentUser.id();
        repository.saveSettings(userId, new Settings(request.budgetAlerts(), request.dailyReminder(),
                request.weeklySummary(), request.tone()));
        return SettingsBody.of(repository.settings(userId));
    }

    @Schema(description = "Aparelho que recebe push")
    record DeviceRequest(
            @Schema(description = "Token do Firebase Cloud Messaging", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 512) String token,
            @Schema(description = "ANDROID ou IOS", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Pattern(regexp = "ANDROID|IOS") String platform) {

        @Override
        public String toString() {
            // O token não aparece em log nem em mensagem de validação.
            return "DeviceRequest[platform=" + platform + "]";
        }
    }

    @Schema(description = "O que o usuário quer receber")
    record SettingsBody(@NotNull Boolean budgetAlerts, @NotNull Boolean dailyReminder, @NotNull Boolean weeklySummary,
                        @Schema(description = "gentle, direct ou scold", example = "direct")
                        @NotNull @Pattern(regexp = "gentle|direct|scold") String tone) {

        static SettingsBody of(Settings settings) {
            return new SettingsBody(settings.budgetAlerts(), settings.dailyReminder(), settings.weeklySummary(),
                    settings.tone());
        }
    }
}
