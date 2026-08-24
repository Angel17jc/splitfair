package com.expensesplit.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generacion y hasheo de tokens opacos: refresco de sesion, invitaciones y
 * cualquier otro secreto de un solo uso.
 *
 * <p>Centralizado a proposito. Duplicar codigo de seguridad es la via mas
 * comun a que dos copias diverjan: una se corrige y la otra se queda con el
 * generador debil o con el hash antiguo, sin que ningun test lo note.
 *
 * <p><b>Por que SHA-256 y no bcrypt:</b> bcrypt protege secretos de baja
 * entropia, como las contrasenas, encareciendo cada intento. Aqui el valor es
 * aleatorio de 256 bits, asi que no existe superficie de fuerza bruta que
 * justificar, y el hash se calcula en cada peticion que presente el token.
 */
public final class SecureTokens {

    /** 32 bytes = 256 bits de entropia. */
    private static final int TOKEN_BYTES = 32;

    /** Longitud del hash en hexadecimal; la usan las columnas de la base. */
    public static final int HASH_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
        // clase de utilidad
    }

    /**
     * Genera un token nuevo en formato base64url, apto para viajar en una URL
     * sin necesidad de escaparlo.
     */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal. Es lo unico que llega a persistirse. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno esta roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
