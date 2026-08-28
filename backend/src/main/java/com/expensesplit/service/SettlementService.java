package com.expensesplit.service;

import com.expensesplit.dto.request.CreateSettlementRequest;
import com.expensesplit.dto.response.PagedResponse;
import com.expensesplit.dto.response.SettlementResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ForbiddenException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.*;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Registro y confirmacion de pagos reales entre miembros de un grupo.
 *
 * <h2>Por que hace falta confirmacion</h2>
 * Una liquidacion la registra quien paga, pero solo cuenta cuando quien cobra
 * la reconoce. Fiarse de una sola parte, en una aplicacion cuyo proposito es
 * zanjar discusiones sobre dinero, invita justo a la discusion contraria:
 * bastaria con declarar un pago que nunca ocurrio para que la deuda
 * desapareciera de las cuentas.
 *
 * <h2>Por que una liquidacion confirmada no se borra</h2>
 * Es un hecho contable: dinero que cambio de manos. Borrarlo reescribiria la
 * historia del grupo y dejaria los balances sin explicacion. Para corregir un
 * error se registra el pago inverso, igual que en cualquier libro de cuentas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAccessService groupAccess;

    /**
     * Registra que el usuario autenticado ha pagado a otro miembro. Nace
     * pendiente de confirmacion.
     */
    @Transactional
    public SettlementResponse record(Long groupId, CreateSettlementRequest request, String payerEmail) {
        GroupMember pagador = groupAccess.requireMember(groupId, payerEmail);

        GroupMember receptor = groupMemberRepository
                .findByGroupIdAndUserId(groupId, request.getPaidTo())
                .orElseThrow(() -> new BadRequestException(
                        "La persona a la que pagas no pertenece a este grupo"));

        if (pagador.getUser().getId().equals(receptor.getUser().getId())) {
            throw new BadRequestException("No puedes registrar un pago a ti mismo");
        }

        Settlement settlement = settlementRepository.save(Settlement.builder()
                .group(pagador.getGroup())
                .paidBy(pagador.getUser())
                .paidTo(receptor.getUser())
                .amount(request.getAmount())
                .status(SettlementStatus.PENDING)
                .createdAt(Instant.now())
                .build());

        log.info("Liquidacion {} registrada en el grupo {}: {} paga {} a {}",
                settlement.getId(), groupId, pagador.getUser().getId(),
                request.getAmount(), receptor.getUser().getId());

        return toResponse(settlement);
    }

    /**
     * Confirma el cobro.
     *
     * <p>Solo puede hacerlo quien recibe el dinero. Si pudiera confirmarla
     * quien paga, la confirmacion no aportaria nada sobre el registro inicial
     * y el control se quedaria en un tramite.
     */
    @Transactional
    public SettlementResponse confirm(Long settlementId, String requesterEmail) {
        Settlement settlement = buscar(settlementId);
        GroupMember solicitante = groupAccess.requireMember(
                settlement.getGroup().getId(), requesterEmail);

        if (!settlement.getPaidTo().getId().equals(solicitante.getUser().getId())) {
            throw new ForbiddenException(
                    "Solo quien recibe el pago puede confirmarlo");
        }

        if (settlement.isConfirmed()) {
            // Idempotente: reintentar una confirmacion no es un error.
            return toResponse(settlement);
        }

        settlement.confirm(Instant.now());
        settlementRepository.save(settlement);

        log.info("Liquidacion {} confirmada por el usuario {}",
                settlementId, solicitante.getUser().getId());

        return toResponse(settlement);
    }

    /**
     * Cancela una liquidacion aun pendiente. Solo quien la registro o un
     * administrador del grupo.
     */
    @Transactional
    public void cancel(Long settlementId, String requesterEmail) {
        Settlement settlement = buscar(settlementId);
        GroupMember solicitante = groupAccess.requireMember(
                settlement.getGroup().getId(), requesterEmail);

        if (settlement.isConfirmed()) {
            throw new BadRequestException(
                    "Una liquidacion ya confirmada no se puede borrar: es dinero que cambio de "
                            + "manos. Si fue un error, registra el pago en sentido contrario.");
        }

        boolean laRegistro = settlement.getPaidBy().getId().equals(solicitante.getUser().getId());

        if (!laRegistro && solicitante.getRole() != GroupRole.ADMIN) {
            throw new ForbiddenException(
                    "Solo quien registro el pago o un administrador pueden cancelarlo");
        }

        settlementRepository.delete(settlement);
    }

    /** Historial de pagos del grupo, del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    public PagedResponse<SettlementResponse> history(Long groupId, Pageable pageable,
                                                      String requesterEmail) {
        groupAccess.requireMember(groupId, requesterEmail);

        return PagedResponse.from(
                settlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageable),
                this::toResponse);
    }

    private Settlement buscar(Long settlementId) {
        return settlementRepository.findWithDetailsById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Liquidacion no encontrada"));
    }

    private SettlementResponse toResponse(Settlement s) {
        return SettlementResponse.builder()
                .id(s.getId())
                .paidByUserId(s.getPaidBy().getId())
                .paidByName(s.getPaidBy().getName())
                .paidToUserId(s.getPaidTo().getId())
                .paidToName(s.getPaidTo().getName())
                .amount(s.getAmount())
                .currency(s.getGroup().getCurrency())
                .status(s.getStatus().name())
                .createdAt(s.getCreatedAt())
                .settledAt(s.getSettledAt())
                .build();
    }
}
