package com.mydly.integrations.web;

import com.mydly.accounts.CurrentUser;
import com.mydly.integrations.internal.openfinance.OpenFinanceConnection;
import com.mydly.integrations.internal.openfinance.OpenFinanceException;
import com.mydly.integrations.internal.openfinance.OpenFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ProblemDetail;

/**
 * Open Finance pelo agregador Pluggy, só no sandbox (ADR 0015). O app pede um connect token, abre o
 * widget do Pluggy com ele e manda de volta o id do item criado.
 */
@RestController
@RequestMapping("/api/v1/open-finance")
@Tag(name = "Open Finance (sandbox)")
class OpenFinanceController {

    private final OpenFinanceService openFinance;
    private final CurrentUser currentUser;

    OpenFinanceController(OpenFinanceService openFinance, CurrentUser currentUser) {
        this.openFinance = openFinance;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Se o Open Finance está ligado neste servidor, e as conexões do usuário")
    StatusResponse status() {
        List<ConnectionResponse> list = openFinance.enabled()
                ? openFinance.list(currentUser.id()).stream().map(ConnectionResponse::of).toList()
                : List.of();
        return new StatusResponse(openFinance.enabled(), list);
    }

    @PostMapping("/connect-token")
    @Operation(summary = "Token para abrir o widget do Pluggy",
            description = "Vale 30 minutos e só pode criar uma conexão para quem pediu. As chaves do Pluggy ficam no "
                    + "servidor.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token criado"),
                    @ApiResponse(responseCode = "502", description = "O Pluggy não respondeu"),
                    @ApiResponse(responseCode = "503", description = "Open Finance não configurado")})
    TokenResponse connectToken() {
        return new TokenResponse(openFinance.connectToken(currentUser.id()));
    }

    @PostMapping("/connections")
    @Operation(summary = "Liga a conexão criada no widget e importa as transações",
            description = "Só bancos de teste (sandbox) são aceitos: uma conexão com banco real é desfeita no Pluggy e "
                    + "recusada. As transações passam pela mesma importação e reconciliação do extrato.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Conexão ligada, com o resumo da importação"),
                    @ApiResponse(responseCode = "400", description = "Não é sandbox, ou foi criada para outra conta"),
                    @ApiResponse(responseCode = "404", description = "O Pluggy não conhece o item"),
                    @ApiResponse(responseCode = "409", description = "A conexão já está ligada a outra conta")})
    SyncResponse connect(@Valid @RequestBody ConnectRequest request) {
        return SyncResponse.of(openFinance.connect(currentUser.id(), request.itemId()));
    }

    @PostMapping("/connections/{id}/sync")
    @Operation(summary = "Importa as transações novas da conexão",
            description = "Volta uma semana antes da última sincronização; sincronizar de novo não duplica nada. Se o "
                    + "banco ainda está atualizando no Pluggy, só o status muda.")
    SyncResponse sync(@PathVariable UUID id) {
        return SyncResponse.of(openFinance.sync(currentUser.id(), id));
    }

    @DeleteMapping("/connections/{id}")
    @Operation(summary = "Desconecta o banco",
            description = "Apaga a conexão no Pluggy e no MyDly. As transações já importadas continuam.")
    ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        openFinance.disconnect(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(OpenFinanceException.class)
    ResponseEntity<ProblemDetail> failure(OpenFinanceException failure) {
        HttpStatus status = switch (failure.kind()) {
            case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case REJECTED -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, failure.getMessage()));
    }

    @Schema(description = "Open Finance neste servidor")
    record StatusResponse(@Schema(description = "false sem as chaves do Pluggy") boolean enabled,
                          List<ConnectionResponse> connections) {
    }

    record TokenResponse(@Schema(description = "Connect token para o widget, válido por 30 minutos") String connectToken) {
    }

    @Schema(description = "O item criado pelo widget")
    record ConnectRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String itemId) {
    }

    @Schema(description = "Um banco conectado")
    record ConnectionResponse(UUID id, @Schema(example = "Pluggy Bank") String institution,
                              @Schema(description = "Status do item no Pluggy: UPDATED, UPDATING, LOGIN_ERROR, OUTDATED...")
                              String status,
                              Instant lastSyncedAt) {

        static ConnectionResponse of(OpenFinanceConnection connection) {
            return new ConnectionResponse(connection.id(), connection.institution(), connection.status(),
                    connection.lastSyncedAt());
        }
    }

    @Schema(description = "A conexão e o resumo da importação, quando houve")
    record SyncResponse(ConnectionResponse connection, ImportResponse imported) {

        static SyncResponse of(OpenFinanceService.Sync sync) {
            return new SyncResponse(ConnectionResponse.of(sync.connection()),
                    sync.summary().map(summary -> ImportResponse.of("OPEN_FINANCE", summary)).orElse(null));
        }
    }
}
