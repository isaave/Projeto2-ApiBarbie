package com.barbie.dreamworld_api.ia.service;

import com.barbie.dreamworld_api.exceptions.AiServiceException;
import com.barbie.dreamworld_api.exceptions.BusinessException;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.ia.client.GeminiClient;
import com.barbie.dreamworld_api.ia.client.GeminiImageClient;
import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookRequest;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookResponse;
import com.barbie.dreamworld_api.model.Acessorio;
import com.barbie.dreamworld_api.model.Barbie;
import com.barbie.dreamworld_api.model.Evento;
import com.barbie.dreamworld_api.repository.BarbieRepository;
import com.barbie.dreamworld_api.repository.EventoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistenteEstiloService {

    private final BarbieRepository barbieRepository;
    private final EventoRepository eventoRepository;
    private final GeminiClient geminiClient;
    private final GeminiImageClient geminiImageClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Não é @Transactional de propósito: a API tem Open Session In View
     * habilitado (padrão do Spring Boot), então os relacionamentos lazy de
     * Barbie (acessórios, profissão, dreamhouse) continuam acessíveis
     * durante toda a requisição HTTP sem precisar manter uma transação de
     * banco aberta enquanto aguardamos a resposta (potencialmente lenta) do
     * Gemini — o que seria um desperdício de conexão do pool.
     */
    public SugestaoLookResponse gerarSugestao(SugestaoLookRequest request) {

        boolean temEvento = request.getEventoId() != null;
        boolean temOcasiaoLivre = request.getOcasiao() != null && !request.getOcasiao().isBlank();

        if (!temEvento && !temOcasiaoLivre) {
            throw new BusinessException(
                    "Informe o 'eventoId' de um evento já cadastrado ou descreva a ocasião em 'ocasiao'.");
        }

        Barbie barbie = barbieRepository.findById(request.getBarbieId())
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", request.getBarbieId()));

        String ocasiaoDescricao = temEvento
                ? descreverEvento(request.getEventoId())
                : request.getOcasiao().trim();

        String prompt = montarPrompt(barbie, ocasiaoDescricao, request.getPreferencias());
        String jsonBruto = geminiClient.generateJson(prompt, buildResponseSchema());

        SugestaoLookResponse resposta = parseResposta(jsonBruto, barbie, ocasiaoDescricao);

        // Usa exatamente o look que a IA acabou de gerar como base para a imagem.
        // Se o provedor de imagens estiver sem quota, ainda devolvemos o look em
        // texto com um aviso, em vez de descartar toda a resposta da IA.
        try {
            String promptImagem = montarPromptImagem(barbie, ocasiaoDescricao, resposta);
            ImagemGerada imagem = geminiImageClient.gerarImagem(promptImagem);
            resposta.setImagem(SugestaoLookResponse.ImagemLook.builder()
                    .dataUrl(imagem.toDataUrl())
                    .mimeType(imagem.mimeType())
                    .base64(imagem.base64())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Não foi possível gerar a imagem do look (look em texto mantido): {}", ex.getMessage());
            resposta.setAvisoImagem("O look foi gerado, mas a imagem não pôde ser criada: " + ex.getMessage());
        }

        return resposta;
    }

    /**
     * Transforma a resposta estruturada do assistente em um prompt de imagem.
     * Todos os elementos visuais vêm do look gerado pela própria IA — nada é
     * inventado aqui — para garantir que a imagem corresponda ao texto.
     */
    private String montarPromptImagem(Barbie barbie, String ocasiao, SugestaoLookResponse look) {
        StringBuilder sb = new StringBuilder();
        sb.append("Illustration of the classic Barbie fashion doll wearing the following outfit. ")
                .append("Full body, head to toe, centered, clean plain light pink studio background, ")
                .append("high detail fashion illustration, cheerful Dreamworld aesthetic. ")
                .append("Do not include any text, words, logos or watermarks in the image.\n\n")
                .append("Doll: ").append(barbie.getNome())
                .append(" (collection: ").append(barbie.getColecao()).append(")");

        if (barbie.getProfissao() != null) {
            sb.append(", profession: ").append(barbie.getProfissao().getNomeProfissao());
        }
        if (barbie.getDreamhouse() != null) {
            sb.append(", dreamhouse: ").append(barbie.getDreamhouse().getNomeCasa());
        }

        sb.append("\nOccasion: ").append(ocasiao)
                .append("\nOutfit title: ").append(look.getTituloLook())
                .append("\nOutfit description: ").append(look.getResumo());

        if (look.getPaletaCores() != null && !look.getPaletaCores().isEmpty()) {
            sb.append("\nColor palette: ").append(String.join(", ", look.getPaletaCores()));
        }
        if (look.getItensSugeridos() != null && !look.getItensSugeridos().isEmpty()) {
            sb.append("\nClothing and accessories to show on the doll: ");
            sb.append(look.getItensSugeridos().stream()
                    .map(i -> i.getNome() + " (" + i.getCategoria() + ")")
                    .collect(Collectors.joining("; ")));
        }
        if (look.getAcessoriosDoAcervoRecomendados() != null
                && !look.getAcessoriosDoAcervoRecomendados().isEmpty()) {
            sb.append("\nAlso include these accessories from her collection: ")
                    .append(String.join(", ", look.getAcessoriosDoAcervoRecomendados()));
        }

        return sb.toString();
    }

    private String descreverEvento(Long eventoId) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento", eventoId));

        return "Evento cadastrado \"%s\"%s, em %s%s".formatted(
                evento.getNomeEvento(),
                (evento.getDescricao() != null && !evento.getDescricao().isBlank())
                        ? " (" + evento.getDescricao() + ")" : "",
                (evento.getLocal() != null && !evento.getLocal().isBlank())
                        ? evento.getLocal() : "local não informado",
                evento.getDataEvento() != null ? ", na data " + evento.getDataEvento() : "");
    }

    private String montarPrompt(Barbie barbie, String ocasiao, String preferencias) {
        String acessorios = barbie.getAcessorios() == null || barbie.getAcessorios().isEmpty()
                ? "nenhum acessório cadastrado ainda"
                : barbie.getAcessorios().stream()
                        .map(this::descreverAcessorio)
                        .collect(Collectors.joining("; "));

        return """
                Você é um estilista pessoal virtual do universo Barbie Dreamworld.
                Sua tarefa é gerar UMA sugestão de look para a Barbie descrita abaixo,
                adequada à ocasião informada.

                Dados reais da Barbie (vindos do banco de dados da aplicação):
                - Nome: %s
                - Coleção: %s
                - Profissão: %s
                - Dreamhouse: %s
                - Acessórios que ela já possui: %s

                Ocasião: %s
                Preferências adicionais informadas pelo usuário: %s

                Regras obrigatórias:
                - Responda sempre em português do Brasil.
                - No campo "acessoriosDoAcervoRecomendados", cite APENAS nomes que apareçam,
                  exatamente como escritos, na lista de acessórios que ela já possui. Se nenhum
                  combinar bem, devolva uma lista vazia.
                - Em "itensSugeridos" você pode sugerir peças novas que ela ainda não possui,
                  mas nunca cite marcas reais — use descrições genéricas
                  (ex: "sandália prateada com salto bloco").
                - Não fuja do tema Barbie Dreamworld e não inclua conteúdo ofensivo, sexual,
                  político ou fora do universo do produto.
                - Seja objetivo: no máximo 5 itens em "itensSugeridos" e no máximo 4 cores em
                  "paletaCores".
                """.formatted(
                barbie.getNome(),
                barbie.getColecao(),
                barbie.getProfissao() != null ? barbie.getProfissao().getNomeProfissao() : "não informada",
                barbie.getDreamhouse() != null ? barbie.getDreamhouse().getNomeCasa() : "não informada",
                acessorios,
                ocasiao,
                (preferencias == null || preferencias.isBlank()) ? "nenhuma" : preferencias.trim()
        );
    }

    private String descreverAcessorio(Acessorio a) {
        StringBuilder sb = new StringBuilder(a.getNomeItem());
        if (a.getCategoria() != null && !a.getCategoria().isBlank()) {
            sb.append(" (").append(a.getCategoria());
            if (a.getRaridade() != null) {
                sb.append(", raridade ").append(a.getRaridade());
            }
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * Schema no formato aceito pelo campo generationConfig.responseSchema do
     * Gemini — força o modelo a devolver JSON já no shape esperado, evitando
     * a necessidade de extrair JSON "escondido" em texto livre ou markdown.
     */
    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> itemSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "nome", Map.of("type", "STRING"),
                        "categoria", Map.of("type", "STRING"),
                        "motivo", Map.of("type", "STRING")
                ),
                "required", List.of("nome", "categoria", "motivo")
        );

        Map<String, Object> properties = Map.of(
                "tituloLook", Map.of("type", "STRING"),
                "resumo", Map.of("type", "STRING"),
                "paletaCores", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                "itensSugeridos", Map.of("type", "ARRAY", "items", itemSchema),
                "acessoriosDoAcervoRecomendados", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))
        );

        return Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", List.of("tituloLook", "resumo", "itensSugeridos")
        );
    }

    private SugestaoLookResponse parseResposta(String json, Barbie barbie, String ocasiao) {
        try {
            JsonNode root = objectMapper.readTree(json);

            return SugestaoLookResponse.builder()
                    .barbieId(barbie.getId())
                    .nomeBarbie(barbie.getNome())
                    .ocasiao(ocasiao)
                    .tituloLook(textoOuPadrao(root, "tituloLook", "Look sugerido"))
                    .resumo(textoOuPadrao(root, "resumo", ""))
                    .paletaCores(listaDeTextos(root, "paletaCores"))
                    .itensSugeridos(listaDeItens(root))
                    .acessoriosDoAcervoRecomendados(listaDeTextos(root, "acessoriosDoAcervoRecomendados"))
                    .build();

        } catch (Exception e) {
            log.error("Não foi possível interpretar o JSON retornado pela IA: {}", json, e);
            throw new AiServiceException(
                    "A IA retornou uma resposta em formato inesperado. Tente novamente em instantes.",
                    HttpStatus.BAD_GATEWAY, e);
        }
    }

    private String textoOuPadrao(JsonNode root, String campo, String padrao) {
        JsonNode node = root.get(campo);
        return (node != null && !node.isNull()) ? node.asText(padrao) : padrao;
    }

    private List<String> listaDeTextos(JsonNode root, String campo) {
        JsonNode node = root.get(campo);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(node.spliterator(), false)
                .map(n -> n.asText(""))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<SugestaoLookResponse.ItemSugerido> listaDeItens(JsonNode root) {
        JsonNode node = root.get("itensSugeridos");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<SugestaoLookResponse.ItemSugerido> itens = new ArrayList<>();
        node.forEach(n -> itens.add(SugestaoLookResponse.ItemSugerido.builder()
                .nome(textoOuPadrao(n, "nome", "Item sem nome"))
                .categoria(textoOuPadrao(n, "categoria", "Categoria não informada"))
                .motivo(textoOuPadrao(n, "motivo", ""))
                .build()));
        return itens;
    }
}
