package com.expensesplit.controller;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro, confirmacion y cancelacion de pagos reales.
 */
@AutoConfigureMockMvc
class SettlementLifecycleTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenAna;
    private long idAna;
    private String tokenBeto;
    private long idBeto;
    private String tokenCarla;
    private long idCarla;
    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();
        idBeto = beto.get("userId").asLong();

        JsonNode carla = registrar("Carla", "carla@test.com");
        tokenCarla = carla.get("accessToken").asText();
        idCarla = carla.get("userId").asLong();

        grupo = crearGrupo();
        anadirMiembro(idBeto);
        anadirMiembro(idCarla);
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @Test
        @DisplayName("nace pendiente de confirmacion, no confirmada")
        void nacePendiente() throws Exception {
            registrarPago(tokenBeto, idAna, "30.00")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.paidByUserId").value(idBeto))
                    .andExpect(jsonPath("$.paidToUserId").value(idAna))
                    .andExpect(jsonPath("$.amount").value(30.00))
                    .andExpect(jsonPath("$.currency").value("EUR"))
                    .andExpect(jsonPath("$.settledAt").doesNotExist());
        }

        @Test
        @DisplayName("quien paga es siempre el usuario autenticado")
        void elPagadorEsElSolicitante() throws Exception {
            // No hay forma de declarar un pago ajeno: bastaria para dar por
            // saldada la deuda de otro sin su conocimiento.
            registrarPago(tokenCarla, idAna, "15.00")
                    .andExpect(jsonPath("$.paidByUserId").value(idCarla));
        }

        @Test
        @DisplayName("pagar a quien no pertenece al grupo se rechaza")
        void receptorAjeno() throws Exception {
            long idMallory = registrar("Mallory", "mallory@test.com").get("userId").asLong();

            registrarPago(tokenAna, idMallory, "10.00")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "La persona a la que pagas no pertenece a este grupo"));
        }

        @Test
        @DisplayName("pagarse a uno mismo se rechaza")
        void pagoAUnoMismo() throws Exception {
            registrarPago(tokenAna, idAna, "10.00")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("No puedes registrar un pago a ti mismo"));
        }

        @Test
        @DisplayName("un importe de cero o negativo se rechaza")
        void importeInvalido() throws Exception {
            registrarPago(tokenBeto, idAna, "0.00")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.amount").exists());
        }

        @Test
        @DisplayName("un extrano al grupo no puede registrar pagos")
        void extranoNoRegistra() throws Exception {
            String tokenMallory = registrar("Mallory", "mallory@test.com").get("accessToken").asText();

            registrarPago(tokenMallory, idAna, "10.00").andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Confirmacion")
    class Confirmacion {

        @Test
        @DisplayName("solo quien recibe el dinero puede confirmar")
        void soloElReceptorConfirma() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));

            // Si pudiera confirmarla quien paga, la confirmacion no aportaria
            // nada sobre el registro inicial.
            confirmar(tokenBeto, pago)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Solo quien recibe el pago puede confirmarlo"));

            // Ni un tercero del grupo.
            confirmar(tokenCarla, pago).andExpect(status().isForbidden());

            confirmar(tokenAna, pago)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.settledAt").exists());
        }

        @Test
        @DisplayName("confirmar dos veces es idempotente")
        void idempotente() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));

            confirmar(tokenAna, pago).andExpect(status().isOk());
            confirmar(tokenAna, pago)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("confirmar una liquidacion inexistente devuelve 404")
        void inexistente() throws Exception {
            confirmar(tokenAna, 999_999L).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Cancelacion")
    class Cancelacion {

        @Test
        @DisplayName("quien la registro puede cancelarla mientras este pendiente")
        void cancelaElPagador() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));

            cancelar(tokenBeto, pago).andExpect(status().isNoContent());

            assertThat(historial(tokenAna).get("totalElements").asInt()).isZero();
        }

        @Test
        @DisplayName("un administrador tambien puede cancelarla")
        void cancelaElAdmin() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idCarla, "30.00"));

            cancelar(tokenAna, pago).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("un miembro cualquiera no puede cancelar el pago de otro")
        void miembroNoCancelaAjenos() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));

            cancelar(tokenCarla, pago).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("una liquidacion confirmada no se puede borrar")
        void confirmadaNoSeBorra() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));
            confirmar(tokenAna, pago).andExpect(status().isOk());

            // Es un hecho contable: dinero que cambio de manos. Borrarlo
            // reescribiria la historia del grupo.
            cancelar(tokenBeto, pago)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("registra el pago en sentido contrario")));
        }
    }

    @Nested
    @DisplayName("Historial")
    class Historial {

        @Test
        @DisplayName("lista los pagos del grupo, pendientes y confirmados")
        void listaTodos() throws Exception {
            long uno = idDe(registrarPago(tokenBeto, idAna, "30.00"));
            registrarPago(tokenCarla, idAna, "20.00");
            confirmar(tokenAna, uno);

            JsonNode historial = historial(tokenAna);
            assertThat(historial.get("totalElements").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("no mezcla los pagos con las liquidaciones sugeridas")
        void separadoDeLasSugerencias() throws Exception {
            crearGasto("90.00");
            registrarPago(tokenBeto, idAna, "30.00");

            // /settlements sigue devolviendo lo que el algoritmo propone,
            // que es una lista, no la respuesta paginada del historial.
            mvc.perform(get("/api/groups/{g}/settlements", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            mvc.perform(get("/api/groups/{g}/settlements/history", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("un extrano al grupo no ve el historial")
        void extranoNoVe() throws Exception {
            String tokenMallory = registrar("Mallory", "mallory@test.com").get("accessToken").asText();

            mvc.perform(get("/api/groups/{g}/settlements/history", grupo)
                            .header("Authorization", bearer(tokenMallory)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Coherencia en la base")
    class Coherencia {

        @Test
        @DisplayName("una pendiente con fecha de confirmacion es rechazada por la base")
        void estadoYFechaCoherentes() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));

            // Es un estado que el codigo nunca produce, pero la base tampoco
            // debe aceptarlo por otra via.
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                            "UPDATE settlements SET settled_at = now() WHERE id = ?", pago))
                    .hasMessageContaining("ck_settlements_settled_consistent");
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions registrarPago(String token, long paidTo, String importe) throws Exception {
        return mvc.perform(post("/api/groups/{g}/settlements", grupo)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paidTo":%d,"amount":%s}
                        """.formatted(paidTo, importe)));
    }

    private ResultActions confirmar(String token, long settlementId) throws Exception {
        return mvc.perform(post("/api/settlements/{id}/confirm", settlementId)
                .header("Authorization", bearer(token)));
    }

    private ResultActions cancelar(String token, long settlementId) throws Exception {
        return mvc.perform(delete("/api/settlements/{id}", settlementId)
                .header("Authorization", bearer(token)));
    }

    private JsonNode historial(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/settlements/history", grupo)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long idDe(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long crearGrupo() throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Piso","description":"test","currency":"EUR"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void anadirMiembro(long userId) throws Exception {
        mvc.perform(post("/api/groups/{g}/members/{u}", grupo, userId)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk());
    }

    private void crearGasto(String importe) throws Exception {
        mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Gasto","amount":%s,"expenseDate":"2026-08-20"}
                                """.formatted(importe)))
                .andExpect(status().isOk());
    }
}
