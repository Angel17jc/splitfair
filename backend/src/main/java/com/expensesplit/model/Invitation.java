package com.expensesplit.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Invitacion a un grupo mediante link con token.
 *
 * <p>Solo se almacena el hash del token: el link es la credencial, y un
 * volcado de esta tabla no debe permitir entrar en ningun grupo.
 */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /**
     * Destinatario previsto. Si esta relleno, solo esa direccion puede
     * aceptar, de modo que reenviar el link a un tercero no sirve de nada.
     * Si es null, vale para quien lo reciba.
     */
    @Column(length = 180)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    public boolean isAccepted() {
        return acceptedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** Una invitacion sirve si no se ha usado y no ha caducado. */
    public boolean isUsable(Instant now) {
        return !isAccepted() && !isExpired(now);
    }

    /**
     * Comprueba si esta direccion puede aceptar la invitacion. Una invitacion
     * sin destinatario vale para cualquiera.
     */
    public boolean acceptableBy(String candidateEmail) {
        return email == null || email.equalsIgnoreCase(candidateEmail);
    }
}
