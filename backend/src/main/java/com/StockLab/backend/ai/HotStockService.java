package com.StockLab.backend.ai;

import com.StockLab.backend.News.NaverNewsDto.NewsArticle;
import com.StockLab.backend.News.NaverNewsService;
import com.StockLab.backend.ai.AiDto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.StockLab.backend.ai.AiDto.HotStockItem;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HotStockService {

    private final NaverNewsService naverNewsService;
    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String defaultModel;

    public HotStockService(
            NaverNewsService naverNewsService,
            WebClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value("${api.openai.base-url}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${api.openai.key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${api.openai.model:gpt-4o-mini}") String defaultModel
    ) {
        this.naverNewsService = naverNewsService;
        this.defaultModel = defaultModel;
        this.openAiWebClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    private static final List<String> ISSUE_KEYWORDS = Arrays.asList(
            "실적", "AI", "인공지능", "반도체", "인수", "합병", "규제", "소송", "특허",
            "신제품", "출시", "계약", "수주", "투자", "증설", "확대"
    );

    private static final Map<String, String> STOCK_POOL = Map.of(
            "005930", "삼성전자",
            "000660", "SK하이닉스",
            "035420", "NAVER",
            "035720", "카카오",
            "051910", "LG화학",
            "006400", "삼성SDI",
            "005380", "현대차",
            "000270", "기아",
            "068270", "셀트리온",
            "AAPL", "Apple"
    );

    public List<HotStockItem> getHotStocks(int limit, List<String> symbols) {
        List<String> targetSymbols = (symbols != null && !symbols.isEmpty())
                ? symbols
                : new ArrayList<>(STOCK_POOL.keySet());

        Map<String, Double> issueScores = new HashMap<>();

        for (String symbol : targetSymbols) {
            try {
                String displayName = STOCK_POOL.getOrDefault(symbol, symbol);
                List<NewsArticle> articles = naverNewsService.searchNewsBySymbol(symbol, displayName, null, 10);

                double score = calculateIssueScore(articles);
                issueScores.put(symbol, score);
                log.info("종목: {}, 이슈 점수: {}", symbol, score);
            } catch (Exception e) {
                log.warn("종목 {} 뉴스 조회 실패: {}", symbol, e.getMessage());
            }
        }

        List<String> topSymbols = issueScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("선정된 주목 종목 TOP {}: {}", limit, topSymbols);

        List<HotStockItem> items = new ArrayList<>();
        int rank = 1;

        for (String symbol : topSymbols) {
            try {
                String displayName = STOCK_POOL.getOrDefault(symbol, symbol);
                List<NewsArticle> articles = naverNewsService.searchNewsBySymbol(symbol, displayName, null, 5);

                if (articles.isEmpty()) {
                    continue;
                }

                HotStockItem item = analyzeWithGpt(symbol, displayName, articles, rank);
                items.add(item);
                rank++;
            } catch (Exception e) {
                log.error("종목 {} GPT 분석 실패: {}", symbol, e.getMessage());
            }
        }

        return items;
    }

    private double calculateIssueScore(List<NewsArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0.0;
        }

        double score = articles.size() * 1.0;

        for (NewsArticle article : articles) {
            String text = (article.getTitle() + " " + article.getSummary()).toLowerCase();
            for (String keyword : ISSUE_KEYWORDS) {
                if (text.contains(keyword.toLowerCase())) {
                    score += 2.0;
                }
            }
        }

        return score;
    }

    private HotStockItem analyzeWithGpt(String symbol, String displayName, List<NewsArticle> articles, int rank) {
        String systemPrompt = """
                너는 신중한 주식 해설자다.
                주어진 뉴스 제목과 요약만 사용하여 다음을 한국어로 작성하라.
                
                1. 오늘 이 종목을 주목해야 하는 이유 (1문장)
                2. 단기적으로 주의할 리스크 (1문장)
                
                가격 예측, 상승/하락 판단, 매수/매도 조언은 절대 하지 마라.
                
                JSON 형식으로만 응답하라:
                {
                  "reason": "...",
                  "risk": "..."
                }
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("종목: ").append(displayName).append(" (").append(symbol).append(")\n\n");
        userPrompt.append("관련 뉴스:\n");

        for (int i = 0; i < Math.min(articles.size(), 5); i++) {
            NewsArticle article = articles.get(i);
            userPrompt.append(i + 1).append(". ")
                    .append(article.getTitle())
                    .append(" - ")
                    .append(article.getSummary())
                    .append("\n");
        }

        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
                .model(defaultModel)
                .messages(Arrays.asList(
                        new OpenAiChatMessage("system", systemPrompt),
                        new OpenAiChatMessage("user", userPrompt.toString())
                ))
                .temperature(0.3)
                .max_tokens(300)
                .build();

        try {
            OpenAiChatResponse response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(chatRequest)
                    .retrieve()
                    .bodyToMono(OpenAiChatResponse.class)
                    .block(Duration.ofSeconds(10));

            String content = extractContent(response);
            JsonNode node = objectMapper.readTree(content);

            String reason = node.path("reason").asText("이슈 분석 중");
            String risk = node.path("risk").asText("정보 부족");

            return HotStockItem.builder()
                    .symbol(symbol)
                    .displayName(displayName)
                    .reason(reason)
                    .risk(risk)
                    .rank(rank)
                    .build();

        } catch (Exception e) {
            log.error("GPT 호출 실패: {}", symbol, e);
            return HotStockItem.builder()
                    .symbol(symbol)
                    .displayName(displayName)
                    .reason("뉴스 이슈로 주목 중")
                    .risk("상세 분석 불가")
                    .rank(rank)
                    .build();
        }
    }

    private String extractContent(OpenAiChatResponse response) {
        if (response == null || response.getChoices() == null) {
            return "{}";
        }
        return response.getChoices().stream()
                .filter(Objects::nonNull)
                .map(OpenAiChatResponse.Choice::getMessage)
                .filter(Objects::nonNull)
                .map(OpenAiChatResponse.Message::getContent)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("{}");
    }
}
