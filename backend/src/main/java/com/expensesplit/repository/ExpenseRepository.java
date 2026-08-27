package com.expensesplit.repository;

import com.expensesplit.model.Expense;
import com.expensesplit.repository.projection.GroupAmount;
import com.expensesplit.repository.projection.UserAmount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * Trae los gastos con quien pago, sus splits y los usuarios de cada split
     * en una sola consulta. Sin el grafo, recorrer las colecciones perezosas
     * al construir la respuesta dispara una consulta por gasto y otra por
     * split (problema N+1).
     */
    @EntityGraph(attributePaths = {"paidBy", "splits", "splits.user"})
    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);

    /** Trae el gasto con todo lo que hace falta para editarlo o describirlo. */
    @EntityGraph(attributePaths = {"group", "paidBy", "splits", "splits.user"})
    Optional<Expense> findWithDetailsById(Long id);

    /**
     * Total desembolsado por cada usuario en el grupo, sumado en la base de
     * datos. Devuelve solo a quienes pagaron algo.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.UserAmount(e.paidBy.id, SUM(e.amount))
            FROM Expense e
            WHERE e.group.id = :groupId
            GROUP BY e.paidBy.id
            """)
    List<UserAmount> sumPaidByUser(@Param("groupId") Long groupId);

    /**
     * Total desembolsado por un usuario en cada uno de sus grupos, en una
     * sola consulta. Es lo que evita que el listado de grupos dispare una
     * agregacion por fila.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.GroupAmount(e.group.id, SUM(e.amount))
            FROM Expense e
            WHERE e.paidBy.id = :userId AND e.group.id IN :groupIds
            GROUP BY e.group.id
            """)
    List<GroupAmount> sumPaidByUserPerGroup(@Param("userId") Long userId,
                                            @Param("groupIds") Collection<Long> groupIds);
}
