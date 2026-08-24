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
 * Listado de los grupos del usuario autenticado.
 *
 * <p>Era el unico endpoint de la especificacion original que nunca llego a
 * implementarse.
 */
@AutoConfigureMockMvc
class GroupListingTest extends AbstractIntegrationTest {

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

    @BeforeEach
    void registrarUsuarios() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();
        idBeto = beto.get("userId").asLong();
    }

    @Nested
    @DisplayName("Contenido del listado")
    class Contenido {

        @Test
        @DisplayName("un usuario sin grupos recibe una pagina vacia, no un error")
        void sinGrupos() throws Exception {
            mvc.perform(get("/api/groups").header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("solo aparecen los grupos propios, nunca los ajenos")
        void soloLosPropios() throws Exception {
            crearGrupo(tokenAna, "Piso de Ana");
            crearGrupo(tokenBeto, "Viaje de Beto");

            mvc.perform(get("/api/groups").header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Piso de Ana"));
        }

        @Test
        @DisplayName("incluye el rol del usuario en cada grupo")
        void incluyeElRol() throws Exception {
            long grupo = crearGrupo(tokenAna, "Piso");
            anadirMiembro(tokenAna, grupo, idBeto);

            // Quien crea el grupo es su administradora.
            mvc.perform(get("/api/groups").header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                    .andExpect(jsonPath("$.content[0].memberCount").value(2));

            // Quien es anadido entra como miembro corriente.
            mvc.perform(get("/api/groups").header("Authorization", bearer(tokenBeto)))
                    .andExpect(jsonPath("$.content[0].role").value("MEMBER"));
        }

        @Test
        @DisplayName("el balance propio se calcula por grupo y con el signo correcto")
        void balancePropio() throws Exception {
            long piso = crearGrupo(tokenAna, "Piso");
            anadirMiembro(tokenAna, piso, idBeto);
            crearGasto(tokenAna, piso, "Alquiler", "100.00");

            long viaje = crearGrupo(tokenAna, "Viaje");
            anadirMiembro(tokenAna, viaje, idBeto);
            crearGasto(tokenBeto, viaje, "Hotel", "50.00");

            JsonNode deAna = leerListado(tokenAna);
            // Ana adelanto 100 y le tocan 50 -> le deben 50.
            assertThat(balanceDe(deAna, piso)).isEqualByComparingTo("50.00");
            // En el viaje pago Beto -> Ana debe 25.
            assertThat(balanceDe(deAna, viaje)).isEqualByComparingTo("-25.00");

            JsonNode deBeto = leerListado(tokenBeto);
            assertThat(balanceDe(deBeto, piso)).isEqualByComparingTo("-50.00");
            assertThat(balanceDe(deBeto, viaje)).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("un grupo sin gastos aparece con balance 0.00")
        void grupoSinGastos() throws Exception {
            long grupo = crearGrupo(tokenAna, "Recien creado");

            assertThat(balanceDe(leerListado(tokenAna), grupo)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("el listado no arrastra el detalle de miembros ni gastos")
        void esUnResumen() throws Exception {
            long grupo = crearGrupo(tokenAna, "Piso");
            crearGasto(tokenAna, grupo, "Cena", "30.00");

            String cuerpo = mvc.perform(get("/api/groups").header("Authorization", bearer(tokenAna)))
                    .andReturn().getResponse().getContentAsString();

            // Un listado que arrastrase todo el detalle crece sin control con
            // el numero de grupos.
            assertThat(cuerpo).doesNotContain("members", "splits", "expenses");
        }
    }

    @Nested
    @DisplayName("Paginacion")
    class Paginacion {

        @Test
        @DisplayName("respeta page y size, y ordena del mas reciente al mas antiguo")
        void pagina() throws Exception {
            for (int i = 1; i <= 5; i++) {
                crearGrupo(tokenAna, "Grupo " + i);
            }

            mvc.perform(get("/api/groups?page=0&size=2").header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.last").value(false))
                    // El ultimo creado encabeza el listado.
                    .andExpect(jsonPath("$.content[0].name").value("Grupo 5"));

            mvc.perform(get("/api/groups?page=2&size=2").header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("un tamano de pagina desmedido se recorta al maximo permitido")
        void tamanoAcotado() throws Exception {
            crearGrupo(tokenAna, "Piso");

            // Sin tope, esto permitiria pedir cien mil filas por peticion.
            mvc.perform(get("/api/groups?size=100000").header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        @DisplayName("valores negativos no rompen la peticion")
        void valoresNegativos() throws Exception {
            crearGrupo(tokenAna, "Piso");

            mvc.perform(get("/api/groups?page=-5&size=-1").header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(1));
        }
    }

    @Nested
    @DisplayName("Coste")
    class Coste {

        @Test
        @DisplayName("el numero de consultas no crece con el numero de grupos")
        void sinConsultasNMasUno() throws Exception {
            for (int i = 0; i < 12; i++) {
                long grupo = crearGrupo(tokenAna, "Grupo " + i);
                crearGasto(tokenAna, grupo, "Gasto", "20.00");
            }

            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();

            mvc.perform(get("/api/groups?size=50").header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content.length()").value(12));

            // Pagina, conteo, las dos agregaciones de balance y la resolucion
            // del usuario autenticado. Calcular el balance grupo a grupo
            // convertiria el listado en N+1 sobre la operacion mas cara de la
            // aplicacion: con 12 grupos pasaria de 5 a mas de 30.
            assertThat(stats.getPrepareStatementCount())
                    .as("consultas al listar 12 grupos")
                    .isLessThanOrEqualTo(8);
        }
    }

    @Nested
    @DisplayName("Seguridad")
    class Seguridad {

        @Test
        @DisplayName("sin autenticar responde 401")
        void sinAutenticar() throws Exception {
            mvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode leerListado(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/groups?size=50")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private java.math.BigDecimal balanceDe(JsonNode listado, long groupId) {
        for (JsonNode g : listado.get("content")) {
            if (g.get("id").asLong() == groupId) {
                return new java.math.BigDecimal(g.get("myBalance").asText());
            }
        }
        throw new AssertionError("El grupo " + groupId + " no aparece en el listado");
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

    private long crearGrupo(String token, String nombre) throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"test"}
                                """.formatted(nombre)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void anadirMiembro(String token, long groupId, long userId) throws Exception {
        mvc.perform(post("/api/groups/{groupId}/members/{userId}", groupId, userId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void crearGasto(String token, long groupId, String descripcion, String importe) throws Exception {
        mvc.perform(post("/api/groups/{id}/expenses", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","amount":%s,"expenseDate":"2026-08-20"}
                                """.formatted(descripcion, importe)))
                .andExpect(status().isOk());
    }
}
