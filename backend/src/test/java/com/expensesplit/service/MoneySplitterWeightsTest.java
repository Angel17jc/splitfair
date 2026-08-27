package com.expensesplit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reparto proporcional por pesos, la base de los repartos por porcentaje y
 * por partes.
 *
 * <p>Tiene el mismo problema del centimo que el reparto igual, y peor:
 * calcular cada parte por separado y redondear produce descuadres en cuanto
 * los pesos tienen decimales. La invariante que hay que sostener es la misma:
 * <b>la suma de las partes es exactamente el total</b>.
 */
class MoneySplitterWeightsTest {

    private static BigDecimal suma(List<BigDecimal> partes) {
        return partes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<BigDecimal> pesos(String... valores) {
        return Stream.of(valores).map(BigDecimal::new).toList();
    }

    @Nested
    @DisplayName("Invariante: la suma de las partes es exactamente el total")
    class SumaExacta {

        @Test
        @DisplayName("exhaustivo sobre importes y repartos porcentuales de tres")
        void exhaustivoTresPorcentajes() {
            // Todos los repartos enteros de 100 entre tres, contra todos los
            // importes de 0.01 a 50.00.
            for (int a = 0; a <= 100; a++) {
                for (int b = 0; a + b <= 100; b++) {
                    int c = 100 - a - b;
                    List<BigDecimal> w = List.of(
                            BigDecimal.valueOf(a), BigDecimal.valueOf(b), BigDecimal.valueOf(c));

                    for (long cents : new long[]{1, 7, 100, 333, 1000, 4999, 5000}) {
                        BigDecimal total = BigDecimal.valueOf(cents, 2);

                        assertThat(suma(MoneySplitter.splitByWeights(total, w)))
                                .as("%s repartido %d/%d/%d", total, a, b, c)
                                .isEqualByComparingTo(total);
                    }
                }
            }
        }

        @Test
        @DisplayName("exhaustivo sobre importes con pesos iguales de 1 a 20 participantes")
        void exhaustivoPesosIguales() {
            for (int n = 1; n <= 20; n++) {
                List<BigDecimal> w = java.util.Collections.nCopies(n, BigDecimal.ONE);

                for (long cents = 1; cents <= 2000; cents++) {
                    BigDecimal total = BigDecimal.valueOf(cents, 2);

                    assertThat(suma(MoneySplitter.splitByWeights(total, w)))
                            .as("%s entre %d pesos iguales", total, n)
                            .isEqualByComparingTo(total);
                }
            }
        }

        @ParameterizedTest(name = "{0} con pesos {1}")
        @CsvSource({
                // Porcentajes con decimales, el caso que mas descuadra.
                "100.00, '33.33|33.33|33.34'",
                "100.00, '33.3333|33.3333|33.3334'",
                "10.00,  '16.66|16.67|16.67|16.66|16.67|16.67'",
                "0.01,   '50|50'",
                "0.03,   '25|25|25|25'",
                "9999999999.99, '1|1|1|1|1|1|1'",
                // Partes desiguales.
                "100.00, '2|1|1'",
                "7.77,   '3|2|1'",
                "0.05,   '1|1|1|1|1|1|1'",
                // Un solo participante se lo lleva todo.
                "42.37,  '1'",
                // Pesos con cero: esos participantes no pagan nada.
                "60.00,  '1|0|2'",
        })
        void casosDelicados(BigDecimal total, String pesosSeparados) {
            List<BigDecimal> w = Stream.of(pesosSeparados.split("\\|"))
                    .map(BigDecimal::new).toList();

            assertThat(suma(MoneySplitter.splitByWeights(total, w)))
                    .isEqualByComparingTo(total);
        }
    }

    @Nested
    @DisplayName("Proporcionalidad")
    class Proporcionalidad {

        @Test
        @DisplayName("el doble de partes paga el doble")
        void dobleDePartes() {
            assertThat(MoneySplitter.splitByWeights(new BigDecimal("100.00"), pesos("2", "1", "1")))
                    .containsExactly(
                            new BigDecimal("50.00"),
                            new BigDecimal("25.00"),
                            new BigDecimal("25.00"));
        }

        @Test
        @DisplayName("los porcentajes se aplican tal cual cuando son exactos")
        void porcentajesExactos() {
            assertThat(MoneySplitter.splitByWeights(new BigDecimal("200.00"), pesos("50", "30", "20")))
                    .containsExactly(
                            new BigDecimal("100.00"),
                            new BigDecimal("60.00"),
                            new BigDecimal("40.00"));
        }

        @Test
        @DisplayName("un peso de cero no recibe nada")
        void pesoCero() {
            assertThat(MoneySplitter.splitByWeights(new BigDecimal("90.00"), pesos("1", "0", "2")))
                    .containsExactly(
                            new BigDecimal("30.00"),
                            new BigDecimal("0.00"),
                            new BigDecimal("60.00"));
        }

        @Test
        @DisplayName("con pesos iguales coincide con el reparto igual")
        void coincideConSplitEqually() {
            BigDecimal total = new BigDecimal("100.00");

            assertThat(MoneySplitter.splitByWeights(total, pesos("1", "1", "1")))
                    .isEqualTo(MoneySplitter.splitEqually(total, 3));
        }

        @Test
        @DisplayName("el centimo sobrante va a quien tiene mayor resto, no al primero")
        void elCentimoVaAlMayorResto() {
            // 0.05 repartido 1:2 -> 1.666.. y 3.333.. centimos.
            // Las partes enteras son 1 y 3, sobra 1 centimo, y el mayor resto
            // (0.666 frente a 0.333) es el del primero.
            assertThat(MoneySplitter.splitByWeights(new BigDecimal("0.05"), pesos("1", "2")))
                    .containsExactly(new BigDecimal("0.02"), new BigDecimal("0.03"));
        }
    }

    @Nested
    @DisplayName("Determinismo y validacion")
    class Contrato {

        @Test
        @DisplayName("dos llamadas iguales dan el mismo reparto")
        void esDeterminista() {
            BigDecimal total = new BigDecimal("77.77");
            List<BigDecimal> w = pesos("1", "1", "1", "1", "1", "1", "1");

            assertThat(MoneySplitter.splitByWeights(total, w))
                    .isEqualTo(MoneySplitter.splitByWeights(total, w));
        }

        @Test
        @DisplayName("devuelve una parte por peso, todas con escala 2")
        void formaDelResultado() {
            assertThat(MoneySplitter.splitByWeights(new BigDecimal("13.13"), pesos("1", "2", "3")))
                    .hasSize(3)
                    .allSatisfy(parte -> assertThat(parte.scale()).isEqualTo(2));
        }

        @Test
        @DisplayName("una lista de pesos vacia se rechaza")
        void sinPesos() {
            assertThatThrownBy(() -> MoneySplitter.splitByWeights(BigDecimal.TEN, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("al menos un peso");
        }

        @Test
        @DisplayName("todos los pesos a cero se rechaza: no hay proporcion que aplicar")
        void todosLosPesosACero() {
            assertThatThrownBy(() -> MoneySplitter.splitByWeights(BigDecimal.TEN, pesos("0", "0")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mayor que cero");
        }

        @Test
        @DisplayName("un peso negativo se rechaza")
        void pesoNegativo() {
            assertThatThrownBy(() -> MoneySplitter.splitByWeights(BigDecimal.TEN, pesos("1", "-1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativos");
        }

        @Test
        @DisplayName("un importe negativo se rechaza")
        void importeNegativo() {
            assertThatThrownBy(() ->
                    MoneySplitter.splitByWeights(new BigDecimal("-1.00"), pesos("1", "1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }
    }
}
