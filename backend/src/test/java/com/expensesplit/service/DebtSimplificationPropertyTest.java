package com.expensesplit.service;

import com.expensesplit.dto.response.SettlementSuggestionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propiedades invariantes del algoritmo de simplificacion de deudas.
 *
 * <p>{@link DebtSimplificationServiceTest} cubre casos escogidos a mano, que
 * son buenos para explicar el comportamiento pero solo prueban las
 * situaciones que a uno se le ocurrieron. Esta clase comprueba lo que debe
 * cumplirse <b>siempre</b>, sobre miles de repartos que nadie eligio:
 *
 * <ol>
 *   <li>aplicar las transacciones deja a todos exactamente a cero;</li>
 *   <li>nunca se sugieren mas de n-1 transacciones para n participantes con
 *       saldo;</li>
 *   <li>ningun importe sugerido es cero ni negativo;</li>
 *   <li>nadie se paga a si mismo;</li>
 *   <li>cada acreedor recibe exactamente lo que se le debe;</li>
 *   <li>el resultado es determinista.</li>
 * </ol>
 *
 * <p>La semilla del generador es fija y esta escrita en el codigo. Un test
 * aleatorio que falla y no se puede reproducir es peor que no tenerlo: da una
 * alarma que nadie sabe investigar.
 */
class DebtSimplificationPropertyTest {

    /** Fecha en que se escribio esta clase; cualquier constante serviria. */
    private static final long SEMILLA = 20_260_827L;

    private static final int REPETICIONES_POR_TAMANO = 200;

    private final DebtSimplificationService service = new DebtSimplificationService();

    @Nested
    @DisplayName("Sobre repartos aleatorios")
    class Aleatorios {

        @ParameterizedTest(name = "{0} participantes")
        @ValueSource(ints = {2, 3, 5, 8, 20})
        @DisplayName("se cumplen todas las invariantes")
        void invariantes(int participantes) {
            Random random = new Random(SEMILLA + participantes);

            for (int caso = 0; caso < REPETICIONES_POR_TAMANO; caso++) {
                List<UserBalance> balances = balancesAleatorios(participantes, random);
                List<SettlementSuggestionResponse> transacciones = service.simplify(balances);

                String contexto = "caso %d con %d participantes: %s"
                        .formatted(caso, participantes, describir(balances));

                comprobarInvariantes(balances, transacciones, contexto);
            }
        }

        @Test
        @DisplayName("tambien con importes muy pequenos, donde el centimo pesa")
        void importesMinusculos() {
            Random random = new Random(SEMILLA);

            for (int caso = 0; caso < 500; caso++) {
                // Saldos de como mucho unos pocos centimos: es donde el
                // redondeo tiene mas peso relativo y donde un algoritmo
                // descuidado deja residuos.
                List<UserBalance> balances = balancesAleatorios(4, random, 10);
                List<SettlementSuggestionResponse> transacciones = service.simplify(balances);

                comprobarInvariantes(balances, transacciones,
                        "caso minusculo %d: %s".formatted(caso, describir(balances)));
            }
        }

        @Test
        @DisplayName("y con grupos grandes de saldos dispares")
        void gruposGrandes() {
            Random random = new Random(SEMILLA);

            for (int caso = 0; caso < 50; caso++) {
                List<UserBalance> balances = balancesAleatorios(50, random, 1_000_000);
                List<SettlementSuggestionResponse> transacciones = service.simplify(balances);

                comprobarInvariantes(balances, transacciones, "caso grande " + caso);
            }
        }
    }

    @Nested
    @DisplayName("Casos limite")
    class Limites {

        @Test
        @DisplayName("un grupo ya saldado no genera ninguna transaccion")
        void grupoSaldado() {
            List<UserBalance> balances = List.of(
                    UserBalance.of(1L, "A", new BigDecimal("0.00")),
                    UserBalance.of(2L, "B", new BigDecimal("0.00")),
                    UserBalance.of(3L, "C", new BigDecimal("0.00")));

            assertThat(service.simplify(balances)).isEmpty();
        }

        @ParameterizedTest(name = "{0} participantes")
        @ValueSource(ints = {2, 3, 5, 20})
        @DisplayName("una sola persona pago todo: n-1 transacciones hacia ella")
        void unaPersonaPagoTodo(int participantes) {
            List<UserBalance> balances = new ArrayList<>();
            BigDecimal cuota = new BigDecimal("25.00");
            BigDecimal total = cuota.multiply(BigDecimal.valueOf(participantes - 1L));

            balances.add(UserBalance.of(1L, "Pagadora", total));
            for (int i = 2; i <= participantes; i++) {
                balances.add(UserBalance.of((long) i, "Deudor " + i, cuota.negate()));
            }

            List<SettlementSuggestionResponse> transacciones = service.simplify(balances);

            assertThat(transacciones).hasSize(participantes - 1);
            assertThat(transacciones).allMatch(t -> t.getToUserId() == 1L);
            comprobarInvariantes(balances, transacciones, "una persona pago todo");
        }

        @Test
        @DisplayName("saldos de un solo centimo se liquidan igualmente")
        void centimosSueltos() {
            List<UserBalance> balances = List.of(
                    UserBalance.of(1L, "A", new BigDecimal("0.01")),
                    UserBalance.of(2L, "B", new BigDecimal("0.01")),
                    UserBalance.of(3L, "C", new BigDecimal("-0.02")));

            List<SettlementSuggestionResponse> transacciones = service.simplify(balances);

            // Con el EPSILON de 0.01 que tenia la version original, estos
            // saldos se habrian descartado como si ya estuvieran saldados.
            assertThat(transacciones).hasSize(2);
            comprobarInvariantes(balances, transacciones, "centimos sueltos");
        }

        @Test
        @DisplayName("un unico participante con saldo cero no genera nada")
        void unicoParticipante() {
            assertThat(service.simplify(List.of(
                    UserBalance.of(1L, "Sola", new BigDecimal("0.00"))))).isEmpty();
        }

        @Test
        @DisplayName("una lista vacia no revienta")
        void listaVacia() {
            assertThat(service.simplify(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Determinismo")
    class Determinismo {

        @Test
        @DisplayName("los mismos balances producen siempre el mismo resultado")
        void mismoResultado() {
            Random random = new Random(SEMILLA);

            for (int caso = 0; caso < 100; caso++) {
                List<UserBalance> balances = balancesAleatorios(6, random);

                // Si el resultado variase entre llamadas, la pantalla de
                // liquidaciones cambiaria sola al refrescar.
                assertThat(describir(service.simplify(balances)))
                        .isEqualTo(describir(service.simplify(balances)));
            }
        }
    }

    // --- comprobacion de las invariantes ---

    private void comprobarInvariantes(List<UserBalance> balances,
                                       List<SettlementSuggestionResponse> transacciones,
                                       String contexto) {

        long conSaldo = balances.stream().filter(b -> b.amount().signum() != 0).count();

        // 2. Cada transaccion salda por completo al menos a uno de los dos
        //    implicados, asi que nunca hacen falta mas de n-1.
        assertThat(transacciones)
                .as("numero de transacciones (%s)", contexto)
                .hasSizeLessThanOrEqualTo((int) Math.max(0, conSaldo - 1));

        for (SettlementSuggestionResponse t : transacciones) {
            // 3. Un importe cero o negativo no es una transaccion, es ruido.
            assertThat(t.getAmount().signum())
                    .as("importe positivo (%s)", contexto)
                    .isPositive();

            // 4. Nadie se paga a si mismo.
            assertThat(t.getFromUserId())
                    .as("origen y destino distintos (%s)", contexto)
                    .isNotEqualTo(t.getToUserId());
        }

        // 5. Cada acreedor recibe exactamente lo suyo, ni mas ni menos.
        Map<Long, BigDecimal> recibido = new LinkedHashMap<>();
        transacciones.forEach(t -> recibido.merge(t.getToUserId(), t.getAmount(), BigDecimal::add));

        for (UserBalance b : balances) {
            if (b.amount().signum() > 0) {
                assertThat(recibido.getOrDefault(b.userId(), BigDecimal.ZERO))
                        .as("lo cobrado por el acreedor %d (%s)", b.userId(), contexto)
                        .isEqualByComparingTo(b.amount());
            }
        }

        // 1. Aplicarlas deja a todo el mundo exactamente a cero. Es la
        //    promesa que la aplicacion le hace al usuario.
        Map<Long, BigDecimal> saldos = new LinkedHashMap<>();
        balances.forEach(b -> saldos.put(b.userId(), b.amount()));

        for (SettlementSuggestionResponse t : transacciones) {
            saldos.merge(t.getFromUserId(), t.getAmount(), BigDecimal::add);
            saldos.merge(t.getToUserId(), t.getAmount().negate(), BigDecimal::add);
        }

        assertThat(saldos.values())
                .as("saldos tras aplicar las transacciones (%s)", contexto)
                .allSatisfy(saldo -> assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO));
    }

    // --- generacion ---

    private List<UserBalance> balancesAleatorios(int participantes, Random random) {
        return balancesAleatorios(participantes, random, 20_000);
    }

    /**
     * Genera saldos aleatorios en centimos que suman exactamente cero, que es
     * la condicion que cumplen siempre los balances reales de un grupo.
     */
    private List<UserBalance> balancesAleatorios(int participantes, Random random, int maxCentimos) {
        List<Long> centimos = new ArrayList<>(participantes);
        long acumulado = 0;

        for (int i = 0; i < participantes - 1; i++) {
            long valor = random.nextInt(2 * maxCentimos + 1) - maxCentimos;
            centimos.add(valor);
            acumulado += valor;
        }
        // El ultimo cierra la suma a cero.
        centimos.add(-acumulado);

        List<UserBalance> balances = new ArrayList<>(participantes);
        for (int i = 0; i < participantes; i++) {
            balances.add(UserBalance.of((long) (i + 1), "U" + (i + 1),
                    BigDecimal.valueOf(centimos.get(i), 2)));
        }
        return balances;
    }

    private String describir(List<?> elementos) {
        return elementos.toString();
    }
}
