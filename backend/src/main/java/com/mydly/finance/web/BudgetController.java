package com.mydly.finance.web;

import com.mydly.entries.Category;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetWindow;
import com.mydly.finance.Budgets;
import com.mydly.finance.internal.BudgetService;
import com.mydly.preferences.UserCalendar;
import com.mydly.preferences.WeekPreferences;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * Orçamentos por categoria (ADR 0010). O progresso é o do período atual: a semana do usuário, que
 * segue o dia de início das preferências, ou o mês civil inteiro, no fuso America/Sao_Paulo.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Orçamentos")
class BudgetController {

    private final BudgetService service;
    private final Budgets budgets;
    private final WeekPreferences weeks;
    private final UserCalendar calendar;

    BudgetController(BudgetService service, Budgets budgets, WeekPreferences weeks, UserCalendar calendar) {
        this.service = service;
        this.budgets = budgets;
        this.weeks = weeks;
        this.calendar = calendar;
    }

    @GetMapping
    @Operation(summary = "Categorias com o orçamento e o gasto do período atual",
            description = "Todas as categorias fixas, na ordem do app. As sem orçamento vêm só com a categoria.")
    List<BudgetResponse> all() {
        Map<Category, BudgetProgress> progress = budgets.progress(currentWindow(), calendar.zone()).stream()
                .collect(Collectors.toMap(BudgetProgress::category, Function.identity()));
        return Arrays.stream(Category.values())
                .map(category -> progress.containsKey(category)
                        ? BudgetResponse.of(progress.get(category))
                        : BudgetResponse.empty(category))
                .toList();
    }

    @GetMapping("/{category}")
    @Operation(summary = "Orçamento de uma categoria", responses = {
            @ApiResponse(responseCode = "200", description = "A categoria e o orçamento vigente hoje, se houver"),
            @ApiResponse(responseCode = "404", description = "Categoria desconhecida")})
    BudgetResponse get(@CategoryParameter @PathVariable String category) {
        return response(parse(category));
    }

    @PutMapping("/{category}")
    @Operation(summary = "Define o limite de uma categoria",
            description = "Grava uma versão nova a partir de hoje (America/Sao_Paulo); uma segunda gravação no mesmo "
                    + "dia substitui a primeira. WEEKLY segue o dia de início da semana das preferências; MONTHLY é o "
                    + "mês civil. O aviso começa em 80% do limite e o estouro é acima de 100%.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "R$ 100 por semana", value = "{\"period\": \"WEEKLY\", \"limit\": 100}"),
                    @ExampleObject(name = "R$ 800 por mês", value = "{\"period\": \"MONTHLY\", \"limit\": 800}")})),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Limite gravado, com o gasto do período atual"),
                    @ApiResponse(responseCode = "400", description = "Período ausente ou limite menor ou igual a zero"),
                    @ApiResponse(responseCode = "404", description = "Categoria desconhecida")})
    BudgetResponse put(@CategoryParameter @PathVariable String category, @Valid @RequestBody BudgetRequest request) {
        Category parsed = parse(category);
        service.set(parsed, request.period(), request.limit());
        return response(parsed);
    }

    @DeleteMapping("/{category}")
    @Operation(summary = "Tira o limite de uma categoria",
            description = "Grava a versão sem limite a partir de hoje; os períodos passados continuam avaliados pelo "
                    + "limite que valia neles. Sem orçamento vigente, não muda nada e também devolve 204.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "A categoria fica sem limite a partir de hoje"),
                    @ApiResponse(responseCode = "404", description = "Categoria desconhecida")})
    ResponseEntity<Void> delete(@CategoryParameter @PathVariable String category) {
        service.remove(parse(category));
        return ResponseEntity.noContent().build();
    }

    private BudgetResponse response(Category category) {
        return budgets.progress(category, currentWindow(), calendar.zone())
                .map(BudgetResponse::of)
                .orElseGet(() -> BudgetResponse.empty(category));
    }

    /** O período atual: a semana de hoje nas preferências e o mês de hoje inteiro. */
    private BudgetWindow currentWindow() {
        LocalDate today = calendar.today();
        return BudgetWindow.wholePeriods(today, weeks.weekContaining(today).start());
    }

    private static Category parse(String category) {
        return Arrays.stream(Category.values())
                .filter(candidate -> candidate.name().equals(category))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria desconhecida."));
    }

    @Schema(description = "Limite de gasto de uma categoria")
    record BudgetRequest(
            @Schema(description = "WEEKLY ou MONTHLY", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull BudgetPeriod period,
            @Schema(description = "Limite em reais, maior que zero, até duas casas", example = "100",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal limit) {
    }

    /** Documentação comum da categoria. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Parameter(description = "Categoria fixa: FOOD, GROCERIES, PHARMACY, TRANSPORT, HOUSING, HEALTH, LEISURE, WORK ou OTHER",
            example = "FOOD")
    @interface CategoryParameter {
    }
}
