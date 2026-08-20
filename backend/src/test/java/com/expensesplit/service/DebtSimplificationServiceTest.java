package com.expensesplit.service;

import com.expensesplit.dto.response.SettlementSuggestionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DebtSimplificationServiceTest {

    private final DebtSimplificationService service = new DebtSimplificationService();

    private static UserBalance balance(long id, String nombre, String importe) {
        return new UserBalance(id, nombre, new BigDecimal(importe));
    }

    @Test
    @DisplayName("Dos personas: una unica transferencia del deudor al acreedor")
    void dosPersonas() {
        List<SettlementSuggestionResponse> result = service.simplify(List.of(
                balance(1, "Ana", "50.00"),
                balance(2, "Beto", "-50.00")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFromUserName()).isEqualTo("Beto");
        assertThat(result.get(0).getToUserName()).isEqualTo("Ana");
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Tres personas: dos deudores pagan a un unico acreedor")
    void tresPersonas() {
        List<SettlementSuggestionResponse> result = service.simplify(List.of(
                balance(1, "Ana", "60.00"),
                balance(2, "Beto", "-30.00"),
                balance(3, "Carla", "-30.00")));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(s -> s.getToUserName().equals("Ana"));
        assertThat(totalTransferido(result)).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("Grupo ya saldado: no se sugiere ninguna transaccion")
    void grupoSaldado() {
        assertThat(service.simplify(List.of(
                balance(1, "Ana", "0.00"),
                balance(2, "Beto", "0.00")))).isEmpty();
    }

    @Test
    @DisplayName("Una persona pago todo: los demas le pagan su parte")
    void unaPersonaPagoTodo() {
        List<SettlementSuggestionResponse> result = service.simplify(List.of(
                balance(1, "Ana", "90.00"),
                balance(2, "Beto", "-30.00"),
                balance(3, "Carla", "-30.00"),
                balance(4, "Diego", "-30.00")));

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(s -> s.getToUserName().equals("Ana"));
        assertThat(result).allMatch(s -> s.getAmount().compareTo(new BigDecimal("30.00")) == 0);
    }

    @Test
    @DisplayName("Cinco personas con balances mixtos: cada acreedor recibe lo suyo")
    void cincoPersonasBalancesMixtos() {
        List<SettlementSuggestionResponse> result = service.simplify(List.of(
                balance(1, "A", "40.00"),
                balance(2, "B", "-15.00"),
                balance(3, "C", "25.00"),
                balance(4, "D", "-30.00"),
                balance(5, "E", "-20.00")));

        Map<String, BigDecimal> recibido = new LinkedHashMap<>();
        result.forEach(s -> recibido.merge(s.getToUserName(), s.getAmount(), BigDecimal::add));

        assertThat(recibido.get("A")).isEqualByComparingTo("40.00");
        assertThat(recibido.get("C")).isEqualByComparingTo("25.00");
        assertThat(result).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("Los miembros con balance cero se ignoran, no generan transacciones")
    void miembrosSaldadosSeIgnoran() {
        List<SettlementSuggestionResponse> result = service.simplify(List.of(
                balance(1, "Ana", "20.00"),
                balance(2, "Beto", "-20.00"),
                balance(3, "Carla", "0.00"),
                balance(4, "Diego", "0.00")));

        assertThat(result).hasSize(1);
        assertThat(result).noneMatch(s ->
                s.getFromUserName().equals("Carla") || s.getToUserName().equals("Carla"));
    }

    @Test
    @DisplayName("Con centimos sueltos, lo transferido cuadra con lo adeudado")
    void centimosSueltos() {
        // 100.00 repartido entre 3: uno asume 33.34 y dos asumen 33.33
        List<SettlementSuggestionResponse> result = service.simplify(List.of(
                balance(1, "Ana", "66.66"),
                balance(2, "Beto", "-33.33"),
                balance(3, "Carla", "-33.33")));

        assertThat(totalTransferido(result)).isEqualByComparingTo("66.66");
    }

    @Test
    @DisplayName("Invariante: nunca mas de n-1 transacciones para n participantes con saldo")
    void nuncaSuperaNMenosUno() {
        List<UserBalance> balances = List.of(
                balance(1, "A", "100.00"),
                balance(2, "B", "-25.00"),
                balance(3, "C", "-25.00"),
                balance(4, "D", "-25.00"),
                balance(5, "E", "-25.00"),
                balance(6, "F", "50.00"),
                balance(7, "G", "-50.00"));

        assertThat(service.simplify(balances)).hasSizeLessThanOrEqualTo(balances.size() - 1);
    }

    @Test
    @DisplayName("Invariante: aplicar las transferencias deja a todos en cero")
    void aplicarTransferenciasSaldaElGrupo() {
        List<UserBalance> balances = List.of(
                balance(1, "A", "40.00"),
                balance(2, "B", "-15.00"),
                balance(3, "C", "25.00"),
                balance(4, "D", "-30.00"),
                balance(5, "E", "-20.00"));

        Map<Long, BigDecimal> resultante = new LinkedHashMap<>();
        balances.forEach(b -> resultante.put(b.userId(), b.amount()));

        for (SettlementSuggestionResponse t : service.simplify(balances)) {
            // Quien paga sube su balance; quien cobra lo baja.
            resultante.merge(t.getFromUserId(), t.getAmount(), BigDecimal::add);
            resultante.merge(t.getToUserId(), t.getAmount().negate(), BigDecimal::add);
        }

        assertThat(resultante.values()).allSatisfy(saldo ->
                assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Invariante: ninguna transferencia sugerida es de importe negativo")
    void sinImportesNegativos() {
        List<SettlementSuggestionResponse> result = service.simplify(List.of(
                balance(1, "A", "33.33"),
                balance(2, "B", "-11.11"),
                balance(3, "C", "-22.22")));

        assertThat(result).allMatch(s -> s.getAmount().signum() > 0);
    }

    private BigDecimal totalTransferido(List<SettlementSuggestionResponse> transacciones) {
        return transacciones.stream()
                .map(SettlementSuggestionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
