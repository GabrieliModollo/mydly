package com.mydly.entries.web;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    EntryController(EntryService service) {
        this.service = service;
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
                    @ExampleObject(name = "Enviado pelo app offline", value = "{\"id\": \"3f1c2a9e-8b7d-4c6a-9e2f-1a2b3c4d5e6f\", "
                            + "\"text\": \"Estudei durante 2 horas\", \"occurredAt\": \"2026-11-05T21:30:00Z\"}")})),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Lançamento criado"),
                    @ApiResponse(responseCode = "200", description = "Reenvio: o lançamento já existia com o mesmo conteúdo"),
                    @ApiResponse(responseCode = "400", description = "Texto vazio, longo demais ou JSON inválido"),
                    @ApiResponse(responseCode = "409", description = "O id já existe com outro texto ou outra data")})
    ResponseEntity<EntryResponse> create(@Valid @RequestBody CreateEntryRequest request) {
        EntryService.CreateResult result = service.create(request.id(), request.text(), request.occurredAt());
        return switch (result.outcome()) {
            case CREATED -> {
                EntryResponse body = EntryResponse.from(result.entry());
                URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(body.id()).toUri();
                yield ResponseEntity.created(location).body(body);
            }
            case ALREADY_EXISTS -> ResponseEntity.ok(EntryResponse.from(result.entry()));
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
        return service.listByDay(date).stream().map(EntryResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um lançamento pelo id", responses = {
            @ApiResponse(responseCode = "200", description = "Lançamento encontrado"),
            @ApiResponse(responseCode = "404", description = "Não existe lançamento com esse id")})
    EntryResponse find(@PathVariable UUID id) {
        return service.find(id)
                .map(EntryResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lançamento não encontrado"));
    }
}
