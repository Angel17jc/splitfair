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
 * Reparto del gasto por categoria y por mes.
 *
 * <p>El endpoint existe para que el cliente no sume dinero: sobre un listado
 * paginado obtendria totales parciales, y en JavaScript los obtendria en coma
 * flotante. Estos tests fijan lo que hace que merezca la pena: que los totales
 * cuadren con el gasto real y que no dependan de cuantos gastos haya.
 */
@AutoConfigureMockMvc
class GroupAnalyticsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String tokenAna;
    private String tokenBeto;
    private long grupo;

    @BeforeEach
    void escenario() throws Exception {
        tokenAna = registrar("Ana", "ana@test.com");
        tokenBeto = registrar("Beto", "beto@test.com");
        grupo = crearGrupo(tokenAna);
    }

    @Nested
    @DisplayName("Por categoria")
    class PorCategoria {

        @Test
        @DisplayName("agrupa los importes y cuenta los gastos de cada categoria")
        void agrupaPorCategoria() throws Exception {
            gasto("Cena", "20.00", "COMIDA", "2026-03-10");
            gasto("Comida", "10.50", "COMIDA", "2026-03-11");
            gasto("Taxi", "5.25", "TRANSPORTE", "2026-03-12");

            mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.byCategory.length()").value(2))
                    // De mayor a menor gasto: es el orden en que se quiere leer.
                    .andExpect(jsonPath("$.byCategory[0].category").value("COMIDA"))
                    .andExpect(jsonPath("$.byCategory[0].total").value(30.50))
                    .andExpect(jsonPath("$.byCategory[0].count").value(2))
                    .andExpect(jsonPath("$.byCategory[1].category").value("TRANSPORTE"))
                    .andExpect(jsonPath("$.byCategory[1].total").value(5.25));
        }

        @Test
        @DisplayName("solo aparecen las categorias con gasto")
        void sinCategoriasVacias() throws Exception {
            gasto("Cena", "20.00", "COMIDA", "2026-03-10");

            // Ocho categorias existen en el enum; una sola tiene gasto. Devolver
            // las otras siete a cero obligaria al cliente a filtrarlas para no
            // pintar un grafico lleno de sectores invisibles.
            mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.byCategory.length()").value(1));
        }

        @Test
        @DisplayName("un grupo sin gastos responde con listas vacias y total cero")
        void grupoVacio() throws Exception {
            mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpent").value(0.00))
                    .andExpect(jsonPath("$.byCategory").isEmpty())
                    .andExpect(jsonPath("$.byMonth").isEmpty());
        }
    }

    @Nested
    @DisplayName("Por mes")
    class PorMes {

        @Test
        @DisplayName("agrupa por mes natural y ordena del mas antiguo al mas reciente")
        void agrupaPorMes() throws Exception {
            gasto("Enero", "10.00", "OTROS", "2026-01-15");
            gasto("Marzo", "30.00", "OTROS", "2026-03-01");
            gasto("Marzo otra vez", "5.00", "OTROS", "2026-03-31");

            mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.byMonth.length()").value(2))
                    .andExpect(jsonPath("$.byMonth[0].month").value("2026-01"))
                    .andExpect(jsonPath("$.byMonth[0].total").value(10.00))
                    .andExpect(jsonPath("$.byMonth[1].month").value("2026-03"))
                    .andExpect(jsonPath("$.byMonth[1].total").value(35.00))
                    .andExpect(jsonPath("$.byMonth[1].count").value(2));
        }

        @Test
        @DisplayName("el mes se rellena con cero a la izquierda")
        void mesConDosDigitos() throws Exception {
            gasto("Septiembre", "10.00", "OTROS", "2026-09-15");

            // "2026-9" romperia el orden alfabetico del cliente, que colocaria
            // septiembre despues de diciembre ("2026-12" < "2026-9").
            mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.byMonth[0].month").value("2026-09"));
        }

        @Test
        @DisplayName("los meses cruzan de ano sin mezclarse")
        void mesesDeAnosDistintos() throws Exception {
            gasto("Diciembre", "10.00", "OTROS", "2025-12-31");
            gasto("Enero", "20.00", "OTROS", "2026-01-01");

            mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(jsonPath("$.byMonth.length()").value(2))
                    .andExpect(jsonPath("$.byMonth[0].month").value("2025-12"))
                    .andExpect(jsonPath("$.byMonth[1].month").value("2026-01"));
        }
    }

    @Nested
    @DisplayName("Invariantes")
    class Invariantes {

        @Test
        @DisplayName("el total coincide con el de los balances y con las dos agrupaciones")
        void losTotalesCuadran() throws Exception {
            gasto("Cena", "33.33", "COMIDA", "2026-03-10");
            gasto("Taxi", "10.01", "TRANSPORTE", "2026-04-11");
            gasto("Hotel", "56.66", "ALOJAMIENTO", "2026-04-12");

            JsonNode analitica = json.readTree(mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andReturn().getResponse().getContentAsString());

            BigDecimal total = analitica.get("totalSpent").decimalValue();

            // Es la comprobacion que da sentido al endpoint: si las tres cifras
            // no coincidieran, el grafico contaria una historia distinta de la
            // que cuentan los balances sobre los mismos gastos.
            assertThat(sumar(analitica.get("byCategory"))).isEqualByComparingTo(total);
            assertThat(sumar(analitica.get("byMonth"))).isEqualByComparingTo(total);

            String balances = mvc.perform(get("/api/groups/{g}/balances", grupo)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(balances).get("totalSpent").decimalValue())
                    .isEqualByComparingTo(total);
        }

        private BigDecimal sumar(JsonNode filas) {
            BigDecimal suma = BigDecimal.ZERO;
            for (JsonNode fila : filas) {
                suma = suma.add(fila.get("total").decimalValue());
            }
            return suma;
        }
    }

    @Nested
    @DisplayName("Acceso")
    class Acceso {

        @Test
        @DisplayName("un extrano al grupo recibe 403")
        void extranoNoVe() throws Exception {
            // La analitica revela cuanto y en que gasta el grupo. Es
            // exactamente lo que la vista previa de invitaciones se cuida de no
            // filtrar, asi que aqui tiene que exigir pertenencia.
            mvc.perform(get("/api/groups/{g}/analytics", grupo)
                            .header("Authorization", "Bearer " + tokenBeto))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sin autenticar responde 401")
        void sinAutenticar() throws Exception {
            mvc.perform(get("/api/groups/{g}/analytics", grupo))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un grupo inexistente responde 404")
        void grupoInexistente() throws Exception {
            mvc.perform(get("/api/groups/{g}/analytics", 999999)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNotFound());
        }
    }

    // --- utilidades ---

    private String registrar(String nombre, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private long crearGrupo(String token) throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Analitica"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void gasto(String descripcion, String importe, String categoria, String fecha)
            throws Exception {
        mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","amount":%s,"expenseDate":"%s","category":"%s"}
                                """.formatted(descripcion, importe, fecha, categoria)))
                .andExpect(status().isOk());
    }
}
