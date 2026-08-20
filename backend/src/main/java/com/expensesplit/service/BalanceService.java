package com.expensesplit.service;

import com.expensesplit.model.Expense;
import com.expensesplit.model.ExpenseSplit;
import com.expensesplit.model.User;
import com.expensesplit.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula el balance neto de cada usuario dentro de un grupo.
 * balance(usuario) = total_pagado_por_usuario - total_que_le_correspondia_pagar
 *
 * balance > 0  -> le deben dinero (acreedor)
 * balance < 0  -> debe dinero (deudor)
 * balance == 0 -> esta a paz y salvo
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final ExpenseRepository expenseRepository;

    public Map<User, BigDecimal> calculateNetBalances(Long groupId) {
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);

        Map<User, BigDecimal> balances = new LinkedHashMap<>();

        for (Expense expense : expenses) {
            // Quien pago recibe credito por el monto total del gasto
            balances.merge(expense.getPaidBy(), expense.getAmount(), BigDecimal::add);

            // Cada quien que debia una parte, se le resta lo que le correspondia
            for (ExpenseSplit split : expense.getSplits()) {
                balances.merge(split.getUser(), split.getAmountOwed().negate(), BigDecimal::add);
            }
        }

        return balances;
    }
}
