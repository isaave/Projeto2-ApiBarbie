package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Desenha a Barbie do look gerado pela IA como uma ilustração vetorial
 * determinística (SVG).
 *
 * Isso substitui o gerador externo gratuito, que usava um modelo fraco e
 * produzia imagens sem sentido — além de falhar por limite de requisições.
 * Aqui todas as cores, peças e acessórios vêm do próprio look, então a imagem
 * sempre corresponde ao que foi pedido. Também não há chamada de rede,
 * logo o botão "Outra imagem" funciona sempre.
 */
@Slf4j
@Component
public class IlustracaoLookClient {

    private static final Map<String, String> CORES = new LinkedHashMap<>();
    private static final Set<String> NEUTRAS = Set.of(
            "white", "branco", "black", "preto", "silver", "prata", "grey", "cinza");

    static {
        CORES.put("hot pink", "#ff2f92");
        CORES.put("pink", "#ff69b4");
        CORES.put("rosa", "#ff69b4");
        CORES.put("rose", "#ff4081");
        CORES.put("magenta", "#e91e8c");
        CORES.put("fuchsia", "#d81b8a");
        CORES.put("lavender", "#b57edc");
        CORES.put("lilac", "#b57edc");
        CORES.put("lilás", "#b57edc");
        CORES.put("purple", "#8e44ad");
        CORES.put("roxo", "#8e44ad");
        CORES.put("violet", "#8f5bd6");
        CORES.put("blue", "#4aa3df");
        CORES.put("azul", "#4aa3df");
        CORES.put("turquoise", "#35c9bd");
        CORES.put("turquesa", "#35c9bd");
        CORES.put("yellow", "#ffd93d");
        CORES.put("amarelo", "#ffd93d");
        CORES.put("gold", "#ffd700");
        CORES.put("dourado", "#ffd700");
        CORES.put("green", "#4caf50");
        CORES.put("verde", "#4caf50");
        CORES.put("red", "#e53935");
        CORES.put("vermelho", "#e53935");
        CORES.put("orange", "#ff8c42");
        CORES.put("laranja", "#ff8c42");
        CORES.put("white", "#ffffff");
        CORES.put("branco", "#ffffff");
        CORES.put("black", "#222222");
        CORES.put("preto", "#222222");
        CORES.put("silver", "#cfd8dc");
        CORES.put("prata", "#cfd8dc");
        CORES.put("prateado", "#cfd8dc");
        CORES.put("grey", "#9e9e9e");
        CORES.put("cinza", "#9e9e9e");
        CORES.put("brown", "#8d6e63");
        CORES.put("marrom", "#8d6e63");
    }

    public ImagemGerada gerarImagem(String prompt, SugestaoLookResponse look) {
        return desenhar(prompt, look, 0);
    }

    /** Gera uma versão visual diferente do mesmo look, mudando apenas a pose e o cenário. */
    public ImagemGerada gerarNovaImagem(String prompt, int variacaoAnterior, SugestaoLookResponse look) {
        return desenhar(prompt, look, variacaoAnterior + 1);
    }

    private ImagemGerada desenhar(String prompt, SugestaoLookResponse look, int variacao) {
        String ctx = contexto(prompt, look);
        int v = Math.floorMod(variacao, 4);

        String primaria = primeiraCor(ctx, false, null);
        if (primaria == null) {
            primaria = "#ff4f9a";
        }
        String acento = primeiraCor(ctx, true, primaria);
        if (acento == null || acento.equals(primaria)) {
            acento = "#ffffff";
        }

        String cabelo = corCabelo(ctx, primaria);
        boolean oculos = contem(ctx, "sunglasses", "cat-eye", "óculos", "oculos", "glasses");
        boolean bolsa = contem(ctx, "handbag", "quilted bag", "bolsa", "bag");
        boolean glitter = contem(ctx, "glitter", "sparkle", "brilho", "strass", "lantejoula");
        boolean floral = contem(ctx, "floral", "flower", "flores", "flor");
        boolean salto = contem(ctx, "heels", "scarpin", "salto", "stiletto", "high heel");
        boolean vestidoLongo = contem(ctx, "gown", "long dress", "vestido longo", "maxi");
        boolean vestidoCurto = contem(ctx, "mini dress", "short dress", "vestido curto");

        Cenario cenario = cenario(ctx, v, primaria, acento);
        String svg = montarSvg(cenario, primaria, acento, cabelo, v,
                oculos, bolsa, glitter, floral, salto, vestidoLongo, vestidoCurto);

        log.debug("Ilustração do look gerada (variação {}, {} bytes de SVG).", v, svg.length());
        return ImagemGerada.fromSvg(svg);
    }

    private String contexto(String prompt, SugestaoLookResponse look) {
        StringBuilder sb = new StringBuilder(nulo(prompt));

        if (look != null) {
            sb.append(' ').append(nulo(look.getTituloLook()))
                    .append(' ').append(nulo(look.getResumo()))
                    .append(' ').append(nulo(look.getPromptImagem()));

            if (look.getPaletaCores() != null) {
                look.getPaletaCores().forEach(c -> sb.append(' ').append(nulo(c)));
            }
            if (look.getItensSugeridos() != null) {
                look.getItensSugeridos().forEach(i -> {
                    if (i != null) {
                        sb.append(' ').append(nulo(i.getNome()));
                    }
                });
            }
            if (look.getAcessoriosDoAcervoRecomendados() != null) {
                look.getAcessoriosDoAcervoRecomendados().forEach(a -> sb.append(' ').append(nulo(a)));
            }
        }

        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String montarSvg(Cenario cenario, String primaria, String acento, String cabelo,
                             int v, boolean oculos, boolean bolsa, boolean glitter,
                             boolean floral, boolean salto, boolean vestidoLongo, boolean vestidoCurto) {

        int hemY = vestidoLongo ? 578 : vestidoCurto ? 472 : 526;
        int hemL = vestidoLongo ? 150 : vestidoCurto ? 196 : 172;
        int hemR = 512 - hemL;
        int cintoY = 392;
        String rotate = String.format(Locale.ROOT, "%.2f", -2.0 + v * 1.35);

        StringBuilder sb = new StringBuilder(6000);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"512\" height=\"683\" viewBox=\"0 0 512 683\">\n");

        sb.append("<defs>\n")
                .append("<linearGradient id=\"bg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
                .append("<stop offset=\"0\" stop-color=\"").append(cenario.cor1()).append("\"/>\n")
                .append("<stop offset=\"1\" stop-color=\"").append(cenario.cor2()).append("\"/>\n")
                .append("</linearGradient>\n")
                .append("<linearGradient id=\"vestido\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
                .append("<stop offset=\"0\" stop-color=\"").append(claro(primaria)).append("\"/>\n")
                .append("<stop offset=\"1\" stop-color=\"").append(escuro(primaria)).append("\"/>\n")
                .append("</linearGradient>\n")
                .append("<radialGradient id=\"brilho\" cx=\"50%\" cy=\"42%\" r=\"62%\">\n")
                .append("<stop offset=\"0\" stop-color=\"#ffffff\" stop-opacity=\".72\"/>\n")
                .append("<stop offset=\"1\" stop-color=\"#ffffff\" stop-opacity=\"0\"/>\n")
                .append("</radialGradient>\n")
                .append("</defs>\n");

        sb.append("<rect width=\"512\" height=\"683\" fill=\"url(#bg)\"/>\n")
                .append("<circle cx=\"256\" cy=\"300\" r=\"230\" fill=\"url(#brilho)\"/>\n")
                .append(cenario.decor).append('\n');

        sb.append("<g transform=\"rotate(").append(rotate).append(" 256 340)\">\n");

        // Sombra no chão
        sb.append("<ellipse cx=\"256\" cy=\"646\" rx=\"94\" ry=\"16\" fill=\"#7a3559\" opacity=\".20\"/>\n");

        // Pernas
        sb.append("<rect x=\"222\" y=\"470\" width=\"24\" height=\"150\" rx=\"12\" fill=\"#ffd8bd\"/>\n")
                .append("<rect x=\"266\" y=\"470\" width=\"24\" height=\"150\" rx=\"12\" fill=\"#ffd8bd\"/>\n");

        // Sapatos
        if (salto) {
            sb.append("<path d=\"M218 614 L250 614 L256 632 L214 632 Z\" fill=\"").append(acento).append("\"/>\n")
                    .append("<rect x=\"246\" y=\"630\" width=\"6\" height=\"14\" rx=\"2\" fill=\"").append(acento).append("\"/>\n")
                    .append("<path d=\"M262 614 L294 614 L298 632 L256 632 Z\" fill=\"").append(acento).append("\"/>\n")
                    .append("<rect x=\"260\" y=\"630\" width=\"6\" height=\"14\" rx=\"2\" fill=\"").append(acento).append("\"/>\n");
        } else {
            sb.append("<ellipse cx=\"234\" cy=\"624\" rx=\"22\" ry=\"10\" fill=\"").append(acento).append("\"/>\n")
                    .append("<ellipse cx=\"278\" cy=\"624\" rx=\"22\" ry=\"10\" fill=\"").append(acento).append("\"/>\n");
        }

        // Cabelo atrás do corpo
        sb.append("<path d=\"M256 126 C188 126 164 178 170 246 C174 298 192 328 208 344 L304 344 "
                + "C320 326 338 294 342 242 C348 174 324 126 256 126 Z\" fill=\"").append(cabelo).append("\"/>\n");

        // Braços
        sb.append("<path d=\"M207 344 C184 368 171 398 166 428\" fill=\"none\" stroke=\"#ffd8bd\" "
                + "stroke-width=\"18\" stroke-linecap=\"round\"/>\n");
        sb.append("<path d=\"M305 344 C326 366 340 394 ").append(344 + (v % 2 == 0 ? 4 : -8))
                .append(" 424\" fill=\"none\" stroke=\"#ffd8bd\" stroke-width=\"18\" stroke-linecap=\"round\"/>\n");

        // Vestido
        sb.append("<path d=\"M204 330 Q256 304 308 330 L").append(hemR).append(' ').append(hemY)
                .append(" Q256 ").append(hemY + 24).append(' ').append(hemL).append(' ').append(hemY)
                .append(" Z\" fill=\"url(#vestido)\"/>\n");

        sb.append("<path d=\"M204 330 Q256 304 308 330 L300 360 Q256 372 212 360 Z\" fill=\"")
                .append(claro(acento)).append("\" opacity=\".55\"/>\n");
        sb.append("<path d=\"M256 366 L256 ").append(hemY - 12)
                .append("\" stroke=\"#ffffff\" stroke-opacity=\".30\" stroke-width=\"3\"/>\n");

        if (floral) {
            int[][] posicoes = {{226, 430}, {286, 448}, {236, 486}, {292, 500}, {256, 460}};
            for (int[] p : posicoes) {
                sb.append(flor(p[0], p[1], 8, acento, "#ffffff"));
            }
        }

        // Cinto
        sb.append("<path d=\"M208 ").append(cintoY).append(" Q256 ").append(cintoY + 16)
                .append(" 304 ").append(cintoY).append(" L308 ").append(cintoY + 20)
                .append(" Q256 ").append(cintoY + 36).append(" 204 ").append(cintoY + 20)
                .append(" Z\" fill=\"").append(acento).append("\" opacity=\".92\"/>\n");

        // Pescoço e rosto
        sb.append("<rect x=\"244\" y=\"296\" width=\"24\" height=\"48\" rx=\"10\" fill=\"#ffd8bd\"/>\n")
                .append("<circle cx=\"186\" cy=\"240\" r=\"12\" fill=\"#ffd8bd\"/>\n")
                .append("<circle cx=\"326\" cy=\"240\" r=\"12\" fill=\"#ffd8bd\"/>\n")
                .append("<ellipse cx=\"256\" cy=\"234\" rx=\"70\" ry=\"84\" fill=\"#ffdec9\"/>\n");

        // Franja / penteado — muda de lado a cada variação
        if (v % 2 == 0) {
            sb.append("<path d=\"M186 216 C188 162 226 138 272 148 C242 166 228 192 226 222 "
                    + "C210 210 196 210 186 216 Z\" fill=\"").append(cabelo).append("\"/>\n")
                    .append("<path d=\"M330 210 C336 168 310 140 272 148 C300 168 310 196 308 224 Z\" fill=\"")
                    .append(cabelo).append("\"/>\n");
        } else {
            sb.append("<path d=\"M326 216 C324 162 286 138 240 148 C270 166 284 192 286 222 "
                    + "C302 210 316 210 326 216 Z\" fill=\"").append(cabelo).append("\"/>\n")
                    .append("<path d=\"M182 210 C176 168 202 140 240 148 C212 168 202 196 204 224 Z\" fill=\"")
                    .append(cabelo).append("\"/>\n");
        }

        // Olhos ou óculos de sol
        if (oculos) {
            sb.append("<g fill=\"#2f2b45\" opacity=\".94\">\n")
                    .append("<path d=\"M200 220 Q226 210 250 222 L248 240 Q224 250 202 238 Z\"/>\n")
                    .append("<path d=\"M262 222 Q286 210 312 220 L310 238 Q288 250 264 240 Z\"/>\n")
                    .append("</g>\n")
                    .append("<path d=\"M250 226 Q256 220 262 226\" fill=\"none\" stroke=\"#2f2b45\" stroke-width=\"4\"/>\n")
                    .append("<path d=\"M200 222 L184 216 M312 222 L328 216\" stroke=\"#2f2b45\" stroke-width=\"4\" stroke-linecap=\"round\"/>\n");
        } else {
            sb.append("<ellipse cx=\"228\" cy=\"232\" rx=\"12\" ry=\"15\" fill=\"#ffffff\"/>\n")
                    .append("<ellipse cx=\"284\" cy=\"232\" rx=\"12\" ry=\"15\" fill=\"#ffffff\"/>\n")
                    .append("<circle cx=\"230\" cy=\"233\" r=\"7\" fill=\"#4aa3df\"/>\n")
                    .append("<circle cx=\"282\" cy=\"233\" r=\"7\" fill=\"#4aa3df\"/>\n")
                    .append("<circle cx=\"230\" cy=\"233\" r=\"3\" fill=\"#22223a\"/>\n")
                    .append("<circle cx=\"282\" cy=\"233\" r=\"3\" fill=\"#22223a\"/>\n")
                    .append("<path d=\"M214 212 Q228 204 242 212 M270 212 Q284 204 298 212\" fill=\"none\" stroke=\"")
                    .append(escuro(cabelo, .35)).append("\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
        }

        // Nariz e sorriso
        sb.append("<path d=\"M256 248 L253 258 L259 258\" fill=\"none\" stroke=\"#d9a07f\" "
                + "stroke-width=\"2\" stroke-linecap=\"round\"/>\n")
                .append("<path d=\"M236 270 Q256 286 276 270\" fill=\"none\" stroke=\"#c94f7c\" "
                + "stroke-width=\"4\" stroke-linecap=\"round\"/>\n");

        // Brincos
        sb.append("<circle cx=\"186\" cy=\"248\" r=\"5\" fill=\"").append(acento).append("\"/>\n")
                .append("<circle cx=\"326\" cy=\"248\" r=\"5\" fill=\"").append(acento).append("\"/>\n");

        // Bolsa
        if (bolsa) {
            int bx = 328 + (v % 2 == 0 ? 0 : -14);
            sb.append("<path d=\"M").append(bx + 14).append(" 420 Q").append(bx + 36)
                    .append(" 400 ").append(bx + 58).append(" 420\" fill=\"none\" stroke=\"")
                    .append(acento).append("\" stroke-width=\"7\" stroke-linecap=\"round\"/>\n")
                    .append("<rect x=\"").append(bx).append("\" y=\"420\" width=\"72\" height=\"58\" rx=\"14\" fill=\"")
                    .append(primaria).append("\" stroke=\"").append(claro(acento)).append("\" stroke-width=\"4\"/>\n")
                    .append("<path d=\"M").append(bx + 36).append(" 424 L").append(bx + 36)
                    .append(" 474\" stroke=\"").append(claro(acento)).append("\" stroke-width=\"3\" opacity=\".8\"/>\n");
        }

        // Brilho extra
        if (glitter) {
            sb.append(estrela(210, 392, 11, "#ffffff", ".85"))
                    .append(estrela(304, 470, 9, acento, ".9"))
                    .append(estrela(256, 508, 7, "#ffffff", ".8"));
        }

        sb.append("</g>\n");

        // Primeiro plano: estrelas que também mudam com a variação
        sb.append(estrela(64 + v * 12, 150, 16, "#ffffff", ".8"))
                .append(estrela(446 - v * 10, 250, 13, "#ffffff", ".75"))
                .append(estrela(92, 540 + v * 8, 10, acento, ".7"))
                .append("</svg>");

        return sb.toString();
    }

    private record Cenario(String cor1, String cor2, String decor) {}

    private Cenario cenario(String ctx, int v, String primaria, String acento) {
        if (contem(ctx, "garden", "jardim", "flower", "flor", "park", "parque")) {
            StringBuilder decor = new StringBuilder();
            String[] cores = {acento, "#ffd93d", primaria, "#ffffff", acento, "#8be28b"};
            for (int i = 0; i < 6; i++) {
                int x = 42 + i * 82 + (v % 2 == 0 ? 0 : 18);
                int y = 560 + (i % 3) * 32;
                decor.append(flor(x, y, 12, cores[i], "#ffdf6b"));
            }
            decor.append("<path d=\"M0 590 Q128 560 256 590 T512 590 L512 683 L0 683 Z\" fill=\"#8fd39a\" opacity=\".55\"/>");
            return new Cenario("#dff6e8", "#ffd9eb", decor.toString());
        }

        if (contem(ctx, "beach", "praia", "ocean", "mar ", "pool", "piscina")) {
            StringBuilder decor = new StringBuilder();
            decor.append("<circle cx=\"420\" cy=\"110\" r=\"54\" fill=\"#ffe27a\" opacity=\".9\"/>");
            decor.append("<path d=\"M0 520 Q128 500 256 520 T512 520 L512 683 L0 683 Z\" fill=\"#7fd6e8\" opacity=\".75\"/>");
            decor.append("<path d=\"M0 560 Q128 540 256 560 T512 560\" fill=\"none\" stroke=\"#ffffff\" stroke-width=\"8\" opacity=\".7\"/>");
            decor.append("<path d=\"M0 614 Q128 594 256 614 T512 614\" fill=\"none\" stroke=\"#ffffff\" stroke-width=\"8\" opacity=\".55\"/>");
            return new Cenario("#d9f4ff", "#ffe9bd", decor.toString());
        }

        if (contem(ctx, "festa", "party", "carnival", "carnaval", "celebra")) {
            StringBuilder decor = new StringBuilder();
            String[] cores = {primaria, "#ffd93d", acento, "#4aa3df", "#ff6b8a"};
            for (int i = 0; i < 18; i++) {
                int x = 28 + (i * 97 + v * 31) % 460;
                int y = 40 + (i * 151 + v * 17) % 560;
                decor.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
                        .append("\" r=\"").append(5 + i % 4).append("\" fill=\"")
                        .append(cores[i % cores.length]).append("\" opacity=\".75\"/>");
            }
            return new Cenario("#f5e6ff", "#ffd7e9", decor.toString());
        }

        if (contem(ctx, "gala", "noite", "night", "elegante", "soirée", "soiree")) {
            StringBuilder decor = new StringBuilder();
            for (int i = 0; i < 14; i++) {
                int x = 30 + (i * 113 + v * 23) % 460;
                int y = 40 + (i * 79 + v * 11) % 580;
                decor.append(estrela(x, y, 7 + i % 5, i % 2 == 0 ? "#ffffff" : acento, ".75"));
            }
            return new Cenario("#2d1b4e", "#7b3f78", decor.toString());
        }

        StringBuilder decor = new StringBuilder();
        decor.append(estrela(70 + v * 14, 120, 18, "#ffffff", ".85"));
        decor.append(estrela(430 - v * 12, 180, 14, acento, ".8"));
        decor.append(estrela(410, 470 + v * 10, 12, "#ffffff", ".7"));
        decor.append("<circle cx=\"82\" cy=\"400\" r=\"26\" fill=\"#ffffff\" opacity=\".35\"/>");
        decor.append("<circle cx=\"440\" cy=\"560\" r=\"34\" fill=\"#ffffff\" opacity=\".28\"/>");
        return new Cenario("#ffeaf4", "#f8c8e3", decor.toString());
    }

    private String flor(int x, int y, int r, String cor, String centro) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            double ang = 2 * Math.PI * i / 5;
            int px = x + (int) Math.round(r * Math.cos(ang));
            int py = y + (int) Math.round(r * Math.sin(ang));
            sb.append("<circle cx=\"").append(px).append("\" cy=\"").append(py)
                    .append("\" r=\"").append(r).append("\" fill=\"").append(cor)
                    .append("\" opacity=\".92\"/>");
        }
        sb.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
                .append("\" r=\"").append(r / 2).append("\" fill=\"").append(centro).append("\"/>");
        return sb.toString();
    }

    private String estrela(int x, int y, int t, String cor, String opacidade) {
        int q = Math.max(1, t / 4);
        return "<path d=\"M" + x + ' ' + (y - t)
                + " L" + (x + q) + ' ' + (y - q)
                + " L" + (x + t) + ' ' + y
                + " L" + (x + q) + ' ' + (y + q)
                + " L" + x + ' ' + (y + t)
                + " L" + (x - q) + ' ' + (y + q)
                + " L" + (x - t) + ' ' + y
                + " L" + (x - q) + ' ' + (y - q)
                + " Z\" fill=\"" + cor + "\" opacity=\"" + opacidade + "\"/>";
    }

    private String corCabelo(String ctx, String primaria) {
        if (contem(ctx, "pink hair", "cabelo rosa", "cabelo pink")) {
            return primaria;
        }
        if (contem(ctx, "brunette", "morena", "brown hair", "cabelo castanho")) {
            return "#5d3a2e";
        }
        if (contem(ctx, "red hair", "ruiva", "cabelo vermelho")) {
            return "#d95f3a";
        }
        if (contem(ctx, "silver hair", "cabelo prateado", "white hair")) {
            return "#d7dde5";
        }
        return "#f7d36b";
    }

    private String primeiraCor(String texto, boolean incluirNeutras, String excluir) {
        String melhor = null;
        int posicao = Integer.MAX_VALUE;

        for (Map.Entry<String, String> entry : CORES.entrySet()) {
            String chave = entry.getKey();
            String hex = entry.getValue();

            if (!incluirNeutras && NEUTRAS.contains(chave)) {
                continue;
            }
            if (hex.equals(excluir)) {
                continue;
            }

            int indice = texto.indexOf(chave);
            if (indice >= 0 && indice < posicao) {
                posicao = indice;
                melhor = hex;
            }
        }

        return melhor;
    }

    private boolean contem(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String nulo(String valor) {
        return valor == null ? "" : valor;
    }

    private String claro(String hex) {
        return ajustar(hex, .35);
    }

    private String escuro(String hex) {
        return escuro(hex, .28);
    }

    private String escuro(String hex, double fator) {
        return ajustar(hex, -fator);
    }

    private String ajustar(String hex, double fator) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);

        if (fator >= 0) {
            r += (255 - r) * fator;
            g += (255 - g) * fator;
            b += (255 - b) * fator;
        } else {
            r *= 1 + fator;
            g *= 1 + fator;
            b *= 1 + fator;
        }

        return String.format(Locale.ROOT, "#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
    }

    private int clamp(int valor) {
        return Math.max(0, Math.min(255, valor));
    }
}
