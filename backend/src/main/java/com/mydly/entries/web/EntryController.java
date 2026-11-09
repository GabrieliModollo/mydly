package com.mydly.entries.web;

import com.mydly.accounts.CurrentUser;
import com.mydly.entries.internal.EntryResolutionException;
import com.mydly.entries.internal.EntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/entries")
@Tag(name = "Lançamentos", description = "Lançamentos em texto livre, interpretados como finança ou rotina")
class EntryController {

    private final EntryService service;
    private final CurrentUser currentUser;

    EntryController(EntryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(
            summary = "Registra um lançamento",
            description = "Interpreta o texto e guarda o lançamento. É idempotente pelo id: reenviar o mesmo "
                    + "lançamento (mesmo id, mesmo texto e, se informada, mesma data) devolve 200 com o registro "
                    + "original, sem reprocessar. O mesmo id com outro conteúdo devolve 409.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Gasto", value = "{\"text\": \"Gastei 50 reais comprando energético na farmácia\"}"),
                    @ExampleObject(name = "Só o valor", value = "{\"text\": \"50 reais\"}"),
                    @ExampleObject(name = "Rotina com quantidade", value = "{\"text\": \"Bebi 2 litros de água\"}"),
                    @ExampleObject(name = "Rotina sem número", value = "{\"text\": \"Dormi bem\"}"),
                    @ExampleObject(name = "Precisa de confirmação", value = "{\"text\": \"Comprei pão\"}"),
                    @ExampleObject(name = "Frase composta", value = "{\"text\": \"Fui à academia e gastei 30 no açaí\"}"),
                    @ExampleObject(name = "Enviado pelo app offline", value = "{\"id\": \"3f1c2a9e-8b7d-4c6a-9e2f-1a2b3c4d5e6f\", "
                            + "\"text\": \"Estudei durante 2 horas\", \"occurredAt\": \"2026-11-05T21:30:00Z\"}")})),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Lançamento criado"),
                    @ApiResponse(responseCode = "200", description = "Reenvio: o lançamento já existia com o mesmo conteúdo"),
                    @ApiResponse(responseCode = "400", description = "Texto vazio, longo demais ou JSON inválido"),
                    @ApiResponse(responseCode = "409", description = "O id já existe com outro texto ou outra data")})
    ResponseEntity<EntryResponse> create(@Valid @RequestBody CreateEntryRequest request) {
        EntryService.CreateResult result = service.create(currentUser.id(), request.id(), request.text(),
                request.occurredAt());
        return switch (result.outcome()) {
            case CREATED -> {
                EntryResponse body = EntryResponse.from(result.entry());
                URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(body.id()).toUri();
                yield ResponseEntity.created(location).eTag(body.etag()).body(body);
            }
            case ALREADY_EXISTS -> {
                EntryResponse body = EntryResponse.from(result.entry());
                yield ResponseEntity.ok().eTag(body.etag()).body(body);
            }
            case CONFLICT -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um lançamento com este id e outro conteúdo. Para enviar um lançamento novo, use outro id.");
        };
    }

    @GetMapping
    @Operation(summary = "Lista os lançamentos de um dia",
            description = "Filtra pela data em que o usuário escreveu (occurredAt), no fuso America/Sao_Paulo, "
                    + "do mais recente para o mais antigo.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lançamentos do dia (pode ser vazio)"),
                    @ApiResponse(responseCode = "400", description = "Data em formato inválido")})
    List<EntryResponse> listByDay(
            @Parameter(description = "Dia no formato AAAA-MM-DD; se omitido, hoje", example = "2026-11-05")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.listByDay(currentUser.id(), date).stream().map(EntryResponse::from).toList();
    }

    @GetMapping("/pending")
    @Operation(summary = "Lista os lançamentos de outros dias que ainda pedem confirmação",
            description = "Os lançamentos escritos antes de hoje (no fuso America/Sao_Paulo) com algum item "
                    + "pendente, do mais recente para o mais antigo, até 50. Os de hoje vêm na listagem do dia.",
            responses = @ApiResponse(responseCode = "200", description = "Lançamentos pendentes (pode ser vazio)"))
    List<EntryResponse> pending() {
        return service.pendingBeforeToday(currentUser.id()).stream().map(EntryResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um lançamento pelo id", responses = {
            @ApiResponse(responseCode = "200", description = "Lançamento encontrado"),
            @ApiResponse(responseCode = "404", description = "Não existe lançamento com esse id")})
    ResponseEntity<EntryResponse> find(@PathVariable UUID id) {
        EntryResponse body = service.find(currentUser.id(), id)
                .map(EntryResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lançamento não encontrado"));
        return ResponseEntity.ok().eTag(body.etag()).body(body);
    }

    @PostMapping("/{id}/items/{index}/resolution")
    @Operation(
            summary = "Resolve ou reabre um item",
            description = "Confirma um item pendente como finança ou rotina, ou o descarta. REOPEN faz um item "
                    + "aceito ou descartado voltar a esperar confirmação (Não foi isso / Desfazer) e o tira de "
                    + "finance e routines (ADR 0008). O que o parser entendeu continua guardado (ADR 0007). "
                    + "Exige If-Match com a versão lida (ETag), para não resolver um item que mudou. O lançamento "
                    + "fica NEEDS_INPUT enquanto algum item estiver pendente e CONFIRMED quando nenhum estiver.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Confirmar gasto", value =
                            "{\"action\": \"CONFIRM_FINANCE\", \"amount\": 8.50, \"category\": \"FOOD\"}"),
                    @ExampleObject(name = "Confirmar entrada", value =
                            "{\"action\": \"CONFIRM_FINANCE\", \"amount\": 100, \"direction\": \"INCOME\"}"),
                    @ExampleObject(name = "Confirmar rotina", value =
                            "{\"action\": \"CONFIRM_ROUTINE\", \"quantity\": 2, \"unit\": \"L\"}"),
                    @ExampleObject(name = "Não foi um gasto", value = "{\"action\": \"DISMISS\"}"),
                    @ExampleObject(name = "Não foi isso", value = "{\"action\": \"REOPEN\"}")})),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item resolvido; o ETag traz a versão nova"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos, como valor menor ou igual a zero"),
                    @ApiResponse(responseCode = "404", description = "Lançamento ou item inexistente"),
                    @ApiResponse(responseCode = "409", description = "Item já resolvido ou que não precisa de confirmação"),
                    @ApiResponse(responseCode = "412", description = "O lançamento mudou desde a leitura (If-Match desatualizado)"),
                    @ApiResponse(responseCode = "428", description = "Faltou o header If-Match")})
    ResponseEntity<EntryResponse> resolve(
            @PathVariable UUID id,
            @PathVariable int index,
            @Parameter(description = "Versão lida do lançamento, como veio no ETag", example = "\"0\"")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ResolveItemRequest request) {
        long expectedVersion = parseVersion(ifMatch);
        try {
            EntryResponse body = EntryResponse.from(service.resolve(currentUser.id(), id, index, expectedVersion,
                    request.toCommand()));
            return ResponseEntity.ok().eTag(body.etag()).body(body);
        } catch (EntryResolutionException e) {
            throw new ResponseStatusException(statusFor(e.reason()), e.getMessage());
        }
    }

    private static long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Envie o header If-Match com a versão do lançamento (o ETag recebido).");
        }
        String value = ifMatch.strip().replaceFirst("^W/", "").replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "If-Match não corresponde a uma versão.");
        }
    }

    private static HttpStatus statusFor(EntryResolutionException.Reason reason) {
        return switch (reason) {
            case ENTRY_NOT_FOUND, ITEM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VERSION_MISMATCH -> HttpStatus.PRECONDITION_FAILED;
            case NOT_PENDING, ALREADY_PENDING -> HttpStatus.CONFLICT;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
    }
}
