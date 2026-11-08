package com.mydly.accounts.web;

import com.mydly.accounts.CurrentUser;
import com.mydly.accounts.internal.Account;
import com.mydly.accounts.internal.LoginBlockedException;
import com.mydly.accounts.internal.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Login com usuário e senha (ADR 0012). As contas vêm do cadastro ({@link RegistrationController}) e
 * o usuário {@code demo} vem da migração.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Login")
class AuthController {

    private final LoginService logins;
    private final CurrentUser currentUser;

    AuthController(LoginService logins, CurrentUser currentUser) {
        this.logins = logins;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Entra com usuário e senha",
            description = "Devolve o token de acesso. Mande-o nos outros pedidos no header "
                    + "Authorization: Bearer (no Swagger, pelo botão Authorize). Usuário inexistente e senha "
                    + "errada dão a mesma resposta.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Demonstração", value = "{\"username\": \"demo\", \"password\": \"demo\"}")})),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login feito"),
                    @ApiResponse(responseCode = "400", description = "Usuário ou senha em branco"),
                    @ApiResponse(responseCode = "401", description = "Usuário ou senha incorretos"),
                    @ApiResponse(responseCode = "429", description = "Senhas erradas demais; o header Retry-After "
                            + "diz em quantos segundos tentar de novo")})
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return logins.login(request.username(), request.password(), http.getRemoteAddr())
                .map(token -> new LoginResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.of(token.account())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha incorretos."));
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> blocked(LoginBlockedException blocked) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de login. Tente de novo em alguns minutos.");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(blocked.retryAfterSeconds())).body(problem);
    }

    @GetMapping("/me")
    @Operation(summary = "O usuário do token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "O usuário"),
                    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou vencido")})
    UserResponse me() {
        return logins.find(currentUser.id())
                .map(UserResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));
    }

    @Schema(description = "Usuário e senha")
    record LoginRequest(
            @Schema(example = "demo", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 40) String username,
            @Schema(example = "demo", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200) String password) {

        @Override
        public String toString() {
            // A senha não aparece em log nem em mensagem de validação.
            return "LoginRequest[]";
        }
    }

    @Schema(description = "Token de acesso e o usuário")
    record LoginResponse(
            @Schema(description = "JWT para o header Authorization") String accessToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(description = "Até quando o token vale") Instant expiresAt,
            UserResponse user) {
    }

    @Schema(description = "Usuário")
    record UserResponse(UUID id, @Schema(example = "demo") String username) {

        static UserResponse of(Account account) {
            return new UserResponse(account.id(), account.username());
        }
    }
}
