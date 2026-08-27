package com.expensesplit.controller;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los cuatro modos de reparto a traves de la API.
 *
 * <p>La invariante comun a todos, y la que hace que el resto de la
 * aplicacion funcione, es que las partes suman exactamente el importe del
 * gasto y los balances del grupo suman cero.
 */
@AutoConfigureMockMvc
class ExpenseSplitTypeTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String token;
    private long idAna;
    private long idBeto;
    private long idCarla;
    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        token = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();
        idBeto = registrar("Beto", "beto@test.com").get("userId").asLong();
        idCarla = registrar("Carla", "carla@test.com").get("userId").asLong();

        grupo = crearGrupo();
        anadirMiembro(idBeto);
        anadirMiembro(idCarla);
    }

    @Nested
    @DisplayName("Reparto igual")
    class Igual {

        @Test
        @DisplayName("es el modo por defecto cuando no se indica ninguno")
        void porDefecto() throws Exception {
            crearGasto("90.00", null, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.splitType").value("EQUAL"))
                    .andExpect(jsonPath("$.splits.length()").value(3));
        }

        @Test
        @DisplayName("acepta la forma detallada aunque los valores sobren")
        void formaDetallada() throws Exception {
            crearGasto("90.00", "EQUAL", """
                    [{"userId":%d},{"userId":%d}]
                    """.formatted(idAna, idBeto))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.splits.length()").value(2))
                    .andExpect(jsonPath("$.splits[0].amountOwed").value(45.00));
        }
    }

    @Nested
    @DisplayName("Importes exactos")
    class Exactos {

        @Test
        @DisplayName("se respetan tal cual si suman el total")
        void cuadran() throws Exception {
            crearGasto("100.00", "EXACT", """
                    [{"userId":%d,"value":60.00},{"userId":%d,"value":25.00},{"userId":%d,"value":15.00}]
                    """.formatted(idAna, idBeto, idCarla))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.splitType").value("EXACT"));

            assertThat(parteDe(idAna)).isEqualByComparingTo("60.00");
            assertThat(parteDe(idBeto)).isEqualByComparingTo("25.00");
            assertThat(parteDe(idCarla)).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("si faltan importes, el error dice cuanto falta")
        void faltan() throws Exception {
            // Con solo "no cuadra", quien mete cinco importes a mano tiene
            // que recalcularlos todos para encontrar el suyo.
            crearGasto("100.00", "EXACT", """
                    [{"userId":%d,"value":60.00},{"userId":%d,"value":25.00}]
                    """.formatted(idAna, idBeto))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Faltan 15.00")));
        }

        @Test
        @DisplayName("si sobran importes, tambien lo dice")
        void sobran() throws Exception {
            crearGasto("100.00", "EXACT", """
                    [{"userId":%d,"value":80.00},{"userId":%d,"value":40.00}]
                    """.formatted(idAna, idBeto))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Sobran 20.00")));
        }

        @Test
        @DisplayName("un importe negativo se rechaza")
        void negativo() throws Exception {
            crearGasto("100.00", "EXACT", """
                    [{"userId":%d,"value":120.00},{"userId":%d,"value":-20.00}]
                    """.formatted(idAna, idBeto))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Porcentajes")
    class Porcentajes {

        @Test
        @DisplayName("se aplican sobre el importe del gasto")
        void seAplican() throws Exception {
            crearGasto("200.00", "PERCENTAGE", """
                    [{"userId":%d,"value":50},{"userId":%d,"value":30},{"userId":%d,"value":20}]
                    """.formatted(idAna, idBeto, idCarla))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.splitType").value("PERCENTAGE"));

            assertThat(parteDe(idAna)).isEqualByComparingTo("100.00");
            assertThat(parteDe(idBeto)).isEqualByComparingTo("60.00");
            assertThat(parteDe(idCarla)).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("con decimales el reparto sigue cuadrando al centimo")
        void conDecimales() throws Exception {
            // Calculando cada parte por separado y redondeando, 33.33% de
            // 100.00 tres veces da 99.99. Repartiendo por pesos, cuadra.
            crearGasto("100.00", "PERCENTAGE", """
                    [{"userId":%d,"value":33.33},{"userId":%d,"value":33.33},{"userId":%d,"value":33.34}]
                    """.formatted(idAna, idBeto, idCarla))
                    .andExpect(status().isOk());

            assertThat(sumaDePartes()).isEqualByComparingTo("100.00");
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");
        }

        @ParameterizedTest
        @ValueSource(strings = {"99", "101", "50"})
        @DisplayName("si no suman 100 se rechaza indicando el total")
        void noSuman100(String primero) throws Exception {
            crearGasto("100.00", "PERCENTAGE", """
                    [{"userId":%d,"value":%s}]
                    """.formatted(idAna, primero))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("deben sumar exactamente 100%")));
        }
    }

    @Nested
    @DisplayName("Partes proporcionales")
    class Partes {

        @Test
        @DisplayName("el doble de partes paga el doble")
        void proporcional() throws Exception {
            crearGasto("100.00", "SHARES", """
                    [{"userId":%d,"value":2},{"userId":%d,"value":1},{"userId":%d,"value":1}]
                    """.formatted(idAna, idBeto, idCarla))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.splitType").value("SHARES"));

            assertThat(parteDe(idAna)).isEqualByComparingTo("50.00");
            assertThat(parteDe(idBeto)).isEqualByComparingTo("25.00");
            assertThat(parteDe(idCarla)).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("las partes no tienen que sumar ninguna cantidad concreta")
        void sinTotalFijo() throws Exception {
            crearGasto("90.00", "SHARES", """
                    [{"userId":%d,"value":7},{"userId":%d,"value":2}]
                    """.formatted(idAna, idBeto))
                    .andExpect(status().isOk());

            assertThat(sumaDePartes()).isEqualByComparingTo("90.00");
        }

        @Test
        @DisplayName("alguien con cero partes no paga nada")
        void parteCero() throws Exception {
            crearGasto("60.00", "SHARES", """
                    [{"userId":%d,"value":1},{"userId":%d,"value":0},{"userId":%d,"value":2}]
                    """.formatted(idAna, idBeto, idCarla))
                    .andExpect(status().isOk());

            assertThat(parteDe(idBeto)).isEqualByComparingTo("0.00");
            assertThat(sumaDePartes()).isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("todas las partes a cero se rechaza")
        void todasACero() throws Exception {
            crearGasto("60.00", "SHARES", """
                    [{"userId":%d,"value":0},{"userId":%d,"value":0}]
                    """.formatted(idAna, idBeto))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Validaciones comunes")
    class Comunes {

        @ParameterizedTest
        @ValueSource(strings = {"EXACT", "PERCENTAGE", "SHARES"})
        @DisplayName("los repartos personalizados exigen la lista de participantes")
        void exigenSplits(String tipo) throws Exception {
            crearGasto("100.00", tipo, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("exige indicar el valor")));
        }

        @Test
        @DisplayName("un participante repetido se rechaza")
        void participanteRepetido() throws Exception {
            // Duplicarlo doblaria su parte y descuadraria el gasto sin que
            // ninguna estrategia lo detectase.
            crearGasto("100.00", "EXACT", """
                    [{"userId":%d,"value":50.00},{"userId":%d,"value":50.00}]
                    """.formatted(idAna, idAna))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Hay participantes repetidos en el reparto"));
        }

        @Test
        @DisplayName("un participante ajeno al grupo se rechaza")
        void participanteAjeno() throws Exception {
            long idMallory = registrar("Mallory", "mallory@test.com").get("userId").asLong();

            crearGasto("100.00", "SHARES", """
                    [{"userId":%d,"value":1},{"userId":%d,"value":1}]
                    """.formatted(idAna, idMallory))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Algun participante indicado no pertenece al grupo"));
        }

        @Test
        @DisplayName("un tipo de reparto inexistente se rechaza")
        void tipoInvalido() throws Exception {
            crearGasto("100.00", "APORTACION_VOLUNTARIA", null)
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Edicion")
    class Edicion {

        @Test
        @DisplayName("se puede cambiar el modo de reparto de un gasto")
        void cambiaDeModo() throws Exception {
            long gasto = idDe(crearGasto("90.00", null, null));

            mvc.perform(put("/api/expenses/{id}", gasto)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Cena","amount":90.00,"expenseDate":"2026-08-20",
                                     "splitType":"SHARES",
                                     "splits":[{"userId":%d,"value":4},{"userId":%d,"value":2}]}
                                    """.formatted(idAna, idBeto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.splitType").value("SHARES"))
                    .andExpect(jsonPath("$.splits.length()").value(2));

            assertThat(parteDe(idAna)).isEqualByComparingTo("60.00");
            assertThat(parteDe(idBeto)).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("el modo se conserva al releer el gasto")
        void seConserva() throws Exception {
            crearGasto("100.00", "PERCENTAGE", """
                    [{"userId":%d,"value":70},{"userId":%d,"value":30}]
                    """.formatted(idAna, idBeto));

            // Sin persistir el tipo, un gasto al 70/30 volveria a partes
            // iguales en cuanto alguien corrigiera su descripcion.
            mvc.perform(get("/api/groups/{g}/expenses", grupo).header("Authorization", bearer()))
                    .andExpect(jsonPath("$[0].splitType").value("PERCENTAGE"));
        }
    }

    // --- utilidades ---

    private String bearer() {
        return "Bearer " + token;
    }

    private ResultActions crearGasto(String importe, String tipo, String splitsJson) throws Exception {
        StringBuilder cuerpo = new StringBuilder("{\"description\":\"Gasto\",\"amount\":")
                .append(importe).append(",\"expenseDate\":\"2026-08-20\"");

        if (tipo != null) {
            cuerpo.append(",\"splitType\":\"").append(tipo).append('"');
        }
        if (splitsJson != null) {
            cuerpo.append(",\"splits\":").append(splitsJson.trim());
        }
        cuerpo.append('}');

        return mvc.perform(post("/api/groups/{g}/expenses", grupo)
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.toString()));
    }

    private long idDe(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private JsonNode gastos() throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private BigDecimal parteDe(long userId) throws Exception {
        for (JsonNode split : gastos().get(0).get("splits")) {
            if (split.get("userId").asLong() == userId) {
                return new BigDecimal(split.get("amountOwed").asText());
            }
        }
        throw new AssertionError("El usuario " + userId + " no participa en el gasto");
    }

    private BigDecimal sumaDePartes() throws Exception {
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode split : gastos().get(0).get("splits")) {
            suma = suma.add(new BigDecimal(split.get("amountOwed").asText()));
        }
        return suma;
    }

    private BigDecimal sumaDeBalances() throws Exception {
        JsonNode balances = json.readTree(mvc.perform(get("/api/groups/{g}/balances", grupo)
                        .header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString());

        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode b : balances) {
            suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
        }
        return suma;
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
                        .header("Authorization", bearer())
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
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }
}
