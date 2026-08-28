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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Efecto de las liquidaciones sobre los balances y las sugerencias.
 *
 * <p>Es lo que cierra el ciclo de la aplicacion: sin esto, una deuda ya
 * pagada seguiria apareciendo en las sugerencias para siempre y el grupo
 * nunca llegaria a estar a paz y salvo.
 */
@AutoConfigureMockMvc
class SettlementEffectOnBalancesTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String tokenAna;
    private long idAna;
    private String tokenBeto;
    private long idBeto;
    private String tokenCarla;
    private long idCarla;
    private long grupo;

    @BeforeEach
    void prepararGrupoConDeuda() throws Exception {
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

        // Ana adelanta 90 entre tres: le deben 60, los otros deben 30 cada uno.
        crearGasto("90.00");
    }

    @Nested
    @DisplayName("Solo las confirmadas cuentan")
    class SoloLasConfirmadas {

        @Test
        @DisplayName("una liquidacion pendiente no altera los balances")
        void pendienteNoCuenta() throws Exception {
            registrarPago(tokenBeto, idAna, "30.00").andExpect(status().isCreated());

            // Una pendiente es la palabra de una sola parte: si contase,
            // bastaria declarar un pago inexistente para borrar una deuda.
            assertThat(balanceDe(idBeto)).isEqualByComparingTo("-30.00");
            assertThat(balanceDe(idAna)).isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("al confirmarla, la deuda desaparece")
        void confirmadaSalda() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));
            confirmar(tokenAna, pago).andExpect(status().isOk());

            assertThat(balanceDe(idBeto)).isEqualByComparingTo("0.00");
            assertThat(balanceDe(idAna)).isEqualByComparingTo("30.00");
            assertThat(balanceDe(idCarla)).isEqualByComparingTo("-30.00");
        }

        @Test
        @DisplayName("los balances siguen sumando cero tras liquidar")
        void siguenSumandoCero() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));
            confirmar(tokenAna, pago);

            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("cancelar una pendiente no deja rastro en los balances")
        void cancelarNoAltera() throws Exception {
            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/settlements/{id}", pago)
                            .header("Authorization", bearer(tokenBeto)))
                    .andExpect(status().isNoContent());

            assertThat(balanceDe(idBeto)).isEqualByComparingTo("-30.00");
        }
    }

    @Nested
    @DisplayName("Efecto sobre las sugerencias")
    class Sugerencias {

        @Test
        @DisplayName("una deuda saldada deja de sugerirse")
        void dejaDeSugerirse() throws Exception {
            assertThat(liquidacionesSugeridas().size())
                    .as("sugerencias antes de saldar")
                    .isEqualTo(2);

            long pago = idDe(registrarPago(tokenBeto, idAna, "30.00"));
            confirmar(tokenAna, pago);

            JsonNode sugerencias = liquidacionesSugeridas();
            assertThat(sugerencias.size()).as("sugerencias tras saldar una").isEqualTo(1);

            // Y la que queda es la de Carla, no la de Beto.
            assertThat(sugerencias.get(0).get("fromUserId").asLong()).isEqualTo(idCarla);
        }

        @Test
        @DisplayName("saldando todo, el grupo queda sin nada que liquidar")
        void grupoALaPaz() throws Exception {
            confirmar(tokenAna, idDe(registrarPago(tokenBeto, idAna, "30.00")));
            confirmar(tokenAna, idDe(registrarPago(tokenCarla, idAna, "30.00")));

            assertThat(liquidacionesSugeridas()).isEmpty();
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");

            for (JsonNode b : balances()) {
                assertThat(new BigDecimal(b.get("netBalance").asText()))
                        .as("balance de %s", b.get("userName").asText())
                        .isEqualByComparingTo("0.00");
            }
        }

        @Test
        @DisplayName("un pago de mas invierte la deuda en vez de descuadrarla")
        void pagoDeMas() throws Exception {
            // Beto debia 30 y paga 50: ahora Ana le debe 20 a Beto.
            confirmar(tokenAna, idDe(registrarPago(tokenBeto, idAna, "50.00")));

            assertThat(balanceDe(idBeto)).isEqualByComparingTo("20.00");
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Coherencia entre pantallas")
    class Coherencia {

        @Test
        @DisplayName("el listado de grupos muestra el mismo balance que el detalle")
        void listadoYDetalleCoinciden() throws Exception {
            confirmar(tokenAna, idDe(registrarPago(tokenBeto, idAna, "30.00")));

            // Si el listado no descontara las liquidaciones, mostraria una
            // deuda que desaparece al entrar en el grupo.
            JsonNode listado = json.readTree(mvc.perform(get("/api/groups")
                            .header("Authorization", bearer(tokenBeto)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            BigDecimal enElListado = new BigDecimal(
                    listado.get("content").get(0).get("myBalance").asText());

            assertThat(enElListado)
                    .as("balance de Beto en el listado")
                    .isEqualByComparingTo(balanceDe(idBeto))
                    .isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Desglose")
    class Desglose {

        @Test
        @DisplayName("las liquidaciones se muestran aparte de los gastos")
        void desgloseSeparado() throws Exception {
            confirmar(tokenAna, idDe(registrarPago(tokenBeto, idAna, "30.00")));

            JsonNode deBeto = balanceCompletoDe(idBeto);
            // Adelantar dinero en un gasto y saldar una deuda son cosas
            // distintas: mezclarlas haria imposible explicar la cifra.
            assertThat(new BigDecimal(deBeto.get("totalPaid").asText())).isEqualByComparingTo("0.00");
            assertThat(new BigDecimal(deBeto.get("totalOwed").asText())).isEqualByComparingTo("30.00");
            assertThat(new BigDecimal(deBeto.get("settlementsPaid").asText())).isEqualByComparingTo("30.00");
            assertThat(new BigDecimal(deBeto.get("netBalance").asText())).isEqualByComparingTo("0.00");

            JsonNode deAna = balanceCompletoDe(idAna);
            assertThat(new BigDecimal(deAna.get("settlementsReceived").asText()))
                    .isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("el gasto total del grupo no incluye las transferencias internas")
        void elTotalNoIncluyeLiquidaciones() throws Exception {
            confirmar(tokenAna, idDe(registrarPago(tokenBeto, idAna, "30.00")));

            // Saldar una deuda no es gastar: el grupo sigue habiendo gastado 90.
            JsonNode cuerpo = json.readTree(mvc.perform(get("/api/groups/{g}/balances", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andReturn().getResponse().getContentAsString());

            assertThat(new BigDecimal(cuerpo.get("totalSpent").asText()))
                    .isEqualByComparingTo("90.00");
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode balances() throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/balances", grupo)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("balances");
    }

    private JsonNode balanceCompletoDe(long userId) throws Exception {
        for (JsonNode b : balances()) {
            if (b.get("userId").asLong() == userId) {
                return b;
            }
        }
        throw new AssertionError("Usuario " + userId + " ausente de los balances");
    }

    private BigDecimal balanceDe(long userId) throws Exception {
        return new BigDecimal(balanceCompletoDe(userId).get("netBalance").asText());
    }

    private BigDecimal sumaDeBalances() throws Exception {
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode b : balances()) {
            suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
        }
        return suma;
    }

    private JsonNode liquidacionesSugeridas() throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/settlements", grupo)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
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
                                {"description":"Alquiler","amount":%s,"expenseDate":"2026-08-20"}
                                """.formatted(importe)))
                .andExpect(status().isOk());
    }
}
