package com.expensesplit.controller;

import com.expensesplit.dto.request.CreateExpenseRequest;
import com.expensesplit.dto.response.BalanceResponse;
import com.expensesplit.dto.response.ExpenseResponse;
import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping("/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(@PathVariable Long groupId,
                                                           @Valid @RequestBody CreateExpenseRequest request,
                                                           Authentication authentication) {
        return ResponseEntity.ok(expenseService.createExpense(groupId, request, authentication.getName()));
    }

    @GetMapping("/expenses")
    public ResponseEntity<List<ExpenseResponse>> getExpenses(@PathVariable Long groupId,
                                                               Authentication authentication) {
        return ResponseEntity.ok(expenseService.getGroupExpenses(groupId, authentication.getName()));
    }

    @GetMapping("/balances")
    public ResponseEntity<List<BalanceResponse>> getBalances(@PathVariable Long groupId,
                                                              Authentication authentication) {
        return ResponseEntity.ok(expenseService.getGroupBalances(groupId, authentication.getName()));
    }

    @GetMapping("/settlements")
    public ResponseEntity<List<SettlementSuggestionResponse>> getSettlements(@PathVariable Long groupId,
                                                                              Authentication authentication) {
        return ResponseEntity.ok(expenseService.getSuggestedSettlements(groupId, authentication.getName()));
    }
}
