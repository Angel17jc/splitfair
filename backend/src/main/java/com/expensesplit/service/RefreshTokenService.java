package com.expensesplit.service;

import com.expensesplit.model.RefreshToken;
import com.expensesplit.model.User;
import com.expensesplit.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Emision, rotacion y revocacion de tokens de refresco.
 *
 * <h2>Por que un token opaco y no otro JWT</h2>
 * Un refresh token debe poder revocarse, y revocar exige estado en el
 * servidor. Si de todos modos hay que consultar la base en cada refresco, la
 * firma del JWT no aporta nada y si complica el borrado. Se usa un valor
 * aleatorio de 256 bits, que ademas no filtra ninguna informacion.
 *
 * <h2>Por que se guarda el hash</h2>
 * En la tabla solo vive el SHA-256. Un volcado de la base no concede
 * sesiones. No se usa bcrypt: protege secretos de baja entropia como las
 * contrasenas, y aqui el valor es aleatorio de 256 bits, sin superficie de
 * fuerza bruta que justifique su coste en cada peticion de refresco.
 *
 * <h2>Rotacion y deteccion de reutilizacion</h2>
 * Cada refresco invalida el token presentado y emite uno nuevo dentro de la
 * misma <i>familia</i>. Si alguna vez se presenta un token ya rotado,
 * significa que existe una copia en circulacion: se revoca la familia entera,
 * de modo que tanto la victima como el atacante quedan fuera y la victima
 * nota el problema al tener que volver a entrar. Es la recomendacion del
 * RFC 9700 para clientes que no pueden custodiar un secreto.
 *
 * <p><b>Cuidado con la transaccionalidad:</b> la revocacion por reutilizacion
 * ocurre justo antes de lanzar una excepcion. Spring revierte la transaccion
 * ante cualquier RuntimeException, de modo que sin {@code noRollbackFor} la
 * revocacion se deshace al propagarse el error y la deteccion no surte
 * ningun efecto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** 32 bytes = 256 bits de entropia. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration-days}")
    private long refreshExpirationDays;

    /**
     * Emite un token nuevo abriendo una familia. Se llama al iniciar sesion.
     *
     * @return el token en claro, unica vez que existe fuera del cliente
     */
    @Transactional
    public String issue(User user) {
        return persist(user, UUID.randomUUID());
    }

    /**
     * Valida el token presentado y lo cambia por uno nuevo.
     *
     * @return el token de refresco nuevo, junto al usuario al que pertenece
     * @throws BadCredentialsException si el token es desconocido, ha caducado
     *                                 o ya habia sido usado
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public RotationResult rotate(String presentedToken) {
        Instant now = Instant.now();

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new BadCredentialsException("Token de refresco invalido"));

        if (stored.isRevoked()) {
            // Un token revocado que vuelve a aparecer solo tiene una
            // explicacion: alguien conserva una copia. No se puede saber si
            // quien lo presenta es la victima o el atacante, asi que se corta
            // la familia entera y ambos deben autenticarse de nuevo.
            int revocados = refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            log.warn("Reutilizacion de refresh token detectada para el usuario {}. "
                            + "Familia {} revocada ({} tokens invalidados).",
                    stored.getUser().getId(), stored.getFamilyId(), revocados);

            // De aqui sale una excepcion, y por eso el metodo declara
            // noRollbackFor: sin ella Spring revierte la transaccion al
            // propagarse, deshaciendo la revocacion que se acaba de hacer.
            // El control de seguridad quedaria anulado por el propio
            // mecanismo transaccional, en silencio y sin dejar rastro.
            throw new BadCredentialsException("Token de refresco invalido");
        }

        if (stored.isExpired(now)) {
            throw new BadCredentialsException("Token de refresco caducado");
        }

        // Rotacion: el token presentado muere aqui.
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        String nuevo = persist(stored.getUser(), stored.getFamilyId());
        return new RotationResult(stored.getUser(), nuevo);
    }

    /**
     * Cierra la sesion asociada al token. Revoca la familia completa y no
     * solo el token presentado: cerrar sesion debe invalidar la cadena, no
     * dejar viva la siguiente rotacion.
     *
     * <p>No falla si el token es desconocido: cerrar sesion es idempotente y
     * no debe servir para averiguar que tokens existen.
     */
    @Transactional
    public void revokeSession(String presentedToken) {
        refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(
                        token.getFamilyId(), Instant.now()));
    }

    /** Revoca todas las sesiones del usuario, en todos sus dispositivos. */
    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /**
     * Elimina los tokens ya caducados. Pensado para una tarea programada:
     * sin ella la tabla crece indefinidamente.
     */
    @Transactional
    public int purgeExpired() {
        return refreshTokenRepository.deleteExpiredBefore(Instant.now());
    }

    private String persist(User user, UUID familyId) {
        String token = generateToken();
        Instant now = Instant.now();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .familyId(familyId)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofDays(refreshExpirationDays)))
                .build());

        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno esta roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /** Usuario y token nuevo resultantes de una rotacion. */
    public record RotationResult(User user, String refreshToken) {
    }
}
