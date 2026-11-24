package com.barbie.dreamworld_api.ia.service;

import com.barbie.dreamworld_api.exceptions.AiServiceException;
import com.barbie.dreamworld_api.exceptions.BusinessException;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.ia.client.GeminiClient;
import com.barbie.dreamworld_api.ia.client.GeminiImageClient;
import com.barbie.dreamworld_api.ia.client.IlustracaoLookClient;
import com.barbie.dreamworld_api.ia.client.LookLocalClient;
import com.barbie.dreamworld_api.ia.client.PollinationsImageClient;
import com.barbie.dreamworld_api.ia.client.PromptFotorealista;
import com.barbie.dreamworld_api.ia.dto.DicasLookRequest;
import com.barbie.dreamworld_api.ia.dto.DicasLookResponse;
import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import com.barbie.dreamworld_api.ia.dto.MensagemHistorico;
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
import java.util.Locale;
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
    private final IlustracaoLookClient ilustracaoLookClient;
    private final PollinationsImageClient pollinationsImageClient;
    private final LookLocalClient lookLocalClient;
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

        String prompt = montarPrompt(barbie, ocasiaoDescricao, request.getPreferencias(),
                formatarHistorico(request.getHistorico()));
        SugestaoLookResponse resposta;

        try {
            String jsonBruto = geminiClient.generateJson(prompt, buildResponseSchema(), 3072);
            resposta = parseResposta(jsonBruto, barbie, ocasiaoDescricao);
        } catch (RuntimeException ex) {
            // O tier gratuito pode esgotar a cota de texto. Em vez de derrubar a
            // demo, montamos um look coerente com os dados reais e avisamos o
            // cliente que o modo local foi usado.
            log.warn("Gemini indisponível para gerar o look ({}). Usando fallback local.", ex.getMessage());
            resposta = lookLocalClient.gerarLook(barbie, ocasiaoDescricao, request.getPreferencias());
        }

        // Usa exatamente o look que a IA acabou de gerar como base para a imagem.
        // Ordem dos provedores: Gemini (melhor qualidade) → Pollinations/FLUX
        // (foto realista sem chave) → ilustração vetorial determinística.
        String promptImagem = montarPromptImagem(barbie, ocasiaoDescricao, resposta);
        String promptFotorealista = PromptFotorealista.de(promptImagem);
        ImagemGerada imagem = null;

        try {
            imagem = geminiImageClient.gerarImagem(promptFotorealista);
        } catch (RuntimeException ex) {
            log.warn("Gemini sem imagem disponível ({}). Tentando gerador de fotos alternativo.",
                    ex.getMessage());
        }

        if (imagem == null) {
            try {
                imagem = pollinationsImageClient.gerarImagem(promptFotorealista);
                resposta.setAvisoImagem(
                        "Foto realista gerada por modelo alternativo (o gerador do Gemini estava sem cota).");
            } catch (RuntimeException ex) {
                log.warn("Gerador de fotos externo falhou ({}). Usando ilustração vetorial.",
                        ex.getMessage());
            }
        }

        if (imagem == null) {
            try {
                // Fallback determinístico: desenha a Barbie usando exatamente as
                // cores, peças e acessórios do look gerado pela IA. Não depende
                // de serviço externo nem de cota de imagem.
                imagem = ilustracaoLookClient.gerarImagem(promptImagem, resposta);
                resposta.setAvisoImagem(
                        "Ilustração estilo Barbie criada a partir do look (os geradores de foto estavam sem cota).");
            } catch (RuntimeException ex) {
                log.warn("Ilustração do look falhou: {}", ex.getMessage());
                resposta.setAvisoImagem(
                        "O look foi gerado, mas a imagem não pôde ser criada: " + ex.getMessage());
            }
        }

        if (imagem != null) {
            resposta.setImagem(SugestaoLookResponse.ImagemLook.builder()
                    .dataUrl(imagem.toDataUrl())
                    .mimeType(imagem.mimeType())
                    .base64(imagem.base64())
                    .build());
        }

        return resposta;
    }

    /**
     * Refina um look que já foi gerado. O look original é mantido: a IA apenas
     * acrescenta dicas práticas e específicas para a ocasião informada.
     */
    public DicasLookResponse gerarDicas(DicasLookRequest request) {
        if (request.getEventoId() == null
                && (request.getOcasiao() == null || request.getOcasiao().isBlank())) {
            throw new BusinessException(
                    "Informe a ocasião do look já gerado (ou o 'eventoId' usado nele).");
        }

        Barbie barbie = barbieRepository.findById(request.getBarbieId())
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", request.getBarbieId()));

        String ocasiaoDescricao = request.getEventoId() != null
                ? descreverEvento(request.getEventoId())
                : request.getOcasiao().trim();

        String prompt = montarPromptDicas(barbie, request, ocasiaoDescricao);
        try {
            String jsonBruto = geminiClient.generateJson(prompt, buildDicasSchema(), 1024);
            return parseDicas(jsonBruto);
        } catch (RuntimeException ex) {
            log.warn("Gemini indisponível para gerar dicas ({}). Usando fallback local.", ex.getMessage());
            return lookLocalClient.gerarDicas(barbie, request, ocasiaoDescricao);
        }
    }

    /** Cria uma nova versão visual do mesmo look, sem chamar o Gemini novamente. */
    public SugestaoLookResponse.ImagemLook gerarNovaImagem(DicasLookRequest request) {
        Barbie barbie = barbieRepository.findById(request.getBarbieId())
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", request.getBarbieId()));

        String ocasiaoDescricao = request.getEventoId() != null
                ? descreverEvento(request.getEventoId())
                : nuloOuTexto(request.getOcasiao());

        List<SugestaoLookResponse.ItemSugerido> itens = (request.getItensSugeridos() == null)
                ? List.of()
                : request.getItensSugeridos().stream()
                        .map(nome -> SugestaoLookResponse.ItemSugerido.builder()
                                .nome(nome)
                                .categoria("Peça do look")
                                .motivo("")
                                .build())
                        .toList();

        SugestaoLookResponse look = SugestaoLookResponse.builder()
                .tituloLook(request.getTituloLook())
                .resumo(request.getResumo())
                .paletaCores(request.getPaletaCores())
                .itensSugeridos(itens)
                .acessoriosDoAcervoRecomendados(request.getAcessoriosRecomendados())
                .promptImagem(request.getPromptImagem())
                .build();

        int variacao = request.getVariacaoImagem() == null ? 0 : request.getVariacaoImagem();
        String promptImagem = montarPromptImagem(barbie, ocasiaoDescricao, look);
        String promptFotorealista = PromptFotorealista.comVariacao(promptImagem, variacao);
        ImagemGerada imagem = null;

        try {
            imagem = geminiImageClient.gerarImagem(promptFotorealista);
        } catch (RuntimeException ex) {
            log.warn("Gemini sem imagem ao regerar ({}). Tentando gerador alternativo.", ex.getMessage());
        }

        if (imagem == null) {
            try {
                imagem = pollinationsImageClient.gerarImagem(promptFotorealista, variacao);
            } catch (RuntimeException ex) {
                log.warn("Gerador externo falhou ao regerar ({}). Usando ilustração vetorial.",
                        ex.getMessage());
            }
        }

        if (imagem == null) {
            imagem = ilustracaoLookClient.gerarNovaImagem(promptImagem, variacao, look);
        }

        return SugestaoLookResponse.ImagemLook.builder()
                .dataUrl(imagem.toDataUrl())
                .mimeType(imagem.mimeType())
                .base64(imagem.base64())
                .build();
    }

    private String montarPromptDicas(Barbie barbie, DicasLookRequest request, String ocasiao) {
        return """
                Você é a estilista pessoal do universo Barbie Dreamworld.
                Um look JÁ FOI gerado para a Barbie abaixo. Não crie um look novo.
                Sua tarefa é dar dicas APRIMORADAS, práticas e específicas para melhorar
                esse look na ocasião informada, sem contrariar a proposta original.

                Dados reais da Barbie (vindos do banco de dados):
                - Nome: %s
                - Coleção: %s
                - Profissão: %s
                - Acessórios que ela possui: %s

                Ocasião: %s
                Preferências do usuário: %s
                Foco extra pedido pelo usuário: %s
                %s

                Look já gerado (base das dicas):
                - Título: %s
                - Resumo: %s
                - Paleta: %s
                - Itens: %s
                - Acessórios recomendados do acervo: %s

                Regras obrigatórias:
                - Responda sempre em português do Brasil.
                - Considere o histórico recente da conversa para não repetir dicas já dadas;
                  ele é um contexto limitado, não uma transcrição a ser copiada.
                - Devolva entre 3 e 5 dicas em "dicas", cada uma com categoria, título curto
                  e descrição objetiva (máximo 2 frases).
                - As dicas devem complementar o look: caimento, combinação de cores, calçado,
                  acessórios, cabelo ou adequação à ocasião. Não repita o resumo original.
                - Cite APENAS acessórios que apareçam na lista de acessórios que ela possui.
                  Não invente marcas reais e não sugira conteúdo ofensivo ou sexual.
                - Em "toqueFinal", dê uma sugestão curta de atitude ou pose para fechar o look.
                """.formatted(
                barbie.getNome(),
                barbie.getColecao(),
                barbie.getProfissao() != null ? barbie.getProfissao().getNomeProfissao() : "não informada",
                (barbie.getAcessorios() == null || barbie.getAcessorios().isEmpty())
                        ? "nenhum cadastrado"
                        : barbie.getAcessorios().stream().map(this::descreverAcessorio).collect(Collectors.joining("; ")),
                ocasiao,
                nuloOuTexto(request.getPreferencias()),
                nuloOuTexto(request.getFoco()),
                formatarHistorico(request.getHistorico()),
                request.getTituloLook(),
                nuloOuTexto(request.getResumo()),
                request.getPaletaCores() == null || request.getPaletaCores().isEmpty()
                        ? "não informada" : String.join(", ", request.getPaletaCores()),
                request.getItensSugeridos() == null || request.getItensSugeridos().isEmpty()
                        ? "não informados" : String.join(", ", request.getItensSugeridos()),
                request.getAcessoriosRecomendados() == null || request.getAcessoriosRecomendados().isEmpty()
                        ? "nenhum" : String.join(", ", request.getAcessoriosRecomendados())
        );
    }

    /**
     * Contexto de conversa com tamanho controlado: no máximo 6 mensagens e
     * 300 caracteres por mensagem, mesmo que o cliente envie mais (defesa
     * em profundidade além das validações do DTO).
     */
    private String formatarHistorico(List<MensagemHistorico> historico) {
        if (historico == null || historico.isEmpty()) {
            return "Histórico recente da conversa: nenhum.";
        }

        List<MensagemHistorico> recentes = historico.stream()
                .filter(m -> m != null && m.getTexto() != null && !m.getTexto().isBlank())
                .limit(6)
                .toList();

        if (recentes.isEmpty()) {
            return "Histórico recente da conversa: nenhum.";
        }

        String linhas = recentes.stream()
                .map(m -> "- " + ("ia".equals(m.getPapel()) ? "Estilista" : "Usuário")
                        + ": " + cortar(m.getTexto().trim(), 300))
                .collect(Collectors.joining("\n"));

        return "Histórico recente da conversa (contexto limitado, não reproduzir):\n" + linhas;
    }

    private String cortar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite) + "…";
    }

    private String nuloOuTexto(String valor) {
        return (valor == null || valor.isBlank()) ? "nenhum" : valor.trim();
    }

    private Map<String, Object> buildDicaItemSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "categoria", Map.of("type", "STRING"),
                        "titulo", Map.of("type", "STRING"),
                        "descricao", Map.of("type", "STRING")
                ),
                "required", List.of("categoria", "titulo", "descricao")
        );
    }

    private Map<String, Object> buildDicasSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "titulo", Map.of("type", "STRING"),
                        "resumo", Map.of("type", "STRING"),
                        "dicas", Map.of("type", "ARRAY", "items", buildDicaItemSchema(), "maxItems", 5),
                        "toqueFinal", Map.of("type", "STRING")
                ),
                "required", List.of("titulo", "resumo", "dicas", "toqueFinal")
        );
    }

    private DicasLookResponse parseDicas(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            return DicasLookResponse.builder()
                    .titulo(textoOuPadrao(root, "titulo", "Dicas aprimoradas"))
                    .resumo(textoOuPadrao(root, "resumo", ""))
                    .dicas(listaDeDicas(root))
                    .toqueFinal(textoOuPadrao(root, "toqueFinal", ""))
                    .build();

        } catch (Exception e) {
            log.error("Não foi possível interpretar as dicas retornadas pela IA: {}", json, e);
            throw new AiServiceException(
                    "A IA retornou dicas em formato inesperado. Tente novamente em instantes.",
                    HttpStatus.BAD_GATEWAY, e);
        }
    }

    /**
     * Transforma a resposta estruturada do assistente em um prompt de imagem.
     * Todos os elementos visuais vêm do look gerado pela própria IA — nada é
     * inventado aqui — para garantir que a imagem corresponda ao texto.
     */
    private String montarPromptImagem(Barbie barbie, String ocasiao, SugestaoLookResponse look) {
        // A própria IA de texto descreve o visual em inglês, curto e objetivo.
        // Isso evita que o gerador de imagem ignore detalhes de um prompt longo
        // e garante que a imagem corresponda ao look que foi pedido.
        if (look.getPromptImagem() != null && !look.getPromptImagem().isBlank()) {
            return look.getPromptImagem().trim().replaceAll("[.\\s]+$", "")
                    + ", photorealistic studio photograph, full body, centered, "
                    + "no text, no logos, no watermarks";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Photorealistic high-fashion editorial photograph of a Barbie-style fashion doll ")
                .append("wearing the following outfit. ")
                .append("Full body, head to toe, centered, clean plain light pink studio background, ")
                .append("ultra detailed realistic skin, hair and fabric textures, cheerful Dreamworld ")
                .append("aesthetic. Do not include any text, words, logos or watermarks in the image.\n\n")
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

    private String montarPrompt(Barbie barbie, String ocasiao, String preferencias, String historico) {
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
                %s

                Regras obrigatórias:
                - Responda sempre em português do Brasil.
                - Use o histórico recente da conversa apenas para entender ajustes pedidos
                  (ex.: "mais discretas"); nunca repita mensagens antigas no resultado.
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
                - No campo "promptImagem", escreva EM INGLÊS no máximo 25 palavras, em ETIQUETAS
                  curtas separadas por vírgula, sem frases e sem verbos. Exemplo:
                  "fashion doll, pink floral midi dress, white cat-eye sunglasses, quilted pink handbag, sunny flower garden, photorealistic studio photograph, full body, pink background".
                  Inclua só peças, cores por nome (hot pink, white, lavender), acessórios, cenário
                  e SEMPRE a etiqueta "photorealistic studio photograph" — a imagem deve ser
                  uma FOTO realista, nunca ilustração, cartoon ou render 3D.
                  Nunca use a palavra "Barbie", marcas, nem frases como "no text" ou "no logo".
                - No campo "dicas", inclua de 3 a 4 dicas APRIMORADAS para refinar esse look
                  na mesma ocasião (caimento, cores, calçado, cabelo ou acessórios), cada uma
                  com "categoria", "titulo" e "descricao" curta. Não repita o resumo.
                """.formatted(
                barbie.getNome(),
                barbie.getColecao(),
                barbie.getProfissao() != null ? barbie.getProfissao().getNomeProfissao() : "não informada",
                barbie.getDreamhouse() != null ? barbie.getDreamhouse().getNomeCasa() : "não informada",
                acessorios,
                ocasiao,
                (preferencias == null || preferencias.isBlank()) ? "nenhuma" : preferencias.trim(),
                historico
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
                "paletaCores", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "maxItems", 4),
                "itensSugeridos", Map.of("type", "ARRAY", "items", itemSchema, "maxItems", 5),
                "acessoriosDoAcervoRecomendados",
                Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "maxItems", 4),
                "promptImagem", Map.of("type", "STRING"),
                "dicas", Map.of("type", "ARRAY", "items", buildDicaItemSchema(), "maxItems", 4)
        );

        return Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", List.of("tituloLook", "resumo", "itensSugeridos", "promptImagem", "dicas")
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
                    .acessoriosDoAcervoRecomendados(
                            filtrarAcessoriosReais(listaDeTextos(root, "acessoriosDoAcervoRecomendados"), barbie))
                    .promptImagem(textoOuPadrao(root, "promptImagem", ""))
                    .dicas(listaDeDicas(root))
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

    /**
     * Defesa em profundidade contra alucinação: a IA pode sugerir apenas
     * acessórios que realmente existem no acervo da Barbie. A verificação é
     * feita também no servidor, não apenas no prompt.
     */
    private List<String> filtrarAcessoriosReais(List<String> sugeridos, Barbie barbie) {
        if (sugeridos == null || sugeridos.isEmpty() || barbie.getAcessorios() == null) {
            return List.of();
        }

        List<String> possuidos = barbie.getAcessorios().stream()
                .map(Acessorio::getNomeItem)
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.trim().toLowerCase(Locale.ROOT))
                .toList();

        return sugeridos.stream()
                .filter(s -> s != null && !s.isBlank())
                .filter(s -> {
                    String nome = s.trim().toLowerCase(Locale.ROOT);
                    return possuidos.stream().anyMatch(p ->
                            nome.equals(p) || nome.contains(p) || p.contains(nome));
                })
                .distinct()
                .limit(4)
                .toList();
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

    private List<DicasLookResponse.Dica> listaDeDicas(JsonNode root) {
        JsonNode node = root.get("dicas");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DicasLookResponse.Dica> dicas = new ArrayList<>();
        node.forEach(n -> dicas.add(DicasLookResponse.Dica.builder()
                .categoria(textoOuPadrao(n, "categoria", "Dica"))
                .titulo(textoOuPadrao(n, "titulo", "Refinamento"))
                .descricao(textoOuPadrao(n, "descricao", ""))
                .build()));
        return dicas;
    }
}
