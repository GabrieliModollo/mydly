package com.mydly.integrations.web;

import com.mydly.accounts.CurrentUser;
import com.mydly.integrations.InvalidSourceException;
import com.mydly.integrations.internal.ImportService;
import com.mydly.integrations.internal.statement.StatementImportSource;
import com.mydly.preferences.UserCalendar;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Importação de extrato OFX ou CSV e as transações importadas (ADR 0014).
 */
@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Extrato")
class ImportController {

    /** Sem período, a lista mostra os últimos 60 dias. */
    static final int DEFAULT_DAYS = 60;

    private final ImportService imports;
    private final CurrentUser currentUser;
    private final UserCalendar calendar;

    ImportController(ImportService imports, CurrentUser currentUser, UserCalendar calendar) {
        this.imports = imports;
        this.currentUser = currentUser;
        this.calendar = calendar;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa um extrato OFX ou CSV",
            description = "Lê o arquivo (até 2 MB e 5.000 transações) e importa cada transação uma vez só: "
                    + "reimportar o mesmo arquivo não duplica nada. Uma transação com o mesmo valor e direção de um "
                    + "lançamento escrito no app, de um dia antes a três dias depois dele, fica ligada ao lançamento e "
                    + "não conta de novo. As outras entram nos números. No CSV, valor negativo é saída, menos na "
                    + "fatura de cartão no formato date title amount, em que a compra vem positiva.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Arquivo importado, com o resumo"),
                    @ApiResponse(responseCode = "400", description = "Arquivo vazio, em outro formato ou com linha inválida"),
                    @ApiResponse(responseCode = "413", description = "Arquivo maior que 2 MB")})
    ImportResponse importStatement(@RequestPart("file") MultipartFile file) {
        try {
            StatementImportSource source = StatementImportSource.of(file.getBytes(), file.getOriginalFilename());
            return ImportResponse.of(source.format().name(), imports.importFrom(currentUser.id(), source));
        } catch (InvalidSourceException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IOException unreadable) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo.");
        }
    }

    @GetMapping("/transactions")
    @Operation(summary = "Transações importadas",
            description = "Da mais nova à mais antiga. Sem período, os últimos 60 dias até hoje.")
    List<ImportedTransactionResponse> list(
            @Parameter(description = "Primeiro dia, AAAA-MM-DD") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último dia, AAAA-MM-DD") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate until = to != null ? to : calendar.today();
        LocalDate since = from != null ? from : until.minusDays(DEFAULT_DAYS - 1L);
        if (since.isAfter(until)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O primeiro dia vem depois do último.");
        }
        return imports.list(currentUser.id(), since, until).stream().map(ImportedTransactionResponse::of).toList();
    }

    @PutMapping("/transactions/{id}")
    @Operation(summary = "Conta ou deixa de contar uma transação importada",
            description = "ignored true tira a transação dos números (uma transferência entre contas suas, por "
                    + "exemplo). ignored false a traz de volta: ligada a um lançamento, se houver um que case, ou "
                    + "sozinha.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "A transação como ficou"),
                    @ApiResponse(responseCode = "404", description = "Transação inexistente ou de outro usuário")})
    ImportedTransactionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        UUID userId = currentUser.id();
        return (request.ignored() ? imports.ignore(userId, id) : imports.restore(userId, id))
                .map(ImportedTransactionResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transação não encontrada."));
    }

    @Schema(description = "Contar ou não uma transação importada")
    record UpdateRequest(@Schema(description = "true para não contar", requiredMode = Schema.RequiredMode.REQUIRED)
                         @NotNull Boolean ignored) {
    }
}
