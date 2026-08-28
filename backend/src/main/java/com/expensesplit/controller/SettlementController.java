package com.expensesplit.controller;

import com.expensesplit.dto.request.CreateSettlementRequest;
import com.expensesplit.dto.response.PagedResponse;
import com.expensesplit.dto.response.SettlementResponse;
import com.expensesplit.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Pagos reales entre miembros de un grupo.
 *
 * <p>Convive con {@code GET /api/groups/{id}/settlements}, que sirve las
 * liquidaciones <i>sugeridas</i> por el algoritmo y no toca la base. Son dos
 * cosas distintas y por eso el historial vive en una ruta propia: mezclar
 * "lo que deberias pagar" con "lo que ya pagaste" en el mismo recurso invita
 * a confundirlos en el cliente.
 */
@RestController
@RequiredArgsConstructor
public class SettlementController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SettlementService settlementService;

    /**
     * Registra que el usuario autenticado ha pagado a otro miembro. Queda
     * pendiente hasta que quien cobra lo confirme.
     */
    @PostMapping("/api/groups/{groupId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse record(@PathVariable Long groupId,
                                       @Valid @RequestBody CreateSettlementRequest request,
                                       Authentication authentication) {
        return settlementService.record(groupId, request, authentication.getName());
    }

    /** Historial de pagos registrados, del mas reciente al mas antiguo. */
    @GetMapping("/api/groups/{groupId}/settlements/history")
    public PagedResponse<SettlementResponse> history(@PathVariable Long groupId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       Authentication authentication) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));

        return settlementService.history(groupId, pageable, authentication.getName());
    }

    /** Confirma el cobro. Solo quien recibe el dinero. */
    @PostMapping("/api/settlements/{settlementId}/confirm")
    public SettlementResponse confirm(@PathVariable Long settlementId,
                                        Authentication authentication) {
        return settlementService.confirm(settlementId, authentication.getName());
    }

    /** Cancela un pago aun pendiente. Uno ya confirmado no se puede borrar. */
    @DeleteMapping("/api/settlements/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long settlementId, Authentication authentication) {
        settlementService.cancel(settlementId, authentication.getName());
    }
}
