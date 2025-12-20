package com.StockLab.backend.ai;

import com.StockLab.backend.News.NaverNewsDto.NewsArticle;
import com.StockLab.backend.ai.AiDto.ForecastDirection;
import com.StockLab.backend.ai.AiDto.GptForecastRequest;
import com.StockLab.backend.ai.AiDto.GptForecastResponse;
import com.StockLab.backend.ai.AiDto.OpenAiChatMessage;
import com.StockLab.backend.ai.AiDto.OpenAiChatRequest;
import com.StockLab.backend.ai.AiDto.OpenAiChatResponse;
import com.StockLab.backend.ai.AiDto.UsedArticle;
import com.StockLab.backend.News.NaverNewsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GptForecastService {

    private final WebClient openAiWebClient;
    private final NaverNewsService naverNewsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String defaultModel;

    public GptForecastService(
            WebClient.Builder builder,
            NaverNewsService naverNewsService,
            @Value("${api.openai.base-url}") String baseUrl,
            @Value("${api.openai.key}") String apiKey,
            @Value("${api.openai.model:gpt-4o-mini}") String defaultModel
    ) {
        this.naverNewsService = naverNewsService;
        this.defaultModel = defaultModel;
        this.openAiWebClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    public GptForecastResponse forecast(GptForecastRequest request) {
        String symbol = safe(request.getSymbol());
        if (symbol.isBlank()) {
            return GptForecastResponse.builder()
                    .direction(ForecastDirection.UNCERTAIN)
                    .summary("symbol이 필요합니다.")
                    .confidence(0.0)
                    .model(defaultModel)
                    .build();
        }

        int limit = (int)clamp(request.getLimit() == null ? 5 : request.getLimit(), 1, 10);
        List<NewsArticle> articles = naverNewsService.searchNewsBySymbol(
                symbol,
                request.getDisplayName(),
                request.getQuery(),
                limit
        );

        if (articles.isEmpty()) {
            GptForecastResponse resp = GptForecastResponse.builder()
                    .direction(ForecastDirection.UNCERTAIN)
                    .summary("관련 뉴스가 부족합니다.")
                    .confidence(0.0)
                    .model(defaultModel)
                    .usedArticles(List.of())
                    .build();
            return resp;
        }

        List<NewsArticle> limited = truncate(articles, 8);
        String userPrompt = buildUserPrompt(symbol, request.getDisplayName(), limited);

        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
                .model(resolvedModel(request.getModel()))
                .messages(List.of(
                        new OpenAiChatMessage("system", systemPrompt()),
                        new OpenAiChatMessage("user", userPrompt)
                ))
                .temperature(0.35)
                .max_tokens(400)
                .build();

        try {
            OpenAiChatResponse response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(chatRequest)
                    .retrieve()
                    .bodyToMono(OpenAiChatResponse.class)
                    .block(Duration.ofSeconds(12));

            String content = extractContent(response);
            GptForecastResponse parsed = parseContent(content);
            parsed.setModel(response != null ? response.getModel() : chatRequest.getModel());
            parsed.setUsedArticles(toUsedArticles(limited));
            return parsed;
        } catch (Exception e) {
            log.error("GPT forecast 호출 실패: symbol={}", symbol, e);
            GptForecastResponse resp = GptForecastResponse.builder()
                    .direction(ForecastDirection.UNCERTAIN)
                    .summary("예측 생성에 실패했습니다.")
                    .confidence(0.0)
                    .model(chatRequest.getModel())
                    .usedArticles(toUsedArticles(limited))
                    .build();
            return resp;
        }
    }

    private String systemPrompt() {
        return """
    너는 신중한 주식 애널리스트다.
    제공된 뉴스 정보만 사용해서 단기 주가 방향성을 추론하라.

    반드시 다음 JSON 형식으로만 응답하라:
    {
    "direction": "UP | DOWN | NEUTRAL | UNCERTAIN",
    "confidence": 0~1 사이 숫자,
    "summary": "한국어로 3문장 이내 요약",
    "risks": "한국어로 2문장 이내 리스크"
    }

    뉴스가 혼재되거나 정보가 부족하면 과감하게 UNCERTAIN을 선택하라.
    과도한 확신 표현을 사용하지 마라.
    모든 텍스트는 반드시 한국어로 작성하라.
    """;
    }

    private String buildUserPrompt(String symbol, String displayName, List<NewsArticle> articles) {
        StringBuilder sb = new StringBuilder();
        sb.append("Stock: ").append(symbol);
        if (!safe(displayName).isBlank()) sb.append(" / ").append(displayName);
        sb.append("\nNews (latest first):\n");
        articles.forEach(a -> sb.append("- [")
                .append(a.getPublishedAt())
                .append("] ")
                .append(a.getTitle())
                .append(" :: ")
                .append(a.getSummary())
                .append(" (")
                .append(a.getUrl())
                .append(")\n"));
        sb.append("Return JSON only.");
        return sb.toString();
    }

    private String extractContent(OpenAiChatResponse response) {
        if (response == null || response.getChoices() == null) return "";
        return response.getChoices().stream()
                .filter(Objects::nonNull)
                .map(OpenAiChatResponse.Choice::getMessage)
                .filter(Objects::nonNull)
                .map(OpenAiChatResponse.Message::getContent)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    private GptForecastResponse parseContent(String content) {
        ForecastDirection direction = ForecastDirection.UNCERTAIN;
        double confidence = 0.0;
        String summary = content == null ? "" : content.trim();
        String risks = "";

        if (content != null && !content.isBlank() && content.trim().startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(content);
                direction = parseDirection(node.path("direction").asText());
                confidence = clamp(node.path("confidence").asDouble(0.0), 0.0, 1.0);
                summary = node.path("summary").asText(summary);
                risks = node.path("risks").asText("");
            } catch (Exception ignored) {}
        }

        return GptForecastResponse.builder()
                .direction(direction)
                .confidence(confidence)
                .summary(summary)
                .risks(risks)
                .build();
    }

    private ForecastDirection parseDirection(String v) {
        if (v == null) return ForecastDirection.UNCERTAIN;
        String s = v.trim().toUpperCase();
        try {
            return ForecastDirection.valueOf(s);
        } catch (Exception e) {
            return ForecastDirection.UNCERTAIN;
        }
    }

    private List<UsedArticle> toUsedArticles(List<NewsArticle> articles) {
        return articles.stream()
                .map(a -> UsedArticle.builder()
                        .title(safe(a.getTitle()))
                        .url(safe(a.getUrl()))
                        .publishedAt(safe(a.getPublishedAt()))
                        .build())
                .collect(Collectors.toList());
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private List<NewsArticle> truncate(List<NewsArticle> articles, int max) {
        if (articles == null || articles.size() <= max) return articles;
        return articles.subList(0, max);
    }

    private String resolvedModel(String candidate) {
        if (candidate == null || candidate.isBlank()) return defaultModel;
        return candidate.trim();
    }
}
