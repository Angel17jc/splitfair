package com.expensesplit.repository;

import com.expensesplit.model.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {
    List<ExpenseSplit> findByExpenseGroupId(Long groupId);
}
