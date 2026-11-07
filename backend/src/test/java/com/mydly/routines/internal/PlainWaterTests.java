package com.mydly.routines.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code bebi 1 litro} sem dizer o que foi bebido conta como água (roteiro da autora, etapa 7). A regra
 * é conservadora: só o verbo {@code bebi}, a quantidade e a unidade de volume, e no máximo advérbios de
 * tempo. Qualquer complemento de bebida tira a frase da regra.
 */
class PlainWaterTests {

    @ParameterizedTest(name = "\"{0}\" ({1}) é água: {2}")
    @CsvSource(delimiter = '|', nullValues = "SEM", textBlock = """
            Bebi 1 litro                      | L    | true
            bebi 1 litro                      | L    | true
            bebi 500ml                        | mL   | true
            bebi 500 ml                       | mL   | true
            Bebi 2 litros hoje                | L    | true
            Bebi 1,5 litro agora              | L    | true
            Bebi 3 litros no total            | L    | true
            Bebi 2 litros e meio              | L    | true
            Bebi meio litro de manhã          | L    | true
            Eu bebi 1 litro.                  | L    | true
            # Complemento de bebida depois da quantidade.
            bebi 1 litro de cerveja           | L    | false
            bebi 500 ml de suco               | mL   | false
            bebi 2 litros de refrigerante     | L    | false
            Bebi 1 litro do suco da geladeira | L    | false
            Bebi 2 litros hoje de café        | L    | false
            # Outra unidade, outro verbo ou negação.
            Bebi 3 copos                      | copo | false
            Bebi 2 litros                     | SEM  | false
            Tomei 1 litro                     | L    | false
            Não bebi 1 litro                  | L    | false
            Corri 1 litro                     | L    | false
            """)
    void soOVolumeSemComplementoEAgua(String text, String unit, boolean water) {
        assertThat(HabitCatalog.isPlainWater(text, unit)).isEqualTo(water);
    }
}
