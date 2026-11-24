package com.barbie.dreamworld_api.ia;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.rate-limit.read-capacity=50",
        "app.rate-limit.write-capacity=50",
        "gemini.api-key=",
        "image.pollinations.enabled=false"
})
@AutoConfigureMockMvc
class HistoricoChatIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private String gerarChave() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"Teste Histórico\",\"accessLevel\":\"WRITE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(resultado.getResponse().getContentAsString()).get("apiKey").asText();
    }

    private String corpo(String historico) {
        return """
                {
                  "barbieId": 1,
                  "ocasiao": "festa no jardim",
                  "preferencias": "tons pastel",
                  "historico": %s
                }
                """.formatted(historico);
    }

    @Test
    void aceitaHistoricoDentroDoLimite() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/ia/assistente-estilo")
                        .header("X-API-Key", gerarChave())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("""
                                [
                                  {"papel":"user","texto":"quero algo discreto"},
                                  {"papel":"ia","texto":"Sugeri tons pastel"},
                                  {"papel":"user","texto":"e com flores?"}
                                ]
                                """)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(resultado.getResponse().getContentAsString());
        assertFalse(json.get("tituloLook").asText().isBlank());
    }

    @Test
    void rejeitaHistoricoMaiorQueSeisMensagens() throws Exception {
        String historico = """
                [
                  {"papel":"user","texto":"1"}, {"papel":"ia","texto":"2"},
                  {"papel":"user","texto":"3"}, {"papel":"ia","texto":"4"},
                  {"papel":"user","texto":"5"}, {"papel":"ia","texto":"6"},
                  {"papel":"user","texto":"7"}
                ]
                """;

        mvc.perform(post("/api/ia/assistente-estilo")
                        .header("X-API-Key", gerarChave())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(historico)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaPapelForaDoPadrao() throws Exception {
        mvc.perform(post("/api/ia/assistente-estilo")
                        .header("X-API-Key", gerarChave())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("[{\"papel\":\"admin\",\"texto\":\"oi\"}]")))
                .andExpect(status().isBadRequest());
    }
}
