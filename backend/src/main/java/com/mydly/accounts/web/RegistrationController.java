package com.mydly.accounts.web;

import com.mydly.accounts.internal.RegistrationException;
import com.mydly.accounts.internal.RegistrationService;
import com.mydly.accounts.web.AuthController.LoginResponse;
import com.mydly.accounts.web.AuthController.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de conta (ADR 0018). Fica fora do {@link AuthController} para o login e o cadastro mudarem
 * sem um atrapalhar o outro; os dois devolvem a mesma resposta.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Login")
class RegistrationController {

    private final RegistrationService registrations;

    RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Cria uma conta e já entra",
            description = "Usuário de 3 a 40 letras sem acento, números, ponto, hífen ou sublinhado (sem diferença "
                    + "entre maiúsculas e minúsculas) e senha de pelo menos 8 caracteres. Devolve o mesmo token do "
                    + "login. Uma recusa traz o motivo no campo code.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Conta nova", value = "{\"username\": \"ana\", \"password\": \"uma-senha-boa\"}")})),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Conta criada, com o token"),
                    @ApiResponse(responseCode = "400", description = "Usuário inválido ou senha curta ou longa demais"),
                    @ApiResponse(responseCode = "409", description = "Usuário já existe")})
    LoginResponse register(@RequestBody RegisterRequest request) {
        var token = registrations.register(request.username(), request.password());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.of(token.account()));
    }

    /** A recusa vira um problema (RFC 9457) com o motivo em {@code code}, que o app traduz. */
    @ExceptionHandler
    ResponseEntity<ProblemDetail> refused(RegistrationException refusal) {
        HttpStatus status = refusal.reason() == RegistrationException.Reason.USERNAME_TAKEN
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, refusal.getMessage());
        problem.setProperty("code", refusal.reason().name());
        return ResponseEntity.status(status).body(problem);
    }

    @Schema(description = "Usuário e senha da conta nova")
    record RegisterRequest(
            @Schema(example = "ana", requiredMode = Schema.RequiredMode.REQUIRED) String username,
            @Schema(example = "uma-senha-boa", requiredMode = Schema.RequiredMode.REQUIRED) String password) {

        @Override
        public String toString() {
            // A senha não aparece em log nem em mensagem de erro.
            return "RegisterRequest[]";
        }
    }
}
