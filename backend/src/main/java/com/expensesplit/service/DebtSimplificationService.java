package com.expensesplit.service;

import com.expensesplit.dto.response.SettlementSuggestionResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Algoritmo greedy de simplificacion de deudas.
 *
 * <p>Dado el balance neto de cada usuario, calcula un conjunto reducido de
 * transacciones que deja a todo el grupo a paz y salvo.
 *
 * <p>Estrategia:
 * <ol>
 *   <li>separar a los usuarios en acreedores (balance &gt; 0) y deudores
 *       (balance &lt; 0)</li>
 *   <li>mantener ambos en colas de prioridad ordenadas por importe</li>
 *   <li>emparejar en cada paso al mayor deudor con el mayor acreedor y
 *       transferir el minimo de ambos importes</li>
 *   <li>devolver el resto a su cola y repetir hasta que una quede vacia</li>
 * </ol>
 *
 * <p>Cada paso salda por completo al menos a uno de los dos implicados, asi
 * que el numero de transacciones nunca supera n-1 para n participantes con
 * saldo. Es el optimo por participante; hallar el minimo absoluto de
 * transacciones es un problema NP-completo (equivale a particion de
 * conjuntos), y para grupos de tamano real esta heuristica da el mismo
 * resultado a coste lineal-logaritmico.
 *
 * <p>Trabaja sobre {@link UserBalance}, no sobre entidades JPA: es una
 * funcion pura, sin acceso a base de datos ni dependencia del contexto de
 * persistencia.
 */
@Service
public class DebtSimplificationService {

    /**
     * Umbral por debajo del cual un saldo se considera liquidado. Es medio
     * centimo: cualquier residuo menor no es representable como un pago real.
     */
    private static final BigDecimal EPSILON = new BigDecimal("0.005");

    public List<SettlementSuggestionResponse> simplify(List<UserBalance> netBalances) {
        // Max-heap por importe: al frente siempre el mayor acreedor y el
        // mayor deudor, que es lo que exige la estrategia greedy.
        Comparator<UserBalance> mayorImportePrimero =
                Comparator.comparing(UserBalance::amount).reversed();

        PriorityQueue<UserBalance> acreedores = new PriorityQueue<>(mayorImportePrimero);
        PriorityQueue<UserBalance> deudores = new PriorityQueue<>(mayorImportePrimero);

        for (UserBalance balance : netBalances) {
            if (balance.amount().compareTo(EPSILON) > 0) {
                acreedores.add(balance);
            } else if (balance.amount().negate().compareTo(EPSILON) > 0) {
                // Los deudores se guardan en valor absoluto para que el
                // comparador ordene igual en ambas colas.
                deudores.add(enValorAbsoluto(balance));
            }
            // Entre -0.005 y 0.005 se considera saldado y se ignora.
        }

        List<SettlementSuggestionResponse> transacciones = new ArrayList<>();

        while (!acreedores.isEmpty() && !deudores.isEmpty()) {
            UserBalance acreedor = acreedores.poll();
            UserBalance deudor = deudores.poll();

            BigDecimal importe = acreedor.amount().min(deudor.amount());

            transacciones.add(SettlementSuggestionResponse.builder()
                    .fromUserId(deudor.userId())
                    .fromUserName(deudor.userName())
                    .toUserId(acreedor.userId())
                    .toUserName(acreedor.userName())
                    .amount(importe)
                    .build());

            devolverSiQuedaSaldo(acreedores, acreedor, importe);
            devolverSiQuedaSaldo(deudores, deudor, importe);
        }

        return transacciones;
    }

    private void devolverSiQuedaSaldo(PriorityQueue<UserBalance> cola,
                                      UserBalance balance,
                                      BigDecimal transferido) {
        BigDecimal resto = balance.amount().subtract(transferido);

        if (resto.compareTo(EPSILON) > 0) {
            cola.add(new UserBalance(balance.userId(), balance.userName(), resto));
        }
    }

    private UserBalance enValorAbsoluto(UserBalance balance) {
        return new UserBalance(balance.userId(), balance.userName(), balance.amount().abs());
    }
}
