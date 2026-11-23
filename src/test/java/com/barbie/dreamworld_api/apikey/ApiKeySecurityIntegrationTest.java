package com.barbie.dreamworld_api.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.rate-limit.read-capacity=50",
        "app.rate-limit.write-capacity=50"
})
@AutoConfigureMockMvc
class ApiKeySecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ApiKeyRepository repository;

    private String gerarChave(String owner, String nivel) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"" + owner + "\",\"accessLevel\":\"" + nivel + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        return json.get("apiKey").asText();
    }

    @Test
    void rotaPublicaNaoCriaChaveAdmin() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"Tentativa ADMIN\",\"accessLevel\":\"ADMIN\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        assertTrue(result.getResponse().getContentAsString().contains("ADMIN"));
    }

    @Test
    void chaveSoApareceNaCriacaoENuncaEmConsultas() throws Exception {
        String chave = gerarChave("Teste Integração", "WRITE");

        assertNotNull(chave);
        assertFalse(chave.isBlank());
        assertTrue(repository.findAll().stream().noneMatch(k -> chave.equals(k.getKeyValue())));

        MvcResult listagem = mvc.perform(get("/api/auth/api-keys"))
                .andExpect(status().isOk())
                .andReturn();
        String json = listagem.getResponse().getContentAsString();

        assertFalse(json.contains(chave));
        assertTrue(json.contains("••••") || json.contains("oculta") || json.contains("\"apiKey\":null"));
    }

    @Test
    void chaveGeradaAutenticaEscritaSemExporORawKey() throws Exception {
        String chave = gerarChave("Teste Autenticação", "WRITE");

        // Com a chave, o filtro deixa passar e a validação do DTO responde 400.
        mvc.perform(post("/api/ia/assistente-estilo/dicas")
                        .header("X-API-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Sem a chave, o filtro.barreira responde 401.
        mvc.perform(post("/api/ia/assistente-estilo/dicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
