package com.expensesplit.config;

import com.expensesplit.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La documentacion de la API debe ser alcanzable sin autenticarse.
 *
 * <p>Existe por un fallo real: {@code /swagger-ui.html} es la URL que la
 * gente teclea, pero no casa con el patron {@code /swagger-ui/**} que
 * SecurityConfig dejaba abierto, asi que respondia 401 y la documentacion
 * quedaba inaccesible pese a estar generada. El fallo es facil de repetir en
 * cuanto alguien reordene esa lista, y no lo detectaba ningun test.
 */
@AutoConfigureMockMvc
class OpenApiDocsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("El esquema OpenAPI se sirve sin autenticacion")
    void esquemaPublico() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SplitFair API"))
                .andExpect(jsonPath("$.paths").isNotEmpty());
    }

    @Test
    @DisplayName("Declara el esquema bearerAuth para poder probar la API autenticada")
    void declaraBearerAuth() throws Exception {
        // Sin esto, Swagger UI muestra los endpoints pero no deja llamarlos:
        // casi todos exigen un access token y no habria donde meterlo.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    @DisplayName("La interfaz de Swagger es alcanzable por la URL que se teclea")
    void swaggerUiAlcanzable() throws Exception {
        // Es el caso concreto que fallaba: 401 en vez de la redireccion.
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Los endpoints principales aparecen documentados")
    void documentaLosEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/groups']").exists())
                .andExpect(jsonPath("$.paths['/api/groups/{groupId}/balances']").exists())
                .andExpect(jsonPath("$.paths['/api/settlements/{settlementId}/confirm']").exists());
    }
}
