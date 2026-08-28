package com.expensesplit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion de la API.
 *
 * <p>springdoc estaba en el proyecto desde el principio pero sin configurar,
 * asi que Swagger UI mostraba los endpoints y no permitia llamarlos: casi
 * todos exigen un access token y no habia donde meterlo. Declarar el esquema
 * <b>bearerAuth</b> anade el boton de autorizacion y convierte la pagina en
 * algo con lo que se puede probar la API de verdad.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI splitFairOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SplitFair API")
                        .version("v1")
                        .description("""
                                Gastos compartidos entre grupos, con calculo de balances y \
                                simplificacion de deudas.

                                **Autenticacion.** Registrate o inicia sesion en `/api/auth`, \
                                copia el `accessToken` de la respuesta y pegalo en el boton \
                                Authorize. Dura 15 minutos; pasado ese plazo se renueva con \
                                `/api/auth/refresh` sin volver a introducir la contrasena.

                                **Importes.** Todos van en la moneda del grupo, con dos \
                                decimales. El reparto garantiza que las partes de un gasto \
                                suman exactamente su importe y que los balances de un grupo \
                                suman cero.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT"))
                        .contact(new Contact().name("SplitFair")))

                // Se declara a nivel global: practicamente todos los endpoints
                // la exigen, y marcarla uno a uno solo genera ruido. Las
                // publicas se documentan como tales en su propio metodo.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .name(ESQUEMA_BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token devuelto por /api/auth/login")));
    }
}
