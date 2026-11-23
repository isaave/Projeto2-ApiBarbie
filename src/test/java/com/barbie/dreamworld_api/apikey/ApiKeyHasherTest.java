package com.barbie.dreamworld_api.apikey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyHasherTest {

    @Test
    void hashEDeterministicoENaoDevolveAChave() {
        String chave = "8b1c2d3e-4f5a-6789-abcd-ef0123456789";

        String hash = ApiKeyHasher.hash(chave);

        assertEquals(64, hash.length());
        assertEquals(hash, ApiKeyHasher.hash(chave));
        assertFalse(hash.contains(chave));
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void chavesDiferentesGeramHashesDiferentes() {
        assertNotEquals(ApiKeyHasher.hash("chave-a"), ApiKeyHasher.hash("chave-b"));
    }

    @Test
    void maskNuncaMostraAChaveCompleta() {
        String chave = "8b1c2d3e-4f5a-6789-abcd-ef0123456789";

        String mask = ApiKeyHasher.mask(chave);

        assertTrue(mask.startsWith("8b1c"));
        assertFalse(mask.contains(chave));
        assertEquals("(vazia)", ApiKeyHasher.mask("  "));
    }
}
