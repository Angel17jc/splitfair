package com.expensesplit.service;

import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.model.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Algoritmo greedy de simplificacion de deudas.
 *
 * Dado el balance neto de cada usuario en un grupo, calcula el numero minimo
 * de transacciones necesarias para que todos queden a paz y salvo.
 *
 * Estrategia:
 * 1. Separar usuarios en acreedores (balance > 0) y deudores (balance < 0)
 * 2. Usar dos colas de prioridad (max-heap) ordenadas por monto absoluto
 * 3. En cada iteracion, emparejar al mayor deudor con el mayor acreedor
 * 4. Transferir el monto minimo entre ambos, actualizar balances, repetir
 */
@Service
public class DebtSimplificationService {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    public List<SettlementSuggestionResponse> simplify(Map<User, BigDecimal> netBalances) {
        PriorityQueue<Balance> creditors = new PriorityQueue<>(
                Comparator.comparing(Balance::amount).reversed());
        PriorityQueue<Balance> debtors = new PriorityQueue<>(
                Comparator.comparing(Balance::amount).reversed());

        for (Map.Entry<User, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal amount = entry.getValue().setScale(2, java.math.RoundingMode.HALF_UP);

            if (amount.compareTo(EPSILON) > 0) {
                creditors.add(new Balance(entry.getKey(), amount));
            } else if (amount.compareTo(EPSILON.negate()) < 0) {
                debtors.add(new Balance(entry.getKey(), amount.abs()));
            }
            // si esta entre -0.01 y 0.01 se considera saldado, se ignora
        }

        List<SettlementSuggestionResponse> settlements = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Balance topCreditor = creditors.poll();
            Balance topDebtor = debtors.poll();

            BigDecimal transferAmount = topCreditor.amount().min(topDebtor.amount());

            settlements.add(SettlementSuggestionResponse.builder()
                    .fromUserId(topDebtor.user().getId())
                    .fromUserName(topDebtor.user().getName())
                    .toUserId(topCreditor.user().getId())
                    .toUserName(topCreditor.user().getName())
                    .amount(transferAmount)
                    .build());

            BigDecimal remainingCreditor = topCreditor.amount().subtract(transferAmount);
            BigDecimal remainingDebtor = topDebtor.amount().subtract(transferAmount);

            if (remainingCreditor.compareTo(EPSILON) > 0) {
                creditors.add(new Balance(topCreditor.user(), remainingCreditor));
            }
            if (remainingDebtor.compareTo(EPSILON) > 0) {
                debtors.add(new Balance(topDebtor.user(), remainingDebtor));
            }
        }

        return settlements;
    }

    private record Balance(User user, BigDecimal amount) {
    }
}
