package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.ia.dto.DicasLookRequest;
import com.barbie.dreamworld_api.ia.dto.DicasLookResponse;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookResponse;
import com.barbie.dreamworld_api.model.Acessorio;
import com.barbie.dreamworld_api.model.Barbie;
import com.barbie.dreamworld_api.model.Profissao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LookLocalClientTest {

    private final LookLocalClient client = new LookLocalClient();

    private Barbie barbie() {
        Profissao profissao = new Profissao();
        profissao.setNomeProfissao("Cientista");

        Acessorio oculos = new Acessorio();
        oculos.setNomeItem("Óculos de sol");

        Acessorio bolsa = new Acessorio();
        bolsa.setNomeItem("Bolsa branca");

        Barbie barbie = new Barbie();
        barbie.setId(1L);
        barbie.setNome("Barbie Cientista");
        barbie.setColecao("Barbie Space");
        barbie.setProfissao(profissao);
        barbie.setAcessorios(List.of(oculos, bolsa));
        return barbie;
    }

    @Test
    void lookFallbackUsaAcessoriosReaisESinalizaOrigem() {
        SugestaoLookResponse look = client.gerarLook(
                barbie(), "festa no jardim", "tons pastel e rosa");

        assertEquals(List.of("Óculos de sol", "Bolsa branca"),
                look.getAcessoriosDoAcervoRecomendados());
        assertTrue(look.getTituloLook().contains("Barbie Space"));
        assertTrue(look.getResumo().contains("Barbie Cientista"));
        assertTrue(look.getPaletaCores().contains("Rosa pastel"));
        assertEquals(3, look.getItensSugeridos().size());
        assertNotNull(look.getAviso());
        assertTrue(look.getAviso().contains("acervo real"));
        assertNotNull(look.getPromptImagem());
    }

    @Test
    void dicasFallbackCitamAcessorioDoAcervo() {
        DicasLookRequest request = new DicasLookRequest();
        request.setTituloLook("Look floral");
        request.setPaletaCores(List.of("rosa", "branco"));

        DicasLookResponse dicas = client.gerarDicas(barbie(), request, "festa no jardim");

        assertEquals(4, dicas.getDicas().size());
        assertTrue(dicas.getDicas().get(0).getDescricao().contains("Óculos de sol"));
        assertNotNull(dicas.getAviso());
        assertNotNull(dicas.getToqueFinal());
    }
}
