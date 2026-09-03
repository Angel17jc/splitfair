package com.expensesplit.controller;

import com.expensesplit.dto.request.CreateExpenseRequest;
import com.expensesplit.dto.request.UpdateExpenseRequest;
import com.expensesplit.dto.response.GroupAnalyticsResponse;
import com.expensesplit.dto.response.GroupBalanceResponse;
import com.expensesplit.dto.response.ExpenseResponse;
import com.expensesplit.dto.response.PagedResponse;
import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.model.ExpenseCategory;
import com.expensesplit.service.AnalyticsService;
import com.expensesplit.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    /** Tope del tamano de pagina, como en el listado de grupos. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ExpenseService expenseService;
    private final AnalyticsService analyticsService;

    @PostMapping("/api/groups/{groupId}/expenses")
    public ExpenseResponse createExpense(@PathVariable Long groupId,
                                           @Valid @RequestBody CreateExpenseRequest request,
                                           Authentication authentication) {
        return expenseService.createExpense(groupId, request, authentication.getName());
    }

    /**
     * Gastos del grupo, paginados y con filtros opcionales.
     *
     * <p>El orden es fijo, del mas reciente al mas antiguo. Permitir ordenar
     * por un campo arbitrario abriria la puerta a ordenaciones sin indice
     * sobre la tabla que mas crece de la aplicacion.
     */
    @GetMapping("/api/groups/{groupId}/expenses")
    public PagedResponse<ExpenseResponse> getExpenses(
            @PathVariable Long groupId,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long paidBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));

        return expenseService.getGroupExpenses(groupId,
                new ExpenseService.ExpenseFilter(category, from, to, paidBy),
                pageable, authentication.getName());
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

    /**
     * Reparto del gasto por categoria y por mes.
     *
     * <p>Existe para que el cliente no tenga que recorrer todas las paginas de
     * gastos y sumarlas: sobre un listado paginado saldrian totales parciales,
     * y sumar dinero en JavaScript es sumar en coma flotante.
     */
    @GetMapping("/api/groups/{groupId}/analytics")
    public GroupAnalyticsResponse getAnalytics(@PathVariable Long groupId,
                                                 Authentication authentication) {
        return analyticsService.getGroupAnalytics(groupId, authentication.getName());
    }

    @GetMapping("/api/groups/{groupId}/balances")
    public GroupBalanceResponse getBalances(@PathVariable Long groupId,
                                              Authentication authentication) {
        return expenseService.getGroupBalances(groupId, authentication.getName());
    }

    @GetMapping("/api/groups/{groupId}/settlements")
    public List<SettlementSuggestionResponse> getSettlements(@PathVariable Long groupId,
                                                               Authentication authentication) {
        return expenseService.getSuggestedSettlements(groupId, authentication.getName());
    }
}
