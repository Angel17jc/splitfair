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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contexto y desglose de la respuesta de balances.
 *
 * <p>Un importe sin moneda no se puede presentar, y un neto sin desglose no
 * se puede explicar. "Debes 40" abre una discusion en el grupo; "pusiste 60 y
 * te tocaban 100" la cierra.
 */
@AutoConfigureMockMvc
class GroupBalanceContextTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String tokenAna;
    private long idAna;
    private long idBeto;
    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();
        idBeto = registrar("Beto", "beto@test.com").get("userId").asLong();

        grupo = crearGrupo("EUR");
        anadirMiembro(idBeto);
    }

    @Nested
    @DisplayName("Contexto del grupo")
    class Contexto {

        @Test
        @DisplayName("la respuesta indica la moneda de los importes")
        void indicaLaMoneda() throws Exception {
            // Sin esto el cliente tenia que pedir el grupo aparte solo para
            // saber como formatear las cifras que acababa de recibir.
            balances().andExpect(jsonPath("$.currency").value("EUR"));
        }

        @Test
        @DisplayName("incluye el gasto total del grupo")
        void incluyeElTotal() throws Exception {
            crearGasto("90.00");
            crearGasto("10.50");

            balances().andExpect(jsonPath("$.totalSpent").value(100.50));
        }

        @Test
        @DisplayName("un grupo sin gastos tiene total cero y todos a cero")
        void grupoVacio() throws Exception {
            balances()
                    .andExpect(jsonPath("$.totalSpent").value(0.00))
                    .andExpect(jsonPath("$.balances.length()").value(2))
                    .andExpect(jsonPath("$.balances[0].netBalance").value(0.00));
        }
    }

    @Nested
    @DisplayName("Desglose por miembro")
    class Desglose {

        @Test
        @DisplayName("cada balance dice cuanto puso y cuanto le tocaba")
        void desglosaLosComponentes() throws Exception {
            // Ana adelanta 100 y se reparte entre los dos.
            crearGasto("100.00");

            JsonNode deAna = balanceDe(idAna);
            assertThat(new BigDecimal(deAna.get("totalPaid").asText())).isEqualByComparingTo("100.00");
            assertThat(new BigDecimal(deAna.get("totalOwed").asText())).isEqualByComparingTo("50.00");
            assertThat(new BigDecimal(deAna.get("netBalance").asText())).isEqualByComparingTo("50.00");

            JsonNode deBeto = balanceDe(idBeto);
            assertThat(new BigDecimal(deBeto.get("totalPaid").asText())).isEqualByComparingTo("0.00");
            assertThat(new BigDecimal(deBeto.get("totalOwed").asText())).isEqualByComparingTo("50.00");
            assertThat(new BigDecimal(deBeto.get("netBalance").asText())).isEqualByComparingTo("-50.00");
        }

        @Test
        @DisplayName("el neto siempre es lo puesto menos lo que tocaba")
        void elNetoCuadraConElDesglose() throws Exception {
            crearGasto("77.77");
            crearGasto("33.33");

            for (JsonNode b : json.readTree(balances().andReturn().getResponse()
                    .getContentAsString()).get("balances")) {

                BigDecimal puesto = new BigDecimal(b.get("totalPaid").asText());
                BigDecimal tocaba = new BigDecimal(b.get("totalOwed").asText());
                BigDecimal neto = new BigDecimal(b.get("netBalance").asText());

                assertThat(puesto.subtract(tocaba))
                        .as("desglose de %s", b.get("userName").asText())
                        .isEqualByComparingTo(neto);
            }
        }

        @Test
        @DisplayName("el total gastado coincide con la suma de lo puesto por todos")
        void elTotalEsLaSumaDeLoPuesto() throws Exception {
            crearGasto("60.00");
            crearGasto("40.00");

            JsonNode cuerpo = json.readTree(balances().andReturn().getResponse().getContentAsString());

            BigDecimal sumaPuesto = BigDecimal.ZERO;
            for (JsonNode b : cuerpo.get("balances")) {
                sumaPuesto = sumaPuesto.add(new BigDecimal(b.get("totalPaid").asText()));
            }

            assertThat(new BigDecimal(cuerpo.get("totalSpent").asText()))
                    .isEqualByComparingTo(sumaPuesto);
        }
    }

    // --- utilidades ---

    private org.springframework.test.web.servlet.ResultActions balances() throws Exception {
        return mvc.perform(get("/api/groups/{g}/balances", grupo)
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk());
    }

    private JsonNode balanceDe(long userId) throws Exception {
        for (JsonNode b : json.readTree(balances().andReturn().getResponse().getContentAsString())
                .get("balances")) {
            if (b.get("userId").asLong() == userId) {
                return b;
            }
        }
        throw new AssertionError("El usuario " + userId + " no aparece en los balances");
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

    private long crearGrupo(String moneda) throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Piso","description":"test","currency":"%s"}
                                """.formatted(moneda)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void anadirMiembro(long userId) throws Exception {
        mvc.perform(post("/api/groups/{g}/members/{u}", grupo, userId)
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk());
    }

    private void crearGasto(String importe) throws Exception {
        mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Gasto","amount":%s,"expenseDate":"2026-08-20"}
                                """.formatted(importe)))
                .andExpect(status().isOk());
    }
}
