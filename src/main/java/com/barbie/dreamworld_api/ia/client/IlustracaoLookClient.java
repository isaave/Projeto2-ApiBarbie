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
 * determinística (SVG), com cabelo loiro volumoso, olhos grandes, batom,
 * salto alto e poses de boneca fashion.
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

        int hemY = vestidoLongo ? 584 : vestidoCurto ? 476 : 538;
        int hemL = vestidoLongo ? 146 : vestidoCurto ? 190 : 168;
        int hemR = 512 - hemL;
        String rotate = String.format(Locale.ROOT, "%.2f", -1.6 + v * 1.2);
        String pele = "#ffe0cc";
        String peleEscura = "#f3bda3";

        StringBuilder sb = new StringBuilder(9000);
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
                .append("<linearGradient id=\"cabelo\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n")
                .append("<stop offset=\"0\" stop-color=\"").append(claro(cabelo)).append("\"/>\n")
                .append("<stop offset=\"1\" stop-color=\"").append(escuro(cabelo)).append("\"/>\n")
                .append("</linearGradient>\n")
                .append("<linearGradient id=\"pele\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n")
                .append("<stop offset=\"0\" stop-color=\"").append(pele).append("\"/>\n")
                .append("<stop offset=\"1\" stop-color=\"").append(peleEscura).append("\"/>\n")
                .append("</linearGradient>\n")
                .append("<linearGradient id=\"lips\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n")
                .append("<stop offset=\"0\" stop-color=\"#ff6f9c\"/>\n")
                .append("<stop offset=\"1\" stop-color=\"#c93f6d\"/>\n")
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
        sb.append("<ellipse cx=\"256\" cy=\"650\" rx=\"96\" ry=\"15\" fill=\"#7a3559\" opacity=\".20\"/>\n");

        // Cabelo loiro volumoso atrás do corpo — traço mais reconhecível da Barbie
        sb.append("<path d=\"M256 116 C170 116 136 180 146 254 C152 314 176 372 190 412 "
                + "C206 388 228 374 256 374 C284 374 306 388 322 412 "
                + "C336 372 360 314 366 254 C376 180 342 116 256 116 Z\" fill=\"url(#cabelo)\"/>\n")
                .append("<path d=\"M182 168 C168 228 176 300 198 360\" fill=\"none\" stroke=\"#fff3c4\" "
                        + "stroke-width=\"11\" stroke-linecap=\"round\" opacity=\".42\"/>\n")
                .append("<path d=\"M330 168 C344 228 336 300 314 360\" fill=\"none\" stroke=\"#fff3c4\" "
                        + "stroke-width=\"11\" stroke-linecap=\"round\" opacity=\".42\"/>\n");

        // Pernas longas de boneca fashion
        int pernaTop = hemY - 14;
        sb.append("<path d=\"M216 ").append(pernaTop).append(" C214 552 216 598 222 628 L246 628 "
                + "C242 592 242 548 244 ").append(pernaTop).append(" Z\" fill=\"url(#pele)\"/>\n")
                .append("<path d=\"M268 ").append(pernaTop).append(" C270 552 268 598 262 628 L286 628 "
                + "C290 592 290 548 286 ").append(pernaTop).append(" Z\" fill=\"url(#pele)\"/>\n")
                .append("<ellipse cx=\"234\" cy=\"582\" rx=\"11\" ry=\"4\" fill=\"").append(peleEscura)
                .append("\" opacity=\".35\"/>\n")
                .append("<ellipse cx=\"278\" cy=\"582\" rx=\"11\" ry=\"4\" fill=\"").append(peleEscura)
                .append("\" opacity=\".35\"/>\n");

        // Salto alto — sempre presente, com ou sem strass
        sb.append("<path d=\"M214 620 Q236 608 252 622 L252 636 L212 636 Z\" fill=\"").append(acento).append("\"/>\n")
                .append("<path d=\"M260 622 Q276 608 298 620 L300 636 L260 636 Z\" fill=\"").append(acento).append("\"/>\n")
                .append("<path d=\"M220 606 Q234 600 248 606 M264 606 Q278 600 292 606\" fill=\"none\" stroke=\"")
                .append(acento).append("\" stroke-width=\"5\" stroke-linecap=\"round\"/>\n");
        if (salto) {
            sb.append("<rect x=\"246\" y=\"634\" width=\"6\" height=\"16\" rx=\"2\" fill=\"").append(acento).append("\"/>\n")
                    .append("<rect x=\"258\" y=\"634\" width=\"6\" height=\"16\" rx=\"2\" fill=\"").append(acento).append("\"/>\n");
        } else {
            sb.append("<rect x=\"242\" y=\"632\" width=\"14\" height=\"14\" rx=\"3\" fill=\"").append(acento).append("\"/>\n")
                    .append("<rect x=\"256\" y=\"632\" width=\"14\" height=\"14\" rx=\"3\" fill=\"").append(acento).append("\"/>\n");
        }

        // Braços: uma mão no quadril e a outra solta ou acenando
        sb.append("<path d=\"M206 326 C182 350 168 390 164 432\" fill=\"none\" stroke=\"url(#pele)\" "
                + "stroke-width=\"16\" stroke-linecap=\"round\"/>\n")
                .append("<circle cx=\"164\" cy=\"436\" r=\"10\" fill=\"").append(pele).append("\"/>\n");
        if (v % 2 == 0) {
            sb.append("<path d=\"M306 326 C330 346 338 380 332 408\" fill=\"none\" stroke=\"url(#pele)\" "
                    + "stroke-width=\"16\" stroke-linecap=\"round\"/>\n")
                    .append("<circle cx=\"332\" cy=\"412\" r=\"10\" fill=\"").append(pele).append("\"/>\n");
        } else {
            sb.append("<path d=\"M306 326 C332 340 348 312 354 286\" fill=\"none\" stroke=\"url(#pele)\" "
                    + "stroke-width=\"16\" stroke-linecap=\"round\"/>\n")
                    .append("<circle cx=\"356\" cy=\"282\" r=\"10\" fill=\"").append(pele).append("\"/>\n");
        }

        // Corpete com decote coração e saia em sino
        sb.append("<path d=\"M204 324 C220 342 238 332 256 346 C274 332 292 342 308 324 "
                + "L300 392 Q256 404 212 392 Z\" fill=\"url(#vestido)\"/>\n")
                .append("<path d=\"M204 324 C220 342 238 332 256 346 C274 332 292 342 308 324\" "
                + "fill=\"none\" stroke=\"").append(claro(acento)).append("\" stroke-width=\"6\" stroke-linecap=\"round\"/>\n")
                .append("<path d=\"M212 392 L").append(hemL).append(' ').append(hemY)
                .append(" Q256 ").append(hemY + 28).append(' ').append(hemR).append(' ').append(hemY)
                .append(" L300 392 Q256 404 212 392 Z\" fill=\"url(#vestido)\"/>\n");

        for (int px = 224; px <= 288; px += 16) {
            sb.append("<path d=\"M").append(px).append(" 400 L").append(px + (px < 256 ? -18 : 18)).append(' ')
                    .append(hemY + 10).append("\" stroke=\"#ffffff\" stroke-opacity=\".26\" stroke-width=\"3\" fill=\"none\"/>\n");
        }

        if (floral) {
            int[][] posicoes = {{226, 440}, {288, 458}, {238, 496}, {296, 512}, {256, 474}};
            for (int[] p : posicoes) {
                sb.append(flor(p[0], p[1], 9, acento, primaria));
            }
        }

        // Cinto e laço na cor exata do look
        sb.append("<path d=\"M212 388 Q256 402 300 388 L304 402 Q256 416 208 402 Z\" fill=\"")
                .append(primaria).append("\" opacity=\".85\"/>\n")
                .append("<circle cx=\"256\" cy=\"402\" r=\"11\" fill=\"").append(claro(acento)).append("\"/>\n")
                .append("<path d=\"M254 400 L232 390 L232 412 Z M258 400 L280 390 L280 412 Z\" fill=\"")
                .append(claro(acento)).append("\" opacity=\".9\"/>\n");

        // Pescoço e rosto
        sb.append("<rect x=\"246\" y=\"286\" width=\"20\" height=\"52\" rx=\"9\" fill=\"url(#pele)\"/>\n")
                .append("<ellipse cx=\"256\" cy=\"230\" rx=\"70\" ry=\"84\" fill=\"url(#pele)\"/>\n")
                .append("<ellipse cx=\"220\" cy=\"258\" rx=\"17\" ry=\"10\" fill=\"#ff8fb8\" opacity=\".38\"/>\n")
                .append("<ellipse cx=\"292\" cy=\"258\" rx=\"17\" ry=\"10\" fill=\"#ff8fb8\" opacity=\".38\"/>\n");

        // Olhos grandes e cílios — assinatura da boneca
        sb.append("<ellipse cx=\"228\" cy=\"230\" rx=\"14\" ry=\"17\" fill=\"#ffffff\"/>\n")
                .append("<ellipse cx=\"284\" cy=\"230\" rx=\"14\" ry=\"17\" fill=\"#ffffff\"/>\n")
                .append("<circle cx=\"230\" cy=\"232\" r=\"9\" fill=\"#5aa9e6\"/>\n")
                .append("<circle cx=\"282\" cy=\"232\" r=\"9\" fill=\"#5aa9e6\"/>\n")
                .append("<circle cx=\"230\" cy=\"232\" r=\"5\" fill=\"#232347\"/>\n")
                .append("<circle cx=\"282\" cy=\"232\" r=\"5\" fill=\"#232347\"/>\n")
                .append("<circle cx=\"233\" cy=\"228\" r=\"3\" fill=\"#ffffff\"/>\n")
                .append("<circle cx=\"285\" cy=\"228\" r=\"3\" fill=\"#ffffff\"/>\n")
                .append("<path d=\"M212 216 Q228 206 244 216 M268 216 Q284 206 300 216\" fill=\"none\" stroke=\"")
                .append(escuro(cabelo, .45)).append("\" stroke-width=\"4\" stroke-linecap=\"round\"/>\n")
                .append("<path d=\"M212 218 L204 210 M244 218 L252 210 M268 218 L260 210 M300 218 L308 210\" "
                        + "stroke=\"").append(escuro(cabelo, .45))
                .append("\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");

        if (oculos) {
            sb.append("<g fill=\"#2f2b45\" opacity=\".95\">\n")
                    .append("<path d=\"M198 216 Q226 204 252 220 L250 242 Q224 254 200 240 Z\"/>\n")
                    .append("<path d=\"M260 220 Q286 204 314 216 L312 240 Q288 254 262 242 Z\"/>\n")
                    .append("</g>\n")
                    .append("<path d=\"M250 224 Q256 218 262 224\" fill=\"none\" stroke=\"#2f2b45\" stroke-width=\"5\"/>\n")
                    .append("<path d=\"M200 220 L182 214 M312 220 L330 214\" stroke=\"")
                    .append(acento).append("\" stroke-width=\"5\" stroke-linecap=\"round\"/>\n");
        }

        // Nariz e sorriso com batom
        sb.append("<path d=\"M256 250 L252 260 L260 260\" fill=\"none\" stroke=\"#d9a07f\" "
                + "stroke-width=\"2.5\" stroke-linecap=\"round\"/>\n")
                .append("<path d=\"M238 270 Q248 264 256 270 Q264 264 274 270 Q256 288 238 270 Z\" fill=\"url(#lips)\"/>\n")
                .append("<path d=\"M244 270 Q256 276 268 270\" fill=\"none\" stroke=\"#ffffff\" "
                        + "stroke-width=\"2\" opacity=\".85\"/>\n");

        // Franja e mechas laterais — penteado muda conforme a variação
        if (v % 2 == 0) {
            sb.append("<path d=\"M186 216 C182 154 222 130 274 144 C246 160 232 186 230 218 "
                    + "C212 206 196 206 186 216 Z\" fill=\"url(#cabelo)\"/>\n")
                    .append("<path d=\"M326 212 C334 158 306 132 274 144 C300 164 312 192 308 220 Z\" fill=\"url(#cabelo)\"/>\n");
        } else {
            sb.append("<path d=\"M326 216 C330 154 290 130 238 144 C266 160 280 186 282 218 "
                    + "C300 206 316 206 326 216 Z\" fill=\"url(#cabelo)\"/>\n")
                    .append("<path d=\"M186 212 C178 158 206 132 238 144 C212 164 200 192 204 220 Z\" fill=\"url(#cabelo)\"/>\n");
        }
        sb.append("<path d=\"M188 214 C176 272 186 334 204 374 C216 342 216 280 208 226 Z\" fill=\"url(#cabelo)\"/>\n")
                .append("<path d=\"M324 214 C336 272 326 334 308 374 C296 342 296 280 304 226 Z\" fill=\"url(#cabelo)\"/>\n");

        // Brincos, colar e bolsa do acervo
        sb.append("<circle cx=\"188\" cy=\"252\" r=\"6\" fill=\"").append(acento).append("\"/>\n")
                .append("<circle cx=\"324\" cy=\"252\" r=\"6\" fill=\"").append(acento).append("\"/>\n")
                .append("<path d=\"M236 304 Q256 322 276 304\" fill=\"none\" stroke=\"")
                .append(acento).append("\" stroke-width=\"4\"/>\n")
                .append("<path d=\"M256 320 L249 328 L256 336 L263 328 Z\" fill=\"").append(acento).append("\"/>\n");

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

        if (glitter) {
            sb.append(estrela(210, 392, 11, "#ffffff", ".85"))
                    .append(estrela(304, 470, 9, acento, ".9"))
                    .append(estrela(256, 508, 7, "#ffffff", ".8"));
        }

        sb.append("</g>\n");

        // Estrelas em primeiro plano também variam com o botão "Outra imagem"
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
