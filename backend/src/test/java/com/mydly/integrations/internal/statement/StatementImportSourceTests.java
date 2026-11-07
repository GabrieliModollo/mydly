package com.mydly.integrations.internal.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.mydly.entries.Direction;
import com.mydly.integrations.ExternalTransaction;
import com.mydly.integrations.InvalidSourceException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Leitura de extratos OFX e CSV (ADR 0014), com arquivos no formato dos bancos: OFX 1.x em SGML e
 * Windows-1252, OFX 2.x em XML, CSV de conta, fatura de cartão e CSV com ponto e vírgula.
 */
class StatementImportSourceTests {

    @Test
    void ofx1EmSgmlComAcentosEmWindows1252() throws IOException {
        StatementImportSource source = read("extrato-conta-ofx1.ofx");

        assertThat(source.format()).isEqualTo(StatementImportSource.Format.OFX);
        List<ExternalTransaction> transactions = source.fetch();
        // A transação de valor zero fica de fora.
        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0)).isEqualTo(new ExternalTransaction("ofx:12345-6:fitid:20261103001",
                LocalDate.of(2026, 11, 3), new BigDecimal("50.00"), Direction.EXPENSE, "DROGASIL 1234 SÃO PAULO"));
        assertThat(transactions.get(1).direction()).isEqualTo(Direction.INCOME);
        assertThat(transactions.get(1).amount()).isEqualByComparingTo("3000");
        assertThat(transactions.get(1).description()).isEqualTo("SALARIO EMPRESA X");
        assertThat(transactions.get(2).description()).isEqualTo("PADARIA PÃO & CAFÉ");
    }

    @Test
    void ofx2EmXmlDeCartaoComDuasComprasIguais() throws IOException {
        List<ExternalTransaction> transactions = read("fatura-cartao-ofx2.ofx").fetch();

        assertThat(transactions).extracting(ExternalTransaction::externalId)
                .containsExactly("ofx:5555:fitid:abc-1", "ofx:5555:fitid:abc-2");
        assertThat(transactions).allSatisfy(transaction -> {
            assertThat(transaction.amount()).isEqualByComparingTo("30");
            assertThat(transaction.direction()).isEqualTo(Direction.EXPENSE);
            assertThat(transaction.description()).isEqualTo("Açaí do Zé");
        });
    }

    @Test
    void csvDeContaComIdentificadorEAspas() throws IOException {
        StatementImportSource source = read("extrato-conta.csv");

        assertThat(source.format()).isEqualTo(StatementImportSource.Format.CSV);
        List<ExternalTransaction> transactions = source.fetch();
        assertThat(transactions).extracting(ExternalTransaction::externalId)
                .containsExactly("csv:id:6a1f-01", "csv:id:6a1f-02", "csv:id:6a1f-03");
        assertThat(transactions.get(0).direction()).isEqualTo(Direction.EXPENSE);
        assertThat(transactions.get(1).direction()).isEqualTo(Direction.INCOME);
        assertThat(transactions.get(2).description()).isEqualTo("Padaria Pão, Café");
    }

    @Test
    void faturaDeCartaoTemACompraPositivaEDuasComprasIguaisContamDuasVezes() throws IOException {
        List<ExternalTransaction> transactions = read("fatura-cartao.csv").fetch();

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).direction()).isEqualTo(Direction.EXPENSE);
        assertThat(transactions.get(0).externalId()).isNotEqualTo(transactions.get(1).externalId());
        assertThat(transactions.get(1).externalId()).isEqualTo(transactions.get(0).externalId() + "#2");
        assertThat(transactions.get(2).direction()).isEqualTo(Direction.INCOME);
        assertThat(transactions.get(2).amount()).isEqualByComparingTo("500");
    }

    @Test
    void csvComPontoEVirgulaEValorBrasileiro() throws IOException {
        List<ExternalTransaction> transactions = read("extrato-ponto-e-virgula.csv").fetch();

        assertThat(transactions.get(0).amount()).isEqualByComparingTo("1234.56");
        assertThat(transactions.get(0).direction()).isEqualTo(Direction.EXPENSE);
        assertThat(transactions.get(0).description()).isEqualTo("COMPRA CARTAO DROGARIA");
        assertThat(transactions.get(1).amount()).isEqualByComparingTo("250");
    }

    @Test
    void lerDeNovoOMesmoArquivoDaOsMesmosIds() throws IOException {
        for (String file : List.of("extrato-ponto-e-virgula.csv", "fatura-cartao.csv", "extrato-conta-ofx1.ofx")) {
            assertThat(read(file).fetch()).isEqualTo(read(file).fetch());
        }
    }

    @ParameterizedTest(name = "{0} vira {1}")
    @CsvSource(delimiter = '|', nullValues = "NADA", value = {
            "-1.234,56 | -1234.56",
            "1.234,56 | 1234.56",
            "1,234.56 | 1234.56",
            "50,00 | 50.00",
            "R$ 12,90 | 12.90",
            "-12.90 | -12.90",
            "abc | NADA",
            "'' | NADA"})
    void valores(String raw, BigDecimal expected) {
        Optional<BigDecimal> parsed = StatementText.amount(raw);
        if (expected == null) {
            assertThat(parsed).isEmpty();
        } else {
            assertThat(parsed).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo(expected));
        }
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "'' | O arquivo está vazio.",
            "Nome;Idade\\nAna;30 | O CSV precisa de uma coluna de data e uma de valor, com cabeçalho.",
            "Data;Valor\\n31/02/2026;10,00 | Linha 2: data inválida.",
            "Data;Valor\\n01/02/2026;dez | Linha 2: valor inválido.",
            "Data;Valor\\n01/02/2026;0,00 | Nenhuma transação encontrada no arquivo."})
    void arquivoInvalidoDizOMotivoSemMostrarOConteudo(String content, String message) {
        assertThatExceptionOfType(InvalidSourceException.class)
                .isThrownBy(() -> StatementImportSource.of(content.replace("\\n", "\n").getBytes(StandardCharsets.UTF_8),
                        "extrato.csv").fetch())
                .withMessage(message);
    }

    @Test
    void ofxQuebradoComExtensaoOfx() {
        assertThatExceptionOfType(InvalidSourceException.class)
                .isThrownBy(() -> StatementImportSource.of("não é ofx".getBytes(StandardCharsets.UTF_8), "extrato.OFX"))
                .withMessage("O arquivo não parece um OFX válido.");
    }

    @Test
    void ofxSemDataValida() {
        String ofx = "OFXHEADER:100\n<OFX><STMTTRN>\n<DTPOSTED>2026\n<TRNAMT>-1.00\n</STMTTRN></OFX>";

        assertThatExceptionOfType(InvalidSourceException.class)
                .isThrownBy(() -> StatementImportSource.of(ofx.getBytes(StandardCharsets.UTF_8), "x.ofx").fetch())
                .withMessage("Transação 1 do OFX sem data válida.");
    }

    @Test
    void descricaoLongaECortada() {
        String csv = "Data;Valor;Descrição\n01/02/2026;-1,00;" + "x".repeat(300);

        ExternalTransaction transaction = StatementImportSource.of(csv.getBytes(StandardCharsets.UTF_8), "a.csv")
                .fetch().getFirst();

        assertThat(transaction.description()).hasSize(ExternalTransaction.MAX_DESCRIPTION);
    }

    private static StatementImportSource read(String name) throws IOException {
        try (InputStream in = StatementImportSourceTests.class.getResourceAsStream("/statements/" + name)) {
            return StatementImportSource.of(in.readAllBytes(), name);
        }
    }
}
