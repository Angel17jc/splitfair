package com.expensesplit.controller;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtros, categorias y paginacion del listado de gastos.
 */
@AutoConfigureMockMvc
class ExpenseFilteringTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String tokenAna;
    private long idAna;
    private String tokenBeto;
    private long idBeto;
    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();
        idBeto = beto.get("userId").asLong();

        grupo = crearGrupo();
        anadirMiembro(idBeto);
    }

    @Nested
    @DisplayName("Categorias")
    class Categorias {

        @Test
        @DisplayName("un gasto sin categoria queda como OTROS")
        void porDefectoOtros() throws Exception {
            crearGasto(tokenAna, "Sin clasificar", "10.00", null, "2026-08-20")
                    .andExpect(jsonPath("$.category").value("OTROS"));
        }

        @Test
        @DisplayName("se guarda la categoria indicada")
        void categoriaIndicada() throws Exception {
            crearGasto(tokenAna, "Cena", "10.00", "COMIDA", "2026-08-20")
                    .andExpect(jsonPath("$.category").value("COMIDA"));
        }

        @Test
        @DisplayName("una categoria inexistente se rechaza")
        void categoriaInvalida() throws Exception {
            // Con texto libre, "comida", "Comida" y "comidas" serian tres
            // categorias distintas y cualquier informe seria inservible.
            crearGasto(tokenAna, "Cena", "10.00", "CRIPTOMONEDAS", "2026-08-20")
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("filtrar por categoria devuelve solo esa")
        void filtraPorCategoria() throws Exception {
            crearGasto(tokenAna, "Cena", "10.00", "COMIDA", "2026-08-20");
            crearGasto(tokenAna, "Taxi", "20.00", "TRANSPORTE", "2026-08-20");
            crearGasto(tokenAna, "Bar", "15.00", "COMIDA", "2026-08-20");

            mvc.perform(get("/api/groups/{g}/expenses?category=COMIDA", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[*].category",
                            org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("COMIDA"))));
        }
    }

    @Nested
    @DisplayName("Filtros")
    class Filtros {

        @Test
        @DisplayName("por rango de fechas, ambos extremos incluidos")
        void porFechas() throws Exception {
            crearGasto(tokenAna, "Enero", "10.00", null, "2026-01-15");
            crearGasto(tokenAna, "Marzo", "10.00", null, "2026-03-15");
            crearGasto(tokenAna, "Junio", "10.00", null, "2026-06-15");

            mvc.perform(get("/api/groups/{g}/expenses?from=2026-03-15&to=2026-06-15", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.totalElements").value(2));

            // Un solo extremo tambien filtra.
            mvc.perform(get("/api/groups/{g}/expenses?from=2026-06-15", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].description").value("Junio"));
        }

        @Test
        @DisplayName("por quien adelanto el dinero")
        void porPagador() throws Exception {
            crearGasto(tokenAna, "De Ana", "10.00", null, "2026-08-20");
            crearGasto(tokenBeto, "De Beto", "10.00", null, "2026-08-20");

            mvc.perform(get("/api/groups/{g}/expenses?paidBy={u}", grupo, idBeto)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].description").value("De Beto"));
        }

        @Test
        @DisplayName("los filtros se combinan entre si")
        void combinados() throws Exception {
            crearGasto(tokenAna, "Cena enero", "10.00", "COMIDA", "2026-01-15");
            crearGasto(tokenAna, "Cena junio", "10.00", "COMIDA", "2026-06-15");
            crearGasto(tokenBeto, "Taxi junio", "10.00", "TRANSPORTE", "2026-06-15");

            mvc.perform(get("/api/groups/{g}/expenses?category=COMIDA&from=2026-06-01&paidBy={u}",
                            grupo, idAna)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].description").value("Cena junio"));
        }

        @Test
        @DisplayName("sin filtros se devuelve todo")
        void sinFiltros() throws Exception {
            crearGasto(tokenAna, "Uno", "10.00", "COMIDA", "2026-01-15");
            crearGasto(tokenAna, "Dos", "10.00", "OCIO", "2026-06-15");

            mvc.perform(get("/api/groups/{g}/expenses", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("un filtro sin resultados devuelve una pagina vacia, no un error")
        void sinResultados() throws Exception {
            crearGasto(tokenAna, "Cena", "10.00", "COMIDA", "2026-08-20");

            mvc.perform(get("/api/groups/{g}/expenses?category=SALUD", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("Paginacion")
    class Paginacion {

        @Test
        @DisplayName("ordena del mas reciente al mas antiguo y respeta page y size")
        void pagina() throws Exception {
            for (int mes = 1; mes <= 5; mes++) {
                crearGasto(tokenAna, "Mes " + mes, "10.00", null,
                        "2026-0%d-15".formatted(mes));
            }

            mvc.perform(get("/api/groups/{g}/expenses?page=0&size=2", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.last").value(false))
                    .andExpect(jsonPath("$.content[0].description").value("Mes 5"))
                    .andExpect(jsonPath("$.content[1].description").value("Mes 4"));

            mvc.perform(get("/api/groups/{g}/expenses?page=2&size=2", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].description").value("Mes 1"))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("el tamano de pagina se acota al maximo permitido")
        void tamanoAcotado() throws Exception {
            crearGasto(tokenAna, "Cena", "10.00", null, "2026-08-20");

            mvc.perform(get("/api/groups/{g}/expenses?size=100000", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        @DisplayName("cada gasto de la pagina llega con su reparto completo")
        void incluyeLosSplits() throws Exception {
            crearGasto(tokenAna, "Cena", "90.00", null, "2026-08-20");

            mvc.perform(get("/api/groups/{g}/expenses", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content[0].splits.length()").value(2))
                    .andExpect(jsonPath("$.content[0].splits[0].userName").exists());
        }
    }

    @Nested
    @DisplayName("Coste")
    class Coste {

        @Test
        @DisplayName("el numero de consultas no crece con el numero de gastos")
        void sinConsultasNMasUno() throws Exception {
            for (int i = 0; i < 25; i++) {
                crearGasto(tokenAna, "Gasto " + i, "10.00", null, "2026-08-20");
            }

            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();

            mvc.perform(get("/api/groups/{g}/expenses?size=25", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content.length()").value(25));

            // Pagina de identificadores, conteo, hidratacion de la pagina y
            // las consultas del control de acceso. Traer los splits en la
            // misma consulta paginada obligaria a Hibernate a cargar todas
            // las filas y paginar en memoria; recorrerlos despues de forma
            // perezosa dispararia una consulta por gasto.
            assertThat(stats.getPrepareStatementCount())
                    .as("consultas al listar 25 gastos")
                    .isLessThanOrEqualTo(8);
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private org.springframework.test.web.servlet.ResultActions crearGasto(
            String token, String descripcion, String importe, String categoria, String fecha)
            throws Exception {

        String cat = categoria == null ? "" : ",\"category\":\"%s\"".formatted(categoria);

        return mvc.perform(post("/api/groups/{g}/expenses", grupo)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description":"%s","amount":%s,"expenseDate":"%s"%s}
                        """.formatted(descripcion, importe, fecha, cat)));
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
}
