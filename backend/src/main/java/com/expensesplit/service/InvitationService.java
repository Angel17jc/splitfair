package com.expensesplit.service;

import com.expensesplit.dto.request.CreateInvitationRequest;
import com.expensesplit.dto.response.InvitationPreviewResponse;
import com.expensesplit.dto.response.InvitationResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.Group;
import com.expensesplit.model.GroupMember;
import com.expensesplit.model.Invitation;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.InvitationRepository;
import com.expensesplit.security.SecureTokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Invitaciones a un grupo mediante link con token.
 *
 * <h2>Por que un token opaco y hasheado</h2>
 * El link <i>es</i> la credencial: quien lo tenga entra al grupo. Se aplica
 * el mismo criterio que a los refresh token, en la tabla vive solo el
 * SHA-256, de modo que un volcado de la base no permite colarse en ningun
 * grupo.
 *
 * <h2>Por que de un solo uso</h2>
 * Un link reutilizable reenviado por error al chat equivocado dejaria entrar
 * a cualquiera de forma indefinida, y sin dejar rastro de quien lo compartio.
 * Para invitar a tres personas se generan tres links. Es algo mas incomodo,
 * pero cada link queda ligado a una unica incorporacion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAccessService groupAccess;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.invitations.expiration-days}")
    private long expirationDays;

    /**
     * Crea una invitacion. Solo un administrador puede hacerlo: incorporar
     * gente altera la composicion del grupo y, con ella, el reparto de todos
     * los gastos futuros.
     */
    @Transactional
    public InvitationResponse create(Long groupId, CreateInvitationRequest request, String adminEmail) {
        GroupMember admin = groupAccess.requireAdmin(groupId, adminEmail);
        Group group = admin.getGroup();

        String email = normalizar(request.getEmail());

        if (email != null && groupMemberRepository.findByGroupIdAndUserEmail(groupId, email).isPresent()) {
            throw new BadRequestException("Esa persona ya pertenece al grupo");
        }

        String token = SecureTokens.generate();
        Instant now = Instant.now();

        Invitation invitation = invitationRepository.save(Invitation.builder()
                .group(group)
                .invitedBy(admin.getUser())
                .tokenHash(SecureTokens.hash(token))
                .email(email)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofDays(expirationDays)))
                .build());

        log.info("Invitacion {} creada para el grupo {} por el usuario {}",
                invitation.getId(), groupId, admin.getUser().getId());

        return InvitationResponse.builder()
                .id(invitation.getId())
                // El token en claro solo existe aqui: despues queda unicamente
                // su hash, y no hay forma de recuperarlo.
                .token(token)
                .url(buildUrl(token))
                .email(email)
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    /**
     * Vista previa publica: quien abre el link necesita saber a que grupo le
     * invitan antes de decidir si se registra.
     *
     * <p>Se sirve sin autenticacion, asi que expone lo minimo: nombre del
     * grupo y de quien invita. Nada de gastos, balances ni miembros.
     *
     * <p>Un token inexistente devuelve 404, pero uno caducado o ya usado
     * responde 200 con {@code valid: false}: el cliente necesita distinguir
     * "este link nunca existio" de "llegas tarde" para explicarselo al
     * usuario.
     */
    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String token) {
        Invitation invitation = buscar(token);

        return InvitationPreviewResponse.builder()
                .groupName(invitation.getGroup().getName())
                .invitedByName(invitation.getInvitedBy().getName())
                .expiresAt(invitation.getExpiresAt())
                .valid(invitation.isUsable(Instant.now()))
                .build();
    }

    /**
     * Elimina las invitaciones caducadas. Pensado para una tarea programada.
     */
    @Transactional
    public int purgeExpired() {
        return invitationRepository.deleteExpiredBefore(Instant.now());
    }

    private Invitation buscar(String token) {
        return invitationRepository.findByTokenHash(SecureTokens.hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("Invitacion no encontrada"));
    }

    private String buildUrl(String token) {
        return frontendBaseUrl + "/invitaciones/"
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String normalizar(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        // Misma normalizacion que en el registro: si no, una invitacion a
        // "Ana@test.com" nunca casaria con la cuenta "ana@test.com".
        return email.trim().toLowerCase();
    }
}
