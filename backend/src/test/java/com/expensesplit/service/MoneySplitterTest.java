package com.expensesplit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneySplitterTest {

    /**
     * La propiedad que da sentido a toda la clase: repartir nunca cambia el
     * total. Se comprueba de forma exhaustiva sobre una malla amplia de
     * importes y numeros de participantes, no sobre casos escogidos a mano.
     */
    @Nested
    @DisplayName("Invariante: la suma de las partes es exactamente el total")
    class SumaExacta {

        @Test
        @DisplayName("para todo importe de 0.01 a 200.00 y de 1 a 25 participantes")
        void exhaustivo() {
            for (long cents = 1; cents <= 20_000; cents++) {
                BigDecimal total = BigDecimal.valueOf(cents, 2);

                for (int parts = 1; parts <= 25; parts++) {
                    List<BigDecimal> shares = MoneySplitter.splitEqually(total, parts);

                    BigDecimal suma = shares.stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    assertThat(suma)
                            .as("%s repartido entre %d", total, parts)
                            .isEqualByComparingTo(total);
                }
            }
        }

        @ParameterizedTest(name = "{0} entre {1}")
        @CsvSource({
                // Los casos concretos que fallaban con el reparto anterior.
                "100.00, 3", "100.00, 6", "100.00, 7",
                "10.00, 3", "10.00, 6", "10.00, 7",
                "0.10, 3", "0.10, 6", "0.10, 7",
                "99.99, 6", "99.99, 7",
                // Importes grandes, cerca del limite de NUMERIC(12,2).
                "9999999999.99, 7", "9999999999.99, 13",
                // Un solo centimo entre muchos.
                "0.01, 50",
        })
        void casosQueAntesDescuadraban(BigDecimal total, int parts) {
            List<BigDecimal> shares = MoneySplitter.splitEqually(total, parts);

            assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(total);
        }
    }

    @Nested
    @DisplayName("Reparto equitativo")
    class Equidad {

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 5, 7, 11, 20, 100})
        @DisplayName("nadie paga mas de un centimo por encima de otro")
        void diferenciaMaximaDeUnCentimo(int parts) {
            List<BigDecimal> shares = MoneySplitter.splitEqually(new BigDecimal("100.00"), parts);

            BigDecimal maximo = shares.stream().max(BigDecimal::compareTo).orElseThrow();
            BigDecimal minimo = shares.stream().min(BigDecimal::compareTo).orElseThrow();

            assertThat(maximo.subtract(minimo)).isLessThanOrEqualTo(new BigDecimal("0.01"));
        }

        @Test
        @DisplayName("division exacta: todos pagan lo mismo y nadie lleva centimo extra")
        void divisionExacta() {
            List<BigDecimal> shares = MoneySplitter.splitEqually(new BigDecimal("90.00"), 3);

            assertThat(shares).containsExactly(
                    new BigDecimal("30.00"), new BigDecimal("30.00"), new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("el centimo sobrante recae en los primeros participantes")
        void residuoAlPrincipio() {
            // 100.00 entre 3 = 33.33 base, sobra 0.01 -> al primero
            assertThat(MoneySplitter.splitEqually(new BigDecimal("100.00"), 3))
                    .containsExactly(
                            new BigDecimal("33.34"),
                            new BigDecimal("33.33"),
                            new BigDecimal("33.33"));

            // 100.00 entre 6 = 16.66 base, sobran 0.04 -> a los cuatro primeros
            assertThat(MoneySplitter.splitEqually(new BigDecimal("100.00"), 6))
                    .containsExactly(
                            new BigDecimal("16.67"), new BigDecimal("16.67"),
                            new BigDecimal("16.67"), new BigDecimal("16.67"),
                            new BigDecimal("16.66"), new BigDecimal("16.66"));
        }

        @Test
        @DisplayName("un solo participante asume el importe integro")
        void participanteUnico() {
            assertThat(MoneySplitter.splitEqually(new BigDecimal("42.37"), 1))
                    .containsExactly(new BigDecimal("42.37"));
        }

        @Test
        @DisplayName("importes menores que el numero de participantes: algunos pagan cero")
        void importeMenorQueParticipantes() {
            List<BigDecimal> shares = MoneySplitter.splitEqually(new BigDecimal("0.03"), 5);

            assertThat(shares).containsExactly(
                    new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.01"),
                    new BigDecimal("0.00"), new BigDecimal("0.00"));
            assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(new BigDecimal("0.03"));
        }
    }

    @Nested
    @DisplayName("Determinismo y forma del resultado")
    class Contrato {

        @Test
        @DisplayName("dos llamadas iguales producen el mismo reparto")
        void esDeterminista() {
            BigDecimal total = new BigDecimal("77.77");

            assertThat(MoneySplitter.splitEqually(total, 9))
                    .isEqualTo(MoneySplitter.splitEqually(total, 9));
        }

        @Test
        @DisplayName("devuelve tantas partes como participantes, todas con escala 2")
        void formaDelResultado() {
            List<BigDecimal> shares = MoneySplitter.splitEqually(new BigDecimal("13.13"), 4);

            assertThat(shares).hasSize(4).allSatisfy(s -> assertThat(s.scale()).isEqualTo(2));
        }

        @Test
        @DisplayName("normaliza importes con mas de dos decimales")
        void masDeDosDecimales() {
            // 10.005 se normaliza a 10.01 (HALF_UP) antes de repartir
            List<BigDecimal> shares = MoneySplitter.splitEqually(new BigDecimal("10.005"), 2);

            assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(new BigDecimal("10.01"));
        }
    }

    @Nested
    @DisplayName("Entradas invalidas")
    class Validacion {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10})
        void participantesNoPositivos(int parts) {
            assertThatThrownBy(() -> MoneySplitter.splitEqually(BigDecimal.TEN, parts))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("al menos un participante");
        }

        @Test
        void importeNegativo() {
            assertThatThrownBy(() -> MoneySplitter.splitEqually(new BigDecimal("-1.00"), 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        void importeNulo() {
            assertThatThrownBy(() -> MoneySplitter.splitEqually(null, 2))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
