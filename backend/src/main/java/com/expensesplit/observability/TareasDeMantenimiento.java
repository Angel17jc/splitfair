package com.expensesplit.observability;

import com.expensesplit.service.InvitationService;
import com.expensesplit.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpieza periodica de credenciales caducadas.
 *
 * <p>{@code purgeExpired()} existia en ambos servicios desde hace fases, pero
 * <b>no lo llamaba nadie</b>. Sin esta tarea, las tablas de refresh tokens e
 * invitaciones crecen indefinidamente: cada inicio de sesion deja una fila
 * que ya no sirve para nada, y cada rotacion deja otra. En desarrollo no se
 * nota; con usuarios reales es la tabla que mas crece de toda la base.
 *
 * <p>No es solo cuestion de espacio. Un refresh token caducado sigue siendo
 * un secreto almacenado: conservarlo indefinidamente amplia sin motivo lo que
 * se filtraria en una copia de seguridad perdida o un volcado de base.
 * Guardar solo lo que sigue en uso es parte de la politica de datos, no una
 * optimizacion.
 *
 * <p>A las 4:00, despues de la copia de seguridad de las 3:00, para que el
 * volcado del dia refleje el estado anterior a la purga: si la purga borrara
 * de mas por un error, la copia de esa madrugada todavia lo contiene.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TareasDeMantenimiento {

    private final RefreshTokenService refreshTokenService;
    private final InvitationService invitationService;

    @Scheduled(cron = "0 0 4 * * *")
    public void purgarCredencialesCaducadas() {
        int tokens = refreshTokenService.purgeExpired();
        int invitaciones = invitationService.purgeExpired();

        // A nivel INFO y siempre, incluso cuando no borra nada: una tarea
        // programada que deja de ejecutarse no da ningun error, simplemente
        // deja de aparecer. La linea diaria es lo que permite notarlo.
        log.info("Purga de caducados: {} refresh tokens, {} invitaciones", tokens, invitaciones);
    }
}
