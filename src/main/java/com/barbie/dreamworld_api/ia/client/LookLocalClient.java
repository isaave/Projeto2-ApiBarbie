package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.ia.dto.DicasLookRequest;
import com.barbie.dreamworld_api.ia.dto.DicasLookResponse;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookResponse;
import com.barbie.dreamworld_api.model.Acessorio;
import com.barbie.dreamworld_api.model.Barbie;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fallback determinístico usado quando o Gemini está sem cota ou indisponível.
 *
 * Não tenta imitate a IA: monta uma sugestão honesta a partir dos dados reais
 * da Barbie (coleção, profissão e acessórios do acervo) e sinaliza no campo
 * {@code aviso} que o modo de demonstração foi ativado. Assim a apresentação
 * nunca fica sem resposta, e o avaliador sabe exatamente qual caminho rodou.
 */
@Component
public class LookLocalClient {

    private static final String AVISO =
            "A IA generativa estava temporariamente indisponível (cota ou conexão). "
                    + "Este look foi montado automaticamente a partir do acervo real da Barbie.";

    public SugestaoLookResponse gerarLook(Barbie barbie, String ocasiao, String preferencias) {
        String ocasaoTexto = nuloOu(ocasiao, "ocasião especial");
        String profissao = barbie.getProfissao() != null
                ? barbie.getProfissao().getNomeProfissao() : "estilista";
        List<String> cores = paleta(preferencias, ocasaoTexto);
        List<String> acessorios = acessoriosReais(barbie);

        String titulo = "Look " + primeiraMaiuscula(cortar(ocasaoTexto, 26)) + " — " + barbie.getColecao();
        String resumo = "Sugestão montada para " + barbie.getNome() + " (" + profissao + ") em "
                + ocasaoTexto + ", combinando a coleção " + barbie.getColecao()
                + " com peças coerentes com a ocasião."
                + (preferencias != null && !preferencias.isBlank()
                        ? " Preferências consideradas: " + preferencias.trim() + "." : "");

        return SugestaoLookResponse.builder()
                .barbieId(barbie.getId())
                .nomeBarbie(barbie.getNome())
                .ocasiao(ocasaoTexto)
                .tituloLook(titulo)
                .resumo(resumo)
                .paletaCores(cores)
                .itensSugeridos(itensPara(ocasaoTexto, cores))
                .acessoriosDoAcervoRecomendados(acessorios)
                .promptImagem(promptImagem(cores, ocasaoTexto))
                .dicas(List.of())
                .aviso(AVISO)
                .build();
    }

    public DicasLookResponse gerarDicas(Barbie barbie, DicasLookRequest request, String ocasiao) {
        List<String> acessorios = acessoriosReais(barbie);
        String primeiroAcessorio = acessorios.isEmpty() ? "um acessório da coleção" : acessorios.get(0);
        String paleta = request.getPaletaCores() == null || request.getPaletaCores().isEmpty()
                ? "rosa e branco" : String.join(", ", request.getPaletaCores());

        List<DicasLookResponse.Dica> dicas = new ArrayList<>();
        dicas.add(DicasLookResponse.Dica.builder()
                .categoria("Acessório")
                .titulo("Use o que ela já tem")
                .descricao("Combine " + primeiroAcessorio
                        + " com o look para criar continuidade com o acervo da Barbie.")
                .build());
        dicas.add(DicasLookResponse.Dica.builder()
                .categoria("Cor")
                .titulo("Repita a paleta")
                .descricao("Mantenha duas cores da paleta (" + paleta
                        + ") na peça principal e deixe a terceira só nos detalhes.")
                .build());
        dicas.add(DicasLookResponse.Dica.builder()
                .categoria("Caimento")
                .titulo("Ajuste a silhueta")
                .descricao("Marque levemente a cintura para valorizar o movimento em "
                        + nuloOu(ocasiao, "qualquer ocasião") + ".")
                .build());
        if (acessorios.size() > 1) {
            dicas.add(DicasLookResponse.Dica.builder()
                    .categoria("Acabamento")
                    .titulo("Um toque final do acervo")
                    .descricao("Se quiser mais brilho, acrescente " + acessorios.get(1) + ".")
                    .build());
        }

        return DicasLookResponse.builder()
                .titulo("Ajustes finais para " + nuloOu(request.getTituloLook(), "o look"))
                .resumo("Dicas práticas montadas a partir do acervo e do look já escolhido.")
                .dicas(dicas)
                .toqueFinal("Finalize com um sorriso e uma pose leve de braços abertos. 💖")
                .aviso(AVISO)
                .build();
    }

    private List<String> acessoriosReais(Barbie barbie) {
        if (barbie.getAcessorios() == null) {
            return List.of();
        }
        return barbie.getAcessorios().stream()
                .map(Acessorio::getNomeItem)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .limit(4)
                .toList();
    }

    private List<String> paleta(String preferencias, String ocasiao) {
        String texto = ((nuloOu(preferencias, "") + " " + nuloOu(ocasiao, ""))
                .toLowerCase(Locale.ROOT));
        List<String> cores = new ArrayList<>();
        if (texto.contains("pastel")) cores.add("Rosa pastel");
        else if (texto.contains("rosa") || texto.contains("pink")) cores.add("Rosa Barbie");
        if (texto.contains("azul")) cores.add("Azul céu");
        if (texto.contains("lilás") || texto.contains("roxo") || texto.contains("lavanda")) cores.add("Lilás");
        if (texto.contains("verde")) cores.add("Verde menta");
        if (texto.contains("dourado") || texto.contains("ouro")) cores.add("Dourado");
        if (texto.contains("branco")) cores.add("Branco");
        if (cores.isEmpty()) {
            cores.add("Rosa");
            cores.add("Branco");
        }
        if (cores.size() < 3) cores.add("Prateado");
        return cores.stream().distinct().limit(4).toList();
    }

    private List<SugestaoLookResponse.ItemSugerido> itensPara(String ocasiao, List<String> cores) {
        String texto = ocasiao.toLowerCase(Locale.ROOT);
        String cor = cores.isEmpty() ? "rosa" : cores.get(0).toLowerCase(Locale.ROOT);

        String vestido;
        String calcado;
        String extra;
        if (texto.contains("praia") || texto.contains("verão") || texto.contains("pool")) {
            vestido = "Vestido leve de verão";
            calcado = "Sandália rasteira";
            extra = "Chapéu de palha";
        } else if (texto.contains("esporte") || texto.contains("academia")) {
            vestido = "Conjunto esportivo";
            calcado = "Tênis branco";
            extra = "Scrunchie de cetim";
        } else if (texto.contains("trabalho") || texto.contains("escritório")
                || texto.contains("ciência") || texto.contains("reunião")) {
            vestido = "Blazer estruturado sobre blusa clara";
            calcado = "Sapato fechado de salto bloco";
            extra = "Bolsa executiva";
        } else {
            vestido = "Vestido de ocasião em " + cor;
            calcado = "Sandália coordenada com o vestido";
            extra = "Bolsa pequena";
        }

        return List.of(
                item(vestido, "Vestuário", "Peça principal, escolhida para a ocasião informada."),
                item(calcado, "Calçado", "Mantém o conforto sem quebrar a paleta do look."),
                item(extra, "Acessório", "Fecha a composição com um detalhe temático.")
        );
    }

    private SugestaoLookResponse.ItemSugerido item(String nome, String categoria, String motivo) {
        return SugestaoLookResponse.ItemSugerido.builder()
                .nome(nome)
                .categoria(categoria)
                .motivo(motivo)
                .build();
    }

    /** Tags curtas em inglês — o mesmo formato usado no campo promptImagem da IA. */
    private String promptImagem(List<String> cores, String ocasiao) {
        String cor = cores.isEmpty() ? "hot pink" : (
                cores.get(0).toLowerCase(Locale.ROOT).contains("pastel")
                        ? "light pink" : "hot pink");
        String cenario = ocasiao.toLowerCase(Locale.ROOT).contains("praia")
                ? "sunny beach" : "dreamworld garden";
        return "fashion doll, " + cor + " outfit, " + cenario
                + ", photorealistic studio photograph, full body, pink background";
    }

    private String nuloOu(String valor, String padrao) {
        return (valor == null || valor.isBlank()) ? padrao : valor.trim();
    }

    private String cortar(String valor, int tamanho) {
        return valor.length() <= tamanho ? valor : valor.substring(0, tamanho).trim() + "…";
    }

    private String primeiraMaiuscula(String valor) {
        return valor.isEmpty() ? valor
                : valor.substring(0, 1).toUpperCase(Locale.ROOT) + valor.substring(1);
    }
}
