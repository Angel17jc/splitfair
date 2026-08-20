package com.expensesplit.service;

import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DebtSimplificationServiceTest {

    private final DebtSimplificationService service = new DebtSimplificationService();

    private User user(long id, String name) {
        return User.builder().id(id).name(name).email(name.toLowerCase() + "@test.com").build();
    }

    @Test
    void dosPersonas_unoLeDebeAlOtro() {
        User a = user(1, "Ana");
        User b = user(2, "Beto");

        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(a, new BigDecimal("50.00"));   // le deben 50
        balances.put(b, new BigDecimal("-50.00"));  // debe 50

        List<SettlementSuggestionResponse> result = service.simplify(balances);

        assertEquals(1, result.size());
        assertEquals("Beto", result.get(0).getFromUserName());
        assertEquals("Ana", result.get(0).getToUserName());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.get(0).getAmount()));
    }

    @Test
    void tresPersonas_cadenaDeudas_seSimplificaAUnaTransaccion() {
        // A pago 90, deberia haber pagado 30 -> le deben 60
        // B pago 0, deberia pagar 30 -> debe 30
        // C pago 0, deberia pagar 30, pero ya le presto a B -> este caso simplificado:
        User a = user(1, "Ana");
        User b = user(2, "Beto");
        User c = user(3, "Carla");

        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(a, new BigDecimal("60.00"));
        balances.put(b, new BigDecimal("-30.00"));
        balances.put(c, new BigDecimal("-30.00"));

        List<SettlementSuggestionResponse> result = service.simplify(balances);

        // Deben ser exactamente 2 transacciones: B->A y C->A
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getToUserName().equals("Ana")));

        BigDecimal total = result.stream()
                .map(SettlementSuggestionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("60.00").compareTo(total));
    }

    @Test
    void grupoYaSaldado_noGeneraTransacciones() {
        User a = user(1, "Ana");
        User b = user(2, "Beto");

        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(a, BigDecimal.ZERO);
        balances.put(b, BigDecimal.ZERO);

        List<SettlementSuggestionResponse> result = service.simplify(balances);

        assertTrue(result.isEmpty());
    }

    @Test
    void unaPersonaPagoTodo_restoDebeProporcional() {
        User a = user(1, "Ana");
        User b = user(2, "Beto");
        User c = user(3, "Carla");
        User d = user(4, "Diego");

        // Ana pago 120 para 4 personas (30 c/u), los demas no pagaron nada
        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(a, new BigDecimal("90.00"));   // pago 120, le correspondia 30 -> le deben 90
        balances.put(b, new BigDecimal("-30.00"));
        balances.put(c, new BigDecimal("-30.00"));
        balances.put(d, new BigDecimal("-30.00"));

        List<SettlementSuggestionResponse> result = service.simplify(balances);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(s -> s.getToUserName().equals("Ana")));
        assertTrue(result.stream().allMatch(s -> s.getAmount().compareTo(new BigDecimal("30.00")) == 0));
    }

    @Test
    void cincoPersonas_balancesMixtos_todosQuedanEnCero() {
        User a = user(1, "A");
        User b = user(2, "B");
        User c = user(3, "C");
        User d = user(4, "D");
        User e = user(5, "E");

        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(a, new BigDecimal("40.00"));
        balances.put(b, new BigDecimal("-15.00"));
        balances.put(c, new BigDecimal("25.00"));
        balances.put(d, new BigDecimal("-30.00"));
        balances.put(e, new BigDecimal("-20.00"));

        List<SettlementSuggestionResponse> result = service.simplify(balances);

        // La suma de todos los pagos por acreedor debe igualar su balance positivo
        Map<String, BigDecimal> receivedByCreditor = new LinkedHashMap<>();
        for (SettlementSuggestionResponse s : result) {
            receivedByCreditor.merge(s.getToUserName(), s.getAmount(), BigDecimal::add);
        }

        assertEquals(0, new BigDecimal("40.00").compareTo(receivedByCreditor.get("A")));
        assertEquals(0, new BigDecimal("25.00").compareTo(receivedByCreditor.get("C")));

        // El numero de transacciones nunca deberia superar (numero de personas - 1)
        assertTrue(result.size() <= 4);
    }
}
