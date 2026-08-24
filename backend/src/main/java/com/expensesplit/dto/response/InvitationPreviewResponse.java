package com.expensesplit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Vista previa publica de una invitacion, para que quien abre el link sepa a
 * que grupo le invitan antes de decidir si se registra.
 *
 * <p>Expone lo minimo: nombre del grupo y quien invita. Nada de gastos,
 * balances ni la lista de miembros, porque este endpoint se sirve sin
 * autenticacion.
 */
@Data
@Builder
public class InvitationPreviewResponse {

    private String groupName;
    private String invitedByName;
    private Instant expiresAt;

    /** false si ya se uso o caduco; el cliente muestra el motivo. */
    private boolean valid;
}
