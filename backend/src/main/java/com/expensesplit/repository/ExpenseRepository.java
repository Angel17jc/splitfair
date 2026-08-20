package com.expensesplit.repository;

import com.expensesplit.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);
}
