package com.expensesplit.controller;

import com.expensesplit.dto.request.CreateExpenseRequest;
import com.expensesplit.dto.request.UpdateExpenseRequest;
import com.expensesplit.dto.response.BalanceResponse;
import com.expensesplit.dto.response.ExpenseResponse;
import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gastos de un grupo y operaciones sobre un gasto concreto.
 *
 * <p>No hay @RequestMapping a nivel de clase: conviven rutas colgadas del
 * grupo ({@code /api/groups/{groupId}/expenses}) con rutas del propio gasto
 * ({@code /api/expenses/{expenseId}}), tal como pide la especificacion. Un
 * prefijo comun obligaria a partir esto en dos controladores por una razon
 * puramente sintactica.
 */
@RestController
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping("/api/groups/{groupId}/expenses")
    public ExpenseResponse createExpense(@PathVariable Long groupId,
                                           @Valid @RequestBody CreateExpenseRequest request,
                                           Authentication authentication) {
        return expenseService.createExpense(groupId, request, authentication.getName());
    }

    @GetMapping("/api/groups/{groupId}/expenses")
    public List<ExpenseResponse> getExpenses(@PathVariable Long groupId,
                                               Authentication authentication) {
        return expenseService.getGroupExpenses(groupId, authentication.getName());
    }

    /**
     * Reemplaza los datos del gasto y recalcula su reparto. Solo quien lo
     * pago o un administrador del grupo.
     */
    @PutMapping("/api/expenses/{expenseId}")
    public ExpenseResponse updateExpense(@PathVariable Long expenseId,
                                           @Valid @RequestBody UpdateExpenseRequest request,
                                           Authentication authentication) {
        return expenseService.updateExpense(expenseId, request, authentication.getName());
    }

    @DeleteMapping("/api/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long expenseId, Authentication authentication) {
        expenseService.deleteExpense(expenseId, authentication.getName());
    }

    @GetMapping("/api/groups/{groupId}/balances")
    public List<BalanceResponse> getBalances(@PathVariable Long groupId,
                                               Authentication authentication) {
        return expenseService.getGroupBalances(groupId, authentication.getName());
    }

    @GetMapping("/api/groups/{groupId}/settlements")
    public List<SettlementSuggestionResponse> getSettlements(@PathVariable Long groupId,
                                                               Authentication authentication) {
        return expenseService.getSuggestedSettlements(groupId, authentication.getName());
    }
}
