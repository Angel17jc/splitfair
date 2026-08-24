package com.expensesplit.repository;

import com.expensesplit.model.ExpenseSplit;
import com.expensesplit.repository.projection.GroupAmount;
import com.expensesplit.repository.projection.UserAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

    /**
     * Total que le correspondia asumir a cada usuario en el grupo, sumado en
     * la base de datos. Devuelve solo a quienes participaron en algun gasto.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.UserAmount(s.user.id, SUM(s.amountOwed))
            FROM ExpenseSplit s
            WHERE s.expense.group.id = :groupId
            GROUP BY s.user.id
            """)
    List<UserAmount> sumOwedByUser(@Param("groupId") Long groupId);

    /**
     * Total que le correspondia asumir a un usuario en cada uno de sus
     * grupos, en una sola consulta.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.GroupAmount(
                s.expense.group.id, SUM(s.amountOwed))
            FROM ExpenseSplit s
            WHERE s.user.id = :userId AND s.expense.group.id IN :groupIds
            GROUP BY s.expense.group.id
            """)
    List<GroupAmount> sumOwedByUserPerGroup(@Param("userId") Long userId,
                                            @Param("groupIds") Collection<Long> groupIds);
}
