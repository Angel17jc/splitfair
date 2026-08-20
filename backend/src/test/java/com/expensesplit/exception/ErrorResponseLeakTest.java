package com.expensesplit.exception;

import com.expensesplit.AbstractIntegrationTest;
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
 * Verifica que las respuestas de error no filtran detalles internos.
 *
 * <p>Antes, el manejador generico devolvia {@code ex.getMessage()} de
 * cualquier excepcion no controlada, con lo que una peticion malformada podia
 * hacer aflorar sentencias SQL, nombres de tablas y rutas del sistema.
 */
@AutoConfigureMockMvc
class ErrorResponseLeakTest extends AbstractIntegrationTest {

    /**
     * Fragmentos que nunca deben aparecer en el cuerpo de una respuesta: son
     * los que delatan la tecnologia, el esquema o la maquina.
     */
    private static final String[] FILTRACIONES = {
            "org.springframework", "org.hibernate", "org.postgresql",
            "com.expensesplit", "jakarta.", "java.lang",
            "select ", "insert into", "update ", "Exception",
            "C:\\", "/usr/", "nested exception"
    };

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String token;

    @BeforeEach
    void registrarUsuario() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@test.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        token = json.readTree(body).get("accessToken").asText();
    }

    @Test
    @DisplayName("Un JSON malformado devuelve 400 sin exponer el parser ni el contenido")
    void jsonMalformado() throws Exception {
        String cuerpo = mvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json "))
                .andExpect(status().isBadRequest())
                // Afirmacion positiva: el mensaje es exactamente el seguro.
                // Comprobar solo que no aparecen ciertas cadenas es mas debil
                // de lo que parece, porque la lista negra nunca es completa:
                // el mensaje de Jackson para este caso no contiene ninguna.
                .andExpect(jsonPath("$.message").value("El cuerpo de la peticion no es un JSON valido"))
                .andReturn().getResponse().getContentAsString();

        assertSinFiltraciones(cuerpo);
    }

    @Test
    @DisplayName("Un tipo de parametro incorrecto devuelve 400 sin traza interna")
    void tipoDeParametroIncorrecto() throws Exception {
        String cuerpo = mvc.perform(get("/api/groups/{id}", "no-es-un-numero")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parametro 'id' no tiene un valor valido"))
                .andReturn().getResponse().getContentAsString();

        assertSinFiltraciones(cuerpo);
    }

    @Test
    @DisplayName("Un email duplicado devuelve 400 con un mensaje util, no un volcado de la base")
    void emailDuplicado() throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Otra Ana","email":"ana@test.com","password":"password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("Ya existe una cuenta con ese email");
        assertSinFiltraciones(cuerpo);
    }

    @Test
    @DisplayName("Los errores de validacion detallan el campo, no la implementacion")
    void erroresDeValidacion() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"no-es-email","password":"corta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("Todas las respuestas de error comparten el mismo formato")
    void formatoUnificado() throws Exception {
        mvc.perform(get("/api/groups/{id}", 999_999L).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/groups/999999"))
                // traceId solo se emite en errores inesperados
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    @DisplayName("Credenciales invalidas no revelan si el email existe")
    void credencialesInvalidasNoEnumeranCuentas() throws Exception {
        String existente = intentarLogin("ana@test.com", "contrasena-incorrecta");
        String inexistente = intentarLogin("nadie@test.com", "contrasena-incorrecta");

        // Si los mensajes difirieran, se podria averiguar que cuentas estan
        // registradas probando emails uno a uno.
        assertThat(existente).contains("Credenciales invalidas");
        assertThat(inexistente).contains("Credenciales invalidas");
        assertSinFiltraciones(existente);
        assertSinFiltraciones(inexistente);
    }

    private String intentarLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    private void assertSinFiltraciones(String cuerpo) {
        assertThat(cuerpo)
                .as("cuerpo de la respuesta de error")
                .doesNotContain(FILTRACIONES);
    }
}
