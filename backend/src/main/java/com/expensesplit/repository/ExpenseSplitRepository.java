package com.expensesplit.repository;

import com.expensesplit.model.ExpenseSplit;
import com.expensesplit.repository.projection.UserAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
