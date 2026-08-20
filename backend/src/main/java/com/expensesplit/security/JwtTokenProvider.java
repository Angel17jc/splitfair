package com.expensesplit.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Emision y verificacion de los access token.
 *
 * <p>El access token es de vida corta (minutos) y deliberadamente sin estado:
 * no se consulta la base para validarlo. La contrapartida es que no puede
 * revocarse antes de que caduque, y por eso su ventana es estrecha. La
 * capacidad de cortar una sesion vive en el refresh token, que si tiene
 * estado.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** HS256 exige una clave de al menos 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    private SecretKey signingKey;

    /**
     * Se valida al arrancar y no en la primera peticion: un secreto demasiado
     * corto debe impedir el despliegue, no producir errores en produccion
     * cuando alguien intente iniciar sesion.
     */
    @PostConstruct
    void initSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES + " bytes ("
                            + MIN_SECRET_BYTES * 8 + " bits) para firmar con HS256. "
                            + "Genera uno con: openssl rand -base64 48");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Valida el token distinguiendo el motivo del rechazo. Un token caducado
     * es una situacion normal que el cliente resuelve refrescando; un token
     * con firma invalida es un intento de manipulacion. Devolver el mismo
     * "no valido" para ambos obliga al cliente a adivinar.
     */
    public ValidationResult validate(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return ValidationResult.VALID;
        } catch (ExpiredJwtException e) {
            return ValidationResult.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rechazado: {}", e.getMessage());
            return ValidationResult.INVALID;
        }
    }

    public enum ValidationResult {
        VALID,
        /** Bien firmado pero fuera de plazo: el cliente debe refrescar. */
        EXPIRED,
        /** Malformado, mal firmado o de otro emisor. */
        INVALID
    }
}
