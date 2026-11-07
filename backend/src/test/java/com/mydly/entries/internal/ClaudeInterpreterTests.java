package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * O pedido ao Claude e a leitura da resposta, sem rede (ADR 0013): o cliente da Anthropic é simulado.
 */
class ClaudeInterpreterTests {

    private static final String MODEL = "claude-opus-5-5";

    private final AnthropicClient client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
    private final ClaudeInterpreter interpreter = new ClaudeInterpreter(client, MODEL);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void pedeSaidaEstruturadaComEsforcoBaixoESoOTextoDoLancamento() throws Exception {
        MessageCreateParams params = interpreter.params("Gastei cinquenta reais", List.of("Gastei cinquenta reais"));

        assertThat(params.model().asString()).isEqualTo(MODEL);
        assertThat(params.maxTokens()).isEqualTo(ClaudeInterpreter.MAX_TOKENS);
        assertThat(params.system().flatMap(MessageCreateParams.System::string)).contains(ClaudeInterpreter.SYSTEM_PROMPT);
        OutputConfig output = params.outputConfig().orElseThrow();
        assertThat(output.effort()).contains(OutputConfig.Effort.LOW);
        assertThat(output.format()).isPresent();

        // A mensagem do usuário é só a frase e os trechos: nenhum id, nome ou outro dado da pessoa (LGPD).
        String sent = params.messages().getFirst().content().string().orElseThrow();
        JsonNode body = json.readTree(sent);
        assertThat(body.fieldNames()).toIterable().containsExactly("frase", "trechos");
        assertThat(body.get("frase").asText()).isEqualTo("Gastei cinquenta reais");
        assertThat(body.get("trechos").get(0).get("index").asInt()).isZero();
        assertThat(body.get("trechos").get(0).get("trecho").asText()).isEqualTo("Gastei cinquenta reais");
    }

    @Test
    void oEsquemaExigeTodosOsCamposSemExtras() throws Exception {
        JsonNode schema = json.valueToTree(ClaudeInterpreter.SCHEMA._additionalProperties());
        JsonNode reading = schema.at("/properties/readings/items");

        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        assertThat(reading.get("additionalProperties").asBoolean()).isFalse();
        assertThat(reading.get("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("index", "type", "direction", "amount", "category", "outcome", "quantity",
                        "unit", "clear");
        assertThat(reading.at("/properties/category/anyOf/0/enum")).extracting(JsonNode::asText).contains("FOOD", "OTHER");
    }

    @Test
    void leAsLeiturasDaResposta() {
        answer(StopReason.END_TURN, """
                {"readings": [{"index": 0, "type": "FINANCE", "direction": "EXPENSE", "amount": 50, "category": "FOOD",
                  "outcome": null, "quantity": null, "unit": null, "clear": true}]}""");

        List<LlmReading> readings = interpreter.read("Gastei cinquenta reais", List.of("Gastei cinquenta reais"))
                .orElseThrow();

        assertThat(readings).singleElement().satisfies(reading -> {
            assertThat(reading.type()).isEqualTo("FINANCE");
            assertThat(reading.amount()).isEqualByComparingTo("50");
            assertThat(reading.clear()).isTrue();
        });
    }

    @Test
    void recusaOuRespostaCortadaFicaComAsRegras() {
        answer(StopReason.REFUSAL, "");
        assertThat(interpreter.read("x", List.of("x"))).isEmpty();

        answer(StopReason.MAX_TOKENS, "{\"readings\": [");
        assertThat(interpreter.read("x", List.of("x"))).isEmpty();
    }

    @Test
    void respostaForaDoEsquemaFicaComAsRegras() {
        answer(StopReason.END_TURN, "não é JSON");

        assertThat(interpreter.read("x", List.of("x"))).isEmpty();
    }

    @Test
    void falhaDeRedeOuLimiteDeUsoFicaComAsRegras() {
        when(client.messages().create(any(MessageCreateParams.class))).thenThrow(new AnthropicIoException("sem rede"));
        assertThat(interpreter.read("x", List.of("x"))).isEmpty();

        Mockito.reset(client);
        when(client.messages().create(any(MessageCreateParams.class))).thenThrow(mock(RateLimitException.class));
        assertThat(interpreter.read("x", List.of("x"))).isEmpty();
    }

    @Test
    void mandaOsTrechosNaOrdem() throws Exception {
        answer(StopReason.END_TURN, "{\"readings\": []}");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        interpreter.read("Fui à academia e comprei pão", List.of("comprei pão", "outro"));

        Mockito.verify(client.messages()).create(captor.capture());
        JsonNode body = json.readTree(captor.getValue().messages().getFirst().content().string().orElseThrow());
        assertThat(body.get("trechos")).extracting(node -> node.get("trecho").asText())
                .containsExactly("comprei pão", "outro");
    }

    private void answer(StopReason stop, String text) {
        TextBlock textBlock = mock(TextBlock.class);
        when(textBlock.text()).thenReturn(text);
        ContentBlock block = mock(ContentBlock.class);
        when(block.text()).thenReturn(Optional.of(textBlock));
        Message message = mock(Message.class);
        when(message.stopReason()).thenReturn(Optional.of(stop));
        when(message.content()).thenReturn(List.of(block));
        when(client.messages().create(any(MessageCreateParams.class))).thenReturn(message);
    }
}
