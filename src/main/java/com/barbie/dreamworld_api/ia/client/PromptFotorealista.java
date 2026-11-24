package com.barbie.dreamworld_api.ia.client;

import java.util.regex.Pattern;

/**
 * Fonte única do prompt de imagem usado por TODOS os geradores (Gemini e
 * Pollinations). Garante que qualquer look — gerado pela IA, pelo fallback
 * local ou reenviado pelo frontend para "Outra imagem" — vire uma FOTO
 * realista, e não uma ilustração, cartoon ou render 3D.
 *
 * As peças, cores e acessórios descritos no look são preservados: só o estilo
 * de renderização é forçado para fotografia editorial.
 */
public final class PromptFotorealista {

    private static final Pattern ESTILO_CONFLITANTE = Pattern.compile(
            "(?i)\\b(illustration|ilustra[cç][aã]o|cartoon|desenho|drawing|3d render|anime|"
                    + "painting|pintura|sketch|renderiza[cç][aã]o)\\b");

    private static final String REGRAS_FOTO = ". Photorealistic high-fashion editorial "
            + "photograph of a Barbie-style fashion doll, believable proportions, blonde hair, "
            + "large blue eyes, wearing exactly the outfit and accessories described, full body "
            + "from head to toe, centered, natural pose, soft diffused studio light, 85mm lens, "
            + "shallow depth of field, realistic skin, hair and fabric texture, ultra detailed, "
            + "high resolution, clean light pink seamless backdrop. True-to-life photography "
            + "only. No text, no letters, no logos, no watermarks, no deformed hands.";

    private PromptFotorealista() {
    }

    /** Transforma o prompt curto do look em um prompt de FOTOGRAFIA realista. */
    public static String de(String promptImagem) {
        String base = promptImagem == null ? "" : promptImagem.trim();
        base = ESTILO_CONFLITANTE.matcher(base).replaceAll("photograph");
        base = base.replaceAll("[.\\s,]+$", "");

        if (base.isBlank()) {
            base = "barbie-style fashion doll, elegant pink outfit, studio backdrop";
        }
        // Mesmo quando o prompt já traz a etiqueta "photorealistic", repetimos
        // as regras completas: são elas que garantem pele/texturas reais e
        // evitam que o gerador entregue um desenho bonitinho mas irreal.
        return base + REGRAS_FOTO;
    }

    /**
     * Deixa a etiqueta fotorealista visível também no campo `promptImagem`
     * devolvido ao frontend (que é copiado/exportado pelo usuário). Idempotente:
     * pode ser chamada quantas vezes for preciso sem duplicar a etiqueta.
     */
    public static String etiquetar(String promptImagem) {
        String base = promptImagem == null ? "" : promptImagem.trim();
        base = base.replaceAll("(?i),?\\s*photorealistic studio photograph", "");
        base = base.replaceAll("[.\\s,]+$", "");
        if (base.isBlank()) {
            return "fashion doll, photorealistic studio photograph";
        }
        return base + ", photorealistic studio photograph";
    }

    /** Variação usada por "Outra imagem": mesma foto, pose e ângulo diferentes. */
    public static String comVariacao(String promptImagem, int variacao) {
        return de(promptImagem)
                + ", alternate pose and camera angle, variation " + Math.floorMod(variacao, 1001);
    }
}
