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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Moneda del grupo: se fija al crearlo y no cambia.
 */
@AutoConfigureMockMvc
class GroupCurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String token;

    @BeforeEach
    void registrarUsuario() throws Exception {
        token = json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@test.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Nested
    @DisplayName("Asignacion")
    class Asignacion {

        @Test
        @DisplayName("se guarda la moneda indicada al crear el grupo")
        void monedaIndicada() throws Exception {
            crearGrupo("EUR")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currency").value("EUR"));
        }

        @Test
        @DisplayName("sin indicarla se usa la moneda por defecto")
        void monedaPorDefecto() throws Exception {
            crearGrupo(null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currency").value("USD"));
        }

        @Test
        @DisplayName("el codigo se normaliza a mayusculas")
        void normalizaMayusculas() throws Exception {
            crearGrupo("eur").andExpect(jsonPath("$.currency").value("EUR"));
        }

        @Test
        @DisplayName("la moneda aparece tambien en el listado de grupos")
        void apareceEnElListado() throws Exception {
            crearGrupo("COP");

            mvc.perform(get("/api/groups").header("Authorization", bearer()))
                    .andExpect(jsonPath("$.content[0].currency").value("COP"));
        }
    }

    @Nested
    @DisplayName("Inmutabilidad")
    class Inmutabilidad {

        @Test
        @DisplayName("editar el grupo no cambia su moneda")
        void noSePuedeCambiar() throws Exception {
            long grupo = idDe(crearGrupo("EUR"));

            // Aunque el cliente mande currency, el PATCH lo ignora.
            mvc.perform(patch("/api/groups/{id}", grupo)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Renombrado","currency":"JPY"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Renombrado"))
                    // Cambiarla reinterpretaria todos los gastos ya
                    // registrados sin tocar un solo importe: 50.00 dejaria de
                    // ser cincuenta euros para pasar a ser otra cosa.
                    .andExpect(jsonPath("$.currency").value("EUR"));
        }
    }

    @Nested
    @DisplayName("Validacion")
    class Validacion {

        @ParameterizedTest
        @ValueSource(strings = {"XXX1", "E", "euros", "123", "ZZZ"})
        @DisplayName("un codigo que no es ISO 4217 se rechaza")
        void codigoInvalido(String codigo) throws Exception {
            crearGrupo(codigo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.currency").exists());
        }

        @ParameterizedTest
        @ValueSource(strings = {"JPY", "KRW", "CLP"})
        @DisplayName("una moneda sin decimales se rechaza con un motivo claro")
        void monedaSinDecimales(String codigo) throws Exception {
            // Todo el reparto trabaja en centimos y el esquema guarda
            // NUMERIC(12,2). Un gasto de 1000 JPY entre tres daria cuotas de
            // 333.33 yenes, un importe que no existe. Es preferible
            // rechazarlas que producir cuentas incorrectas en silencio.
            crearGrupo(codigo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.currency").value(
                            org.hamcrest.Matchers.containsString("decimales")));
        }

        @Test
        @DisplayName("las monedas de dos decimales habituales se aceptan")
        void monedasSoportadas() throws Exception {
            for (String codigo : new String[]{"USD", "EUR", "GBP", "MXN", "COP", "ARS", "BRL"}) {
                crearGrupo(codigo)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.currency").value(codigo));
            }
        }
    }

    // --- utilidades ---

    private String bearer() {
        return "Bearer " + token;
    }

    private ResultActions crearGrupo(String currency) throws Exception {
        String cuerpo = currency == null
                ? "{\"name\":\"Piso\",\"description\":\"test\"}"
                : json.writeValueAsString(java.util.Map.of(
                "name", "Piso", "description", "test", "currency", currency));

        return mvc.perform(post("/api/groups")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private long idDe(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }
}
