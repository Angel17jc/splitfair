package com.expensesplit.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Limitador de peticiones para los endpoints de autenticacion.
 *
 * <h2>Por que dos dimensiones</h2>
 * Se limita por IP y por email a la vez, porque cada una cubre un ataque que
 * la otra deja pasar:
 * <ul>
 *   <li>Solo por IP: un atacante con una botnet prueba miles de contrasenas
 *       contra una misma cuenta, una peticion por maquina.</li>
 *   <li>Solo por email: un atacante prueba una contrasena habitual contra
 *       miles de cuentas distintas desde una sola maquina (<i>password
 *       spraying</i>), sin repetir email.</li>
 * </ul>
 *
 * <h2>Por que Caffeine y no un HashMap</h2>
 * Guardar un bucket por IP en un mapa normal es una via de agotamiento de
 * memoria: basta con enviar peticiones desde direcciones distintas para
 * hacerlo crecer sin limite. La cache esta acotada en numero de entradas y
 * las expira por inactividad.
 *
 * <h2>Limitacion conocida</h2>
 * El estado vive en memoria, de modo que con varias instancias cada una
 * aplica su propio limite y el efectivo se multiplica por el numero de
 * replicas. Para desplegar en varias instancias hay que mover los buckets a
 * Redis (bucket4j-redis), sin cambiar el resto del diseno.
 */
@Service
public class RateLimitService {

    /**
     * Numero maximo de claves vigiladas a la vez. Al superarlo, Caffeine
     * descarta las menos usadas: en el peor caso un atacante se libra del
     * limite tras generar 100.000 claves distintas, lo que ya de por si
     * constituye un volumen detectable.
     */
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final Cache<String, Bucket> buckets;
    private final Bandwidth loginLimit;
    private final Bandwidth registerLimit;

    public RateLimitService(
            @Value("${app.rate-limit.login-attempts}") int loginAttempts,
            @Value("${app.rate-limit.login-window-minutes}") int loginWindowMinutes,
            @Value("${app.rate-limit.register-attempts}") int registerAttempts,
            @Value("${app.rate-limit.register-window-minutes}") int registerWindowMinutes) {

        // La ventana de expiracion es la del limite mas largo: un bucket que
        // lleva mas tiempo sin usarse ya se habria rellenado por completo.
        Duration retencion = Duration.ofMinutes(Math.max(loginWindowMinutes, registerWindowMinutes));

        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(retencion)
                .build();

        // Rellenado gradual (greedy) y no de golpe al final de la ventana:
        // evita que el atacante espere al reinicio para lanzar otra rafaga.
        this.loginLimit = Bandwidth.builder()
                .capacity(loginAttempts)
                .refillGreedy(loginAttempts, Duration.ofMinutes(loginWindowMinutes))
                .build();

        this.registerLimit = Bandwidth.builder()
                .capacity(registerAttempts)
                .refillGreedy(registerAttempts, Duration.ofMinutes(registerWindowMinutes))
                .build();
    }

    /**
     * Consume un intento para la clave dada.
     *
     * @return el resultado, con los segundos que faltan si se agoto el cupo
     */
    public Decision tryConsume(Scope scope, String key) {
        Bucket bucket = buckets.get(scope.name() + ':' + key, k -> Bucket.builder()
                .addLimit(scope == Scope.LOGIN ? loginLimit : registerLimit)
                .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return Decision.allowed(probe.getRemainingTokens());
        }
        // Se redondea hacia arriba: devolver 0 invitaria a reintentar de
        // inmediato y volver a recibir un 429.
        long segundos = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        return Decision.denied(segundos);
    }

    /** Endpoints vigilados, cada uno con su propio cupo. */
    public enum Scope {
        LOGIN,
        REGISTER
    }

    /**
     * @param allowed         si la peticion puede continuar
     * @param remainingTokens intentos que quedan en la ventana actual
     * @param retryAfterSeconds espera recomendada cuando se agoto el cupo
     */
    public record Decision(boolean allowed, long remainingTokens, long retryAfterSeconds) {

        static Decision allowed(long remaining) {
            return new Decision(true, remaining, 0);
        }

        static Decision denied(long retryAfter) {
            return new Decision(false, 0, retryAfter);
        }
    }
}
