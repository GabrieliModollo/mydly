package com.mydly.preferences.web;

import com.mydly.accounts.CurrentUser;
import com.mydly.preferences.internal.PreferencesRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preferências de quem está logado (ADR 0010 e ADR 0012). Hoje só o dia de início da semana.
 */
@RestController
@RequestMapping("/api/v1/preferences")
@Tag(name = "Preferências")
class PreferencesController {

    private final PreferencesRepository repository;
    private final CurrentUser currentUser;

    PreferencesController(PreferencesRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Preferências atuais")
    @Transactional(readOnly = true)
    Preferences get() {
        return new Preferences(repository.firstDayOfWeek(currentUser.id()));
    }

    @PutMapping
    @Operation(summary = "Troca as preferências",
            description = "O dia de início da semana vale para todas as semanas, inclusive as passadas, que passam "
                    + "a ser calculadas na definição nova. Depois da troca, use /weeks/current para achar a semana "
                    + "atual: um início de semana antigo passa a devolver 400.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Semana começando no domingo", value = "{\"firstDayOfWeek\": \"SUNDAY\"}"),
                    @ExampleObject(name = "Semana começando na segunda", value = "{\"firstDayOfWeek\": \"MONDAY\"}")})),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Preferências gravadas"),
                    @ApiResponse(responseCode = "400", description = "Dia ausente ou inválido")})
    @Transactional
    Preferences put(@Valid @RequestBody Preferences request) {
        UUID userId = currentUser.id();
        repository.saveFirstDayOfWeek(userId, request.firstDayOfWeek());
        return new Preferences(repository.firstDayOfWeek(userId));
    }

    @Schema(description = "Preferências do usuário")
    record Preferences(
            @Schema(description = "Dia em que a semana começa: MONDAY a SUNDAY", example = "MONDAY",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull DayOfWeek firstDayOfWeek) {
    }
}
