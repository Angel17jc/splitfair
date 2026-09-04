package com.expensesplit.observability;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las sondas de estado, y sobre todo lo que <b>no</b> cuentan.
 *
 * <p>Exponer Actuator es abrir una puerta al interior de la aplicacion. Estos
 * tests fijan hasta donde llega esa puerta, porque es el tipo de detalle que
 * se afloja sin querer: basta un {@code include: "*"} o un
 * {@code show-details: always} anadido para depurar y olvidado despues.
 */
@AutoConfigureMockMvc
class SondasDeEstadoTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Nested
    @DisplayName("Disponibilidad")
    class Disponibilidad {

        @Test
        @DisplayName("health responde sin credenciales")
        void healthEsPublico() throws Exception {
            // Quien consulta la sonda —Docker, el orquestador, el
            // balanceador— no tiene con que autenticarse. Si respondiera 401
            // lo leeria como servicio caido y el contenedor entraria en un
            // ciclo de reinicios sin tener nada roto.
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("liveness y readiness existen y son distintas")
        void sondasSeparadas() throws Exception {
            // "Vivo" significa no me reinicies; "listo", ya puedes mandarme
            // trafico. Sin la segunda, el orquestador enruta peticiones a una
            // instancia que todavia esta migrando el esquema.
            mvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));

            mvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Nested
    @DisplayName("Lo que no se filtra")
    class NoSeFiltra {

        @Test
        @DisplayName("health expone exactamente un campo: el estado")
        void healthNoDaDetalles() throws Exception {
            String cuerpo = mvc.perform(get("/actuator/health"))
                    .andReturn().getResponse().getContentAsString();

            JsonNode raiz = json.readTree(cuerpo);
            List<String> campos = new java.util.ArrayList<>();
            raiz.fieldNames().forEachRemaining(campos::add);

            // Se afirma el conjunto exacto y no una lista de prohibidos: una
            // lista negra solo detecta las fugas que alguien penso en
            // escribir, y con detalles activados esta respuesta publica diria
            // que base de datos hay detras, su version y el espacio en disco.
            // "groups" solo enumera los nombres de los grupos de sondas
            // (liveness, readiness): no dice nada del interior. Lo que no
            // puede aparecer es "components" ni "details", que es donde van
            // la base de datos y el disco.
            assertThat(campos).containsExactlyInAnyOrder("status", "groups");
        }

        @Test
        @DisplayName("las metricas exigen autenticacion")
        void metricasCerradas() throws Exception {
            mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("los endpoints que vuelcan la configuracion ni siquiera existen")
        void configuracionNoExpuesta() throws Exception {
            // /actuator/env y /actuator/configprops muestran la configuracion
            // entera, secreto de JWT y contrasena de base de datos incluidos.
            // No basta con que pidan credenciales: cualquier usuario
            // registrado tiene credenciales validas.
            //
            // La comprobacion se hace **autenticado** a proposito. Sin token
            // la respuesta es 401 para todo, exista el endpoint o no, y ese
            // 401 pasaria igual aunque estuvieran publicados: el test daria
            // verde sin comprobar nada. Con token, 404 significa que no
            // existen.
            String token = tokenDeUnUsuarioCualquiera();

            for (String endpoint : List.of("env", "configprops", "beans", "loggers")) {
                mvc.perform(get("/actuator/" + endpoint).header("Authorization", "Bearer " + token))
                        .andExpect(status().isNotFound());
            }
        }
    }

    /** Registra a alguien y devuelve su access token. */
    private String tokenDeUnUsuarioCualquiera() throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sonda","email":"sonda@test.com","password":"password123"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("accessToken").asText();
    }
}
