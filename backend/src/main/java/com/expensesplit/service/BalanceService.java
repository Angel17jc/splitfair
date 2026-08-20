package com.expensesplit.service;

import com.expensesplit.model.GroupMember;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.ExpenseSplitRepository;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.projection.UserAmount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcula el balance neto de cada usuario dentro de un grupo.
 *
 * <pre>
 *   balance(usuario) = total_desembolsado - total_que_le_correspondia_asumir
 * </pre>
 *
 * <p>La suma se hace en la base de datos con dos consultas de agrupacion, no
 * recorriendo entidades. La version anterior cargaba todos los gastos del
 * grupo y navegaba sus colecciones perezosas, con tres problemas:
 *
 * <ul>
 *   <li>una consulta por gasto y otra por split (N+1)</li>
 *   <li>sin transaccion abierta, tocar esas colecciones lanzaba
 *       LazyInitializationException al servir peticiones reales</li>
 *   <li>los miembros que no participaron en ningun gasto no aparecian, en
 *       lugar de figurar con balance cero</li>
 * </ul>
 *
 * <p>Se parte de la lista de miembros y no de la de gastos precisamente para
 * que todo el grupo aparezca siempre, hayan gastado o no.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private static final int SCALE = 2;

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional(readOnly = true)
    public List<UserBalance> calculateNetBalances(Long groupId) {
        Map<Long, BigDecimal> pagado = indexar(expenseRepository.sumPaidByUser(groupId));
        Map<Long, BigDecimal> adeudado = indexar(expenseSplitRepository.sumOwedByUser(groupId));

        return groupMemberRepository.findByGroupId(groupId).stream()
                .map(member -> toBalance(member, pagado, adeudado))
                // Orden estable: acreedores primero, luego deudores. Sin un
                // criterio explicito el orden lo decidiria la base de datos y
                // la respuesta cambiaria entre llamadas identicas.
                .sorted(Comparator.comparing(UserBalance::amount).reversed()
                        .thenComparing(UserBalance::userId))
                .toList();
    }

    private UserBalance toBalance(GroupMember member,
                                  Map<Long, BigDecimal> pagado,
                                  Map<Long, BigDecimal> adeudado) {
        Long userId = member.getUser().getId();

        BigDecimal balance = pagado.getOrDefault(userId, BigDecimal.ZERO)
                .subtract(adeudado.getOrDefault(userId, BigDecimal.ZERO))
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new UserBalance(userId, member.getUser().getName(), balance);
    }

    private Map<Long, BigDecimal> indexar(List<UserAmount> totales) {
        return totales.stream()
                .collect(Collectors.toMap(UserAmount::userId, UserAmount::amount));
    }
}
