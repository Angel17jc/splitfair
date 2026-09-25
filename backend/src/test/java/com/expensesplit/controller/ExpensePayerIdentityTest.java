package com.expensesplit.controller;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Quien pago un gasto viaja por identificador, no solo por nombre.
 *
 * <p>La respuesta llevaba unicamente {@code paidByName}, y con un nombre no se
 * puede decidir nada: dos personas del mismo grupo pueden llamarse igual. El
 * cliente no tenia forma fiable de saber si el usuario actual pago un gasto, y
 * de eso depende ofrecer o no editar y borrar —permisos que el backend ya
 * restringe al pagador o a un administrador—.
 *
 * <p>El escenario de estos tests es justo ese: <b>dos miembros con el mismo
 * nombre</b>. Es el caso que el nombre no puede distinguir, asi que es el
 * unico que demuestra que el identificador hace falta. Con nombres distintos,
 * un cliente que comparase nombres pasaria el test y seguiria estando mal.
 */
@AutoConfigureMockMvc
class ExpensePayerIdentityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String tokenPrimera;
    private long idPrimera;
    private long idSegunda;
    private long grupo;

    @BeforeEach
    void dosMiembrosLlamadosIgual() throws Exception {
        JsonNode primera = registrar("Ana Garcia", "ana1@test.com");
        tokenPrimera = primera.get("accessToken").asText();
        idPrimera = primera.get("userId").asLong();

        JsonNode segunda = registrar("Ana Garcia", "ana2@test.com");
        idSegunda = segunda.get("userId").asLong();

        grupo = json.readTree(mvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + tokenPrimera)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Piso\",\"currency\":\"EUR\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/groups/{g}/members/{u}", grupo, idSegunda)
                        .header("Authorization", "Bearer " + tokenPrimera))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el gasto identifica a su pagador entre dos homonimos")
    void distingueEntreHomonimos() throws Exception {
        long gasto = crearGasto();

        JsonNode respuesta = json.readTree(mvc.perform(get("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", "Bearer " + tokenPrimera))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("content").get(0);

        assertThat(respuesta.get("id").asLong()).isEqualTo(gasto);
        assertThat(respuesta.get("paidByName").asText()).isEqualTo("Ana Garcia");

        // Las dos se llaman igual, asi que esta es la unica forma de saber
        // cual de las dos pago.
        assertThat(respuesta.get("paidByUserId").asLong())
                .as("el gasto lo pago la primera Ana, no la segunda")
                .isEqualTo(idPrimera)
                .isNotEqualTo(idSegunda);
    }

    @Test
    @DisplayName("al crear un gasto la respuesta ya trae el identificador del pagador")
    void tambienAlCrearlo() throws Exception {
        // El cliente pinta el gasto recien creado sin volver a pedir la lista,
        // asi que si el campo faltara aqui los botones de editar y borrar no
        // apareceran hasta recargar.
        mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", "Bearer " + tokenPrimera)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Cena\",\"amount\":30.00,"
                                + "\"expenseDate\":\"2026-09-25\"}"))
                .andExpect(jsonPath("$.paidByUserId").value(idPrimera));
    }

    // --- utilidades ---

    private long crearGasto() throws Exception {
        String cuerpo = mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", "Bearer " + tokenPrimera)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Compra\",\"amount\":40.00,"
                                + "\"expenseDate\":\"2026-09-20\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("id").asLong();
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        String cuerpo = "{\"name\":\"" + nombre + "\",\"email\":\"" + email
                + "\",\"password\":\"password123\"}";

        return json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn().getResponse().getContentAsString());
    }
}
