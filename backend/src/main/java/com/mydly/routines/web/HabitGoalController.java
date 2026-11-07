package com.mydly.routines.web;

import com.mydly.accounts.CurrentUser;
import com.mydly.routines.GoalKind;
import com.mydly.routines.internal.HabitGoalService;
import com.mydly.routines.internal.InvalidGoalException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Metas dos hábitos do catálogo (ADR 0010). Toda mudança vale de hoje em diante, no fuso do usuário:
 * os dias passados continuam avaliados pela meta que valia neles.
 */
@RestController
@RequestMapping("/api/v1/goals/habits")
@Tag(name = "Metas de hábito")
class HabitGoalController {

    private static final String NOT_FOUND = "Hábito fora do catálogo.";

    private final HabitGoalService service;
    private final CurrentUser currentUser;

    HabitGoalController(HabitGoalService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Hábitos do catálogo com a meta de hoje",
            description = "Todos os hábitos do catálogo, com a polaridade, a medida, os tipos de meta aceitos e a "
                    + "meta vigente hoje (ausente quando o hábito está sem meta).")
    List<HabitGoalResponse> all() {
        return service.all(currentUser.id()).stream().map(HabitGoalResponse::of).toList();
    }

    @GetMapping("/{habitId}")
    @Operation(summary = "Meta de um hábito", responses = {
            @ApiResponse(responseCode = "200", description = "O hábito e a meta vigente hoje"),
            @ApiResponse(responseCode = "404", description = "Hábito fora do catálogo")})
    HabitGoalResponse get(@HabitParameter @PathVariable String habitId) {
        return service.find(currentUser.id(), habitId).map(HabitGoalResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    @PutMapping("/{habitId}")
    @Operation(summary = "Define a meta de um hábito",
            description = "Grava uma versão nova da meta a partir de hoje (America/Sao_Paulo). Uma segunda gravação "
                    + "no mesmo dia substitui a primeira. DAILY_QUANTITY só vale para hábito bom com medida, com "
                    + "unidade da mesma dimensão (L ou mL na hidratação; h ou min no estudo, sono, leitura e "
                    + "academia). Num hábito ruim, WEEKLY_FREQUENCY é um limite: no máximo n dias por semana, de 0 a 7.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "2 L de água por dia",
                            value = "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"L\"}"),
                    @ExampleObject(name = "30 min de leitura por dia",
                            value = "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 30, \"unit\": \"min\"}"),
                    @ExampleObject(name = "Academia 4 vezes por semana",
                            value = "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 4}"),
                    @ExampleObject(name = "Limite de 2 dias de delivery",
                            value = "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 2}")})),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Meta gravada; a resposta traz a meta vigente"),
                    @ApiResponse(responseCode = "400", description = "Tipo, unidade ou valor que não combinam com o hábito"),
                    @ApiResponse(responseCode = "404", description = "Hábito fora do catálogo")})
    HabitGoalResponse put(@HabitParameter @PathVariable String habitId, @Valid @RequestBody GoalRequest request) {
        try {
            return service.set(currentUser.id(), habitId, request.kind(), request.targetQuantity(), request.unit(), request.daysPerWeek())
                    .map(HabitGoalResponse::of)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        } catch (InvalidGoalException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{habitId}")
    @Operation(summary = "Tira a meta de um hábito",
            description = "Grava a versão sem meta a partir de hoje; os dias passados continuam com a meta que "
                    + "valia neles. Sem meta vigente, não muda nada e também devolve 204.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "O hábito fica sem meta a partir de hoje"),
                    @ApiResponse(responseCode = "404", description = "Hábito fora do catálogo")})
    ResponseEntity<Void> delete(@HabitParameter @PathVariable String habitId) {
        if (!service.remove(currentUser.id(), habitId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }

    @Schema(description = "Meta de um hábito")
    record GoalRequest(
            @Schema(description = "DAILY_QUANTITY ou WEEKLY_FREQUENCY", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull GoalKind kind,
            @Schema(description = "DAILY_QUANTITY: quantidade por dia, maior que zero, até duas casas", example = "2")
            BigDecimal targetQuantity,
            @Schema(description = "DAILY_QUANTITY: L, mL, h ou min", example = "L") String unit,
            @Schema(description = "WEEKLY_FREQUENCY: 1 a 7 num hábito bom; 0 a 7 (limite) num hábito ruim",
                    example = "4") Integer daysPerWeek) {
    }

    /** Documentação comum do código do hábito. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
    @Parameter(description = "Código do hábito do catálogo", example = "HYDRATION")
    @interface HabitParameter {
    }
}
