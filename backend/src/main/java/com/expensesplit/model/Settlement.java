package com.expensesplit.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pago real de un miembro a otro para saldar deudas del grupo.
 *
 * <p>No confundir con las liquidaciones <i>sugeridas</i>, que se calculan al
 * vuelo a partir de los balances y no se persisten. Esto es dinero que ya
 * cambio de manos.
 *
 * <p>Nace en {@link SettlementStatus#PENDING}: quien paga lo registra, y solo
 * cuenta para los balances cuando quien cobra lo confirma. La alternativa
 * seria fiarse de una sola parte, y en una aplicacion cuyo proposito es
 * zanjar discusiones sobre dinero eso invita justo a la discusion contraria.
 */
@Entity
@Table(name = "settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_to", nullable = false)
    private User paidTo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    /** Cuando se registro el pago. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Cuando lo confirmo quien cobra. Null mientras esta pendiente. */
    @Column(name = "settled_at")
    private Instant settledAt;

    public boolean isConfirmed() {
        return status == SettlementStatus.CONFIRMED;
    }

    /**
     * Marca la liquidacion como reconocida por quien cobra. A partir de aqui
     * altera los balances del grupo.
     */
    public void confirm(Instant now) {
        this.status = SettlementStatus.CONFIRMED;
        this.settledAt = now;
    }
}
