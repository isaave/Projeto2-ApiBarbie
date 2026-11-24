package com.barbie.dreamworld_api.ia.client;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PromptFotorealistaTest {

    @Test
    void todoPromptViraFotografiaRealista() {
        String prompt = PromptFotorealista.de(
                "fashion doll, pink floral dress, white sunglasses, sunny garden");

        assertTrue(prompt.contains("Photorealistic high-fashion editorial photograph"));
        assertTrue(prompt.contains("realistic skin, hair and fabric texture"));
        assertTrue(prompt.contains("pink floral dress"));
        assertTrue(prompt.contains("white sunglasses"));
    }

    @Test
    void removeEstilosQueConflitamComFoto() {
        String prompt = PromptFotorealista.de(
                "illustration of a cartoon doll, 3d render, anime painting");

        String minusculas = prompt.toLowerCase(Locale.ROOT);
        assertFalse(minusculas.contains("illustration"));
        assertFalse(minusculas.contains("cartoon"));
        assertFalse(minusculas.contains("3d render"));
        assertFalse(minusculas.contains("anime"));
        assertFalse(minusculas.contains("painting"));
        assertTrue(prompt.contains("photograph"));
    }

    @Test
    void promptVazioAindaGeraFoto() {
        String prompt = PromptFotorealista.de(null);
        assertTrue(prompt.contains("barbie-style fashion doll"));
        assertTrue(prompt.contains("Photorealistic"));
    }

    @Test
    void variacaoMantemFotoEAcrescentaPose() {
        String prompt = PromptFotorealista.comVariacao("fashion doll, lavender dress", 7);

        assertTrue(prompt.contains("Photorealistic high-fashion editorial photograph"));
        assertTrue(prompt.contains("lavender dress"));
        assertTrue(prompt.contains("alternate pose and camera angle"));
        assertTrue(prompt.contains("variation 7"));
    }

    @Test
    void etiquetaFotorealistaEIdempotente() {
        String uma = PromptFotorealista.etiquetar("fashion doll, pink dress");
        String duas = PromptFotorealista.etiquetar(uma);

        assertEquals(uma, duas);
        assertTrue(uma.contains("photorealistic studio photograph"));
        assertEquals(1, uma.toLowerCase(Locale.ROOT).split("photorealistic", -1).length - 1);
    }

    @Test
    void promptJaFotorealistaRecebeRegrasCompletas() {
        String prompt = PromptFotorealista.de("photorealistic fashion doll, pink dress");

        assertTrue(prompt.contains("pink dress"));
        assertTrue(prompt.contains("85mm lens"));
        assertTrue(prompt.contains("realistic skin, hair and fabric texture"));
        assertTrue(prompt.contains("True-to-life photography only"));
    }
}
