package com.expensesplit.service;

import com.expensesplit.dto.response.GroupAnalyticsResponse;
import com.expensesplit.model.Group;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.projection.CategoryAmount;
import com.expensesplit.repository.projection.MonthAmount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reparto del gasto de un grupo, agregado en la base de datos.
 *
 * <p>Dos consultas de agrupacion, coste independiente del numero de gastos.
 * Es el mismo enfoque que {@link BalanceService}: si la suma la hace SQL sobre
 * NUMERIC, no hay coma flotante ni recorridos en memoria por medio.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int SCALE = 2;

    private final ExpenseRepository expenseRepository;
    private final GroupAccessService groupAccess;

    @Transactional(readOnly = true)
    public GroupAnalyticsResponse getGroupAnalytics(Long groupId, String requesterEmail) {
        // El guardian ya devuelve la pertenencia con su grupo cargado: pedirlo
        // otra vez seria una consulta de mas.
        Group grupo = groupAccess.requireMember(groupId, requesterEmail).getGroup();

        List<CategoryAmount> porCategoria = expenseRepository.sumByCategory(groupId);
        List<MonthAmount> porMes = expenseRepository.sumByMonth(groupId);

        // El total sale de sumar las categorias, no de una tercera consulta:
        // toda partida esta en exactamente una categoria, asi que la suma es
        // la misma y se ahorra un viaje a la base.
        BigDecimal total = porCategoria.stream()
                .map(CategoryAmount::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, java.math.RoundingMode.HALF_UP);

        return GroupAnalyticsResponse.builder()
                .currency(grupo.getCurrency())
                .totalSpent(total)
                .byCategory(porCategoria.stream()
                        .map(fila -> GroupAnalyticsResponse.CategoryTotal.builder()
                                .category(fila.category())
                                .total(fila.total().setScale(SCALE, java.math.RoundingMode.HALF_UP))
                                .count(fila.count())
                                .build())
                        .toList())
                .byMonth(porMes.stream()
                        .map(fila -> GroupAnalyticsResponse.MonthTotal.builder()
                                .month(formatearMes(fila))
                                .total(fila.total().setScale(SCALE, java.math.RoundingMode.HALF_UP))
                                .count(fila.count())
                                .build())
                        .toList())
                .build();
    }

    /** {@code 2026-9} no sirve: el cliente ordena y agrupa por esta cadena. */
    private static String formatearMes(MonthAmount fila) {
        return "%d-%02d".formatted(fila.year(), fila.month());
    }
}
