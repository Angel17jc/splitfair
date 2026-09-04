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

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baja de cuenta.
 *
 * <p>La decision de fondo es que <b>no se borra la fila</b>. El usuario
 * aparece en gastos, repartos y liquidaciones; borrarlo dejaria apuntes sin
 * dueno y los balances del grupo dejarian de sumar cero, es decir, dinero
 * evaporandose del informe sin que nadie lo hubiera pagado. Lo que se elimina
 * son los datos personales.
 *
 * <p>Estos tests fijan las dos mitades: que los datos personales desaparecen
 * de verdad, y que la contabilidad sobrevive intacta.
 */
@AutoConfigureMockMvc
class BajaDeCuentaTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenAna;
    private String tokenBeto;
    private Long idAna;
    private Long grupo;

    @BeforeEach
    void escenario() throws Exception {
        JsonNode ana = registrar("Ana Perez", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto Ruiz", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();

        grupo = json.readTree(mvc.perform(post("/api/groups")
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Piso\",\"currency\":\"EUR\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/groups/{g}/members/{u}", grupo, beto.get("userId").asLong())
                .header("Authorization", bearer(tokenAna)));

        // Ana adelanta 90,00 que se reparten entre los dos: Beto le debe 45.
        mvc.perform(post("/api/groups/{g}/expenses", grupo)
                .header("Authorization", bearer(tokenAna))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Compra\",\"amount\":90.00,\"expenseDate\":\"2026-09-01\"}"));
    }

    @Nested
    @DisplayName("Antes de dar de baja")
    class Requisitos {

        @Test
        @DisplayName("hace falta la contrasena actual")
        void exigeContrasena() throws Exception {
            // Un access token robado no debe bastar para destruir la cuenta.
            mvc.perform(delete("/api/users/me")
                            .header("Authorization", bearer(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"la-que-no-es\"}"))
                    .andExpect(status().isBadRequest());

            // Y la cuenta sigue en pie.
            mvc.perform(get("/api/users/me").header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("ana@test.com"));
        }

        @Test
        @DisplayName("sin sesion no se puede dar de baja a nadie")
        void exigeSesion() throws Exception {
            mvc.perform(delete("/api/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"password123\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Los datos personales desaparecen")
    class DatosPersonales {

        @Test
        @DisplayName("nombre y correo dejan de estar en la base")
        void seAnonimiza() throws Exception {
            darDeBaja();

            Map<String, Object> fila = jdbc.queryForMap(
                    "SELECT name, email, deleted_at FROM users WHERE id = ?", idAna);

            assertThat((String) fila.get("name")).isEqualTo("Usuario eliminado");
            assertThat((String) fila.get("email"))
                    .doesNotContain("ana@test.com")
                    .endsWith("@invalid");
            assertThat(fila.get("deleted_at")).isNotNull();
        }

        @Test
        @DisplayName("el correo anterior ya no sirve para entrar")
        void noSePuedeVolverAEntrar() throws Exception {
            darDeBaja();

            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"ana@test.com\",\"password\":\"password123\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el access token que ya tenia deja de valer al instante")
        void elTokenVivoCaduca() throws Exception {
            darDeBaja();

            // El sujeto del token es el correo. Al sustituirlo, deja de
            // resolver a ningun usuario. Sin esto, quien se da de baja
            // seguiria operando sus quince minutos restantes.
            mvc.perform(get("/api/users/me").header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("La contabilidad sobrevive")
    class Contabilidad {

        @Test
        @DisplayName("el gasto que pago sigue existiendo")
        void elGastoSeConserva() throws Exception {
            darDeBaja();

            String cuerpo = mvc.perform(get("/api/groups/{g}/expenses", grupo)
                            .header("Authorization", bearer(tokenBeto)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode gasto = json.readTree(cuerpo).get("content").get(0);
            assertThat(gasto.get("amount").decimalValue()).isEqualByComparingTo("90.00");
            // Quien lo pago sigue identificado, pero ya no por su nombre real.
            assertThat(gasto.get("paidByName").asText()).isEqualTo("Usuario eliminado");
        }

        @Test
        @DisplayName("los balances del grupo siguen sumando cero")
        void losBalancesCuadran() throws Exception {
            darDeBaja();

            String cuerpo = mvc.perform(get("/api/groups/{g}/balances", grupo)
                            .header("Authorization", bearer(tokenBeto)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode balances = json.readTree(cuerpo).get("balances");

            // Esta es la invariante que se romperia si la baja borrara al
            // usuario o lo sacara del grupo: los 45 que debe Beto no tendrian
            // acreedor y la suma dejaria de ser cero. Un informe que no cuadra
            // es peor que uno que falta, porque parece correcto.
            BigDecimal suma = BigDecimal.ZERO;
            for (JsonNode b : balances) {
                suma = suma.add(b.get("netBalance").decimalValue());
            }
            assertThat(suma).isEqualByComparingTo("0.00");
            assertThat(balances).hasSize(2);
        }

        @Test
        @DisplayName("la deuda de los demas no se cancela sola")
        void laDeudaSigueAhi() throws Exception {
            darDeBaja();

            String cuerpo = mvc.perform(get("/api/groups/{g}/balances", grupo)
                            .header("Authorization", bearer(tokenBeto)))
                    .andReturn().getResponse().getContentAsString();

            // Beto debe 45,00 antes y despues. Que alguien se de de baja no
            // salda las deudas que tenia con esa persona el resto del grupo.
            JsonNode deBeto = null;
            for (JsonNode b : json.readTree(cuerpo).get("balances")) {
                if ("Beto Ruiz".equals(b.get("userName").asText())) {
                    deBeto = b;
                }
            }
            assertThat(deBeto).isNotNull();
            assertThat(deBeto.get("netBalance").decimalValue()).isEqualByComparingTo("-45.00");
        }
    }

    // --- utilidades ---

    private void darDeBaja() throws Exception {
        mvc.perform(delete("/api/users/me")
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\"}"))
                .andExpect(status().isNoContent());
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        String cuerpo = "{\"name\":\"" + nombre + "\",\"email\":\"" + email
                + "\",\"password\":\"password123\"}";

        return json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn().getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
