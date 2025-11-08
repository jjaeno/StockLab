package com.StockLab.backend.Quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.StockLab.backend.common.BusinessException;
import com.StockLab.backend.common.ErrorCode;
import com.StockLab.backend.config.KisTokenManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 한국투자증권 해외주식 시세 조회 서비스
 * 
 * 지원 시장:
 * - 미국: 뉴욕(NYSE), 나스닥(NASDAQ), 아멕스(AMEX)
 * - 홍콩: 홍콩증권거래소(HKEX)
 * - 일본: 도쿄증권거래소(TSE)
 * - 중국: 상하이(SSE), 선전(SZSE)
 * - 베트남: 호치민(HOSE), 하노이(HNX)
 * 
 * 기능:
 * - 해외주식 현재가 조회
 * - 해외주식 캔들 차트 조회
 */
@Service
@Slf4j
public class KisOverseasQuoteService {
    
    private final WebClient webClient;
    private final KisTokenManager tokenManager;
    private final String appKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 60초 TTL 캐시
    private final Cache<String, QuoteDto.QuoteResponse> quoteCache;
    
    // TR_ID 상수
    private static final String TR_ID_OVERSEAS_PRICE = "HHDFS00000300";   // 현재가 조회
    private static final String TR_ID_OVERSEAS_CANDLE = "HHDFS76240000";  // 캔들 차트 조회

    // 거래소 코드 매핑
    private static final Map<String, String> EXCHANGE_CODES = Map.of(
        "NYSE", "NYS",      // 뉴욕증권거래소
        "NASDAQ", "NAS",    // 나스닥
        "AMEX", "AMS",      // 아멕스
        "HKEX", "HKS",      // 홍콩
        "TSE", "TSE",       // 도쿄
        "SSE", "SHS",       // 상하이
        "SZSE", "SZS",      // 선전
        "HOSE", "HSX",      // 호치민
        "HNX", "HNX"        // 하노이
    );
    
    public KisOverseasQuoteService(WebClient.Builder webClientBuilder,
                                   @Value("${api.kis.base-url}") String baseUrl,
                                   @Value("${api.kis.appkey}") String appKey,
                                   KisTokenManager tokenManager) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.appKey = appKey;
        this.tokenManager = tokenManager;
        this.quoteCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }
    
    /**
     * 해외 단일 종목 시세 조회
     * 
     * API: GET /uapi/overseas-price/v1/quotations/price
     * 
     * @param symbol 종목 심볼 (예: AAPL, TSLA, 700.HK)
     * @param exchange 거래소 코드 (예: NASDAQ, NYSE, HKEX)
     * @return 현재가, 등락률 등
     */
    public QuoteDto.QuoteResponse getOverseasQuote(String symbol, String exchange) {
        String cacheKey = exchange + ":" + symbol;
        
        // 캐시 확인
        QuoteDto.QuoteResponse cached = quoteCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("캐시 히트: {} ({})", symbol, exchange);
            return cached;
        }
        
        log.debug("해외주식 시세 조회: {} - {}", symbol, exchange);
        
        try {
            String accessToken = tokenManager.getAccessToken();
            String exchangeCode = getExchangeCode(exchange);
            
            // 심볼 정리 (홍콩은 .HK 제거)
            String cleanSymbol = cleanSymbol(symbol);
            
            // API 호출
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/overseas-price/v1/quotations/price")
                            .queryParam("AUTH", "")
                            .queryParam("EXCD", exchangeCode)     // 거래소 코드
                            .queryParam("SYMB", cleanSymbol)      // 종목 심볼
                            .build())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", tokenManager.getAccessToken())
                    .header("tr_id", TR_ID_OVERSEAS_PRICE)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            // 응답 파싱
            QuoteDto.QuoteResponse quote = parseOverseasQuoteResponse(symbol, exchange, response);
            
            // 캐시 저장
            quoteCache.put(cacheKey, quote);
            
            log.info("해외주식 시세 조회 성공: {} ({}) - ${}",
                    symbol, exchange, quote.getCurrentPrice());
            return quote;
            
        } catch (Exception e) {
            log.error("해외주식 시세 조회 실패: {} - {}", symbol, e.getMessage());
            throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                    "해외주식 시세 조회 실패: " + symbol);
        }
    }
    
    /**
     * 해외주식 캔들 데이터 조회
     * 
     * API: GET /uapi/overseas-price/v1/quotations/dailyprice
     * 
     * @param symbol 종목 심볼 (예: AAPL, TSLA)
     * @param exchange 거래소 코드 (예: NASDAQ, NYSE, HKEX)
     * @param range 조회 기간 (1D, 1W, 1M, 3M, 1Y)
     * @return 캔들 차트 데이터 (시가, 고가, 저가, 종가, 거래량)
     */
    public QuoteDto.CandleResponse getOverseasCandles(String symbol, String exchange, String range) {
        log.debug("해외주식 캔들 조회: {} ({}) - {}", symbol, exchange, range);
        
        try {
            String accessToken = tokenManager.getAccessToken();
            String exchangeCode = getExchangeCode(exchange);
            String cleanSymbol = cleanSymbol(symbol);
            
            // 조회 시작일 계산
            String startDate = calculateOverseasStartDate(range);
            
            // API 호출
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/overseas-price/v1/quotations/dailyprice")
                            .queryParam("AUTH", "")
                            .queryParam("EXCD", exchangeCode)      // 거래소 코드
                            .queryParam("SYMB", cleanSymbol)       // 종목 심볼
                            .queryParam("GUBN", "0")               // 0: 일봉
                            .queryParam("BYMD", startDate)         // 조회 시작일 (YYYYMMDD)
                            .queryParam("MODP", "1")               // 1: 수정주가 반영
                            .build())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", tokenManager.getAccessToken())
                    .header("tr_id", TR_ID_OVERSEAS_CANDLE)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            // 응답 파싱
            QuoteDto.CandleResponse candles = parseOverseasCandleResponse(symbol, range, response);
            
            log.info("해외주식 캔들 조회 성공: {} ({}) - {} 데이터",
                    symbol, exchange, candles.getTimestamps().size());
            return candles;
            
        } catch (Exception e) {
            log.error("해외주식 캔들 조회 실패: {} - {}", symbol, e.getMessage());
            throw new BusinessException(ErrorCode.API_ERROR, "해외주식 캔들 데이터 조회 실패");
        }
    }
    
    /**
     * 다중 해외 종목 시세 조회
     * 
     * @param requests 종목 리스트 (심볼 + 거래소)
     * @return 각 종목의 시세 정보
     */
    public List<QuoteDto.QuoteResponse> getOverseasQuotes(List<OverseasStockRequest> requests) {
        log.info("해외주식 다중 조회: {} 건", requests.size());
        
        return requests.stream()
                .map(req -> getOverseasQuote(req.getSymbol(), req.getExchange()))
                .collect(Collectors.toList());
    }
    
    /**
     * 미국 주요 종목 시세 조회 (편의 메서드)
     */
    public QuoteDto.QuoteResponse getUsStock(String symbol) {
        // NASDAQ인지 NYSE인지 자동 판단
        String exchange = isNasdaqStock(symbol) ? "NASDAQ" : "NYSE";
        return getOverseasQuote(symbol, exchange);
    }
    
    /**
     * 미국 주식 캔들 조회 (편의 메서드)
     */
    public QuoteDto.CandleResponse getUsCandlesForSymbol(String symbol, String range) {
        String exchange = isNasdaqStock(symbol) ? "NASDAQ" : "NYSE";
        return getOverseasCandles(symbol, exchange, range);
    }
    
    /**
     * 홍콩 주식 시세 조회 (편의 메서드)
     */
    public QuoteDto.QuoteResponse getHkStock(String symbol) {
        // .HK 제거 (예: 700.HK → 700)
        String cleanSymbol = symbol.replace(".HK", "");
        return getOverseasQuote(cleanSymbol, "HKEX");
    }
    
    /**
     * 홍콩 주식 캔들 조회 (편의 메서드)
     */
    public QuoteDto.CandleResponse getHkCandles(String symbol, String range) {
        String cleanSymbol = symbol.replace(".HK", "");
        return getOverseasCandles(cleanSymbol, "HKEX", range);
    }
    
    /**
     * 해외주식 캔들 응답 파싱
     * 
     * KIS 해외주식 캔들 응답 구조:
     * {
     *   "output2": [
     *     {
     *       "xymd": "20250115",       // 날짜 (YYYYMMDD)
     *       "clos": "180.50",         // 종가
     *       "open": "179.00",         // 시가
     *       "high": "182.00",         // 고가
     *       "low": "178.50",          // 저가
     *       "tvol": "12345678"        // 거래량
     *     },
     *     ...
     *   ]
     * }
     */
    private QuoteDto.CandleResponse parseOverseasCandleResponse(String symbol, String range, 
                                                                String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            JsonNode output2 = json.get("output2");
            
            if (output2 == null || output2.size() == 0) {
                log.warn("캔들 데이터 없음: {}", symbol);
                return QuoteDto.CandleResponse.builder()
                        .symbol(symbol)
                        .range(range)
                        .timestamps(Collections.emptyList())
                        .open(Collections.emptyList())
                        .high(Collections.emptyList())
                        .low(Collections.emptyList())
                        .close(Collections.emptyList())
                        .volume(Collections.emptyList())
                        .build();
            }
            
            List<Long> timestamps = new ArrayList<>();
            List<Double> open = new ArrayList<>();
            List<Double> high = new ArrayList<>();
            List<Double> low = new ArrayList<>();
            List<Double> close = new ArrayList<>();
            List<Long> volume = new ArrayList<>();
            
            // 데이터 파싱 (최신 데이터가 앞에 오므로 역순으로 처리)
            for (int i = output2.size() - 1; i >= 0; i--) {
                JsonNode candle = output2.get(i);
                
                // 날짜를 Unix timestamp로 변환
                String dateStr = candle.get("xymd").asText();
                timestamps.add(parseDateToTimestamp(dateStr));
                
                // OHLCV 데이터 추출
                open.add(parseDoubleOrZero(candle.get("open")));
                high.add(parseDoubleOrZero(candle.get("high")));
                low.add(parseDoubleOrZero(candle.get("low")));
                close.add(parseDoubleOrZero(candle.get("clos")));
                volume.add(parseLongOrZero(candle.get("tvol")));
            }
            
            return QuoteDto.CandleResponse.builder()
                    .symbol(symbol)
                    .range(range)
                    .timestamps(timestamps)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build();
                    
        } catch (Exception e) {
            log.error("캔들 응답 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("해외주식 캔들 데이터 파싱 실패", e);
        }
    }
    
    /**
     * 해외주식 현재가 응답 파싱
     * 
     * KIS 해외주식 응답 구조:
     * {
     *   "output": {
     *     "last": "180.50",           // 현재가 (USD)
     *     "diff": "2.30",             // 전일 대비
     *     "rate": "1.29",             // 등락률 (%)
     *     "high": "182.00",           // 고가
     *     "low": "178.50",            // 저가
     *     "open": "179.00",           // 시가
     *     "base": "178.20",           // 전일 종가
     *     "sign": "2"                 // 전일 대비 부호 (2:상승, 5:하락)
     *   }
     * }
     */
    private QuoteDto.QuoteResponse parseOverseasQuoteResponse(String symbol, String exchange,
                                                              String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            JsonNode output = json.get("output");
            
            if (output == null) {
                throw new RuntimeException("응답 데이터가 없습니다");
            }
            
            BigDecimal currentPrice = new BigDecimal(output.get("last").asText());
            BigDecimal change = new BigDecimal(output.get("diff").asText());
            BigDecimal percentChange = new BigDecimal(output.get("rate").asText());
            
            // 전일 대비 부호 확인 (5: 하락)
            String sign = output.get("sign").asText();
            if ("5".equals(sign) || "4".equals(sign)) {
                change = change.negate();
                percentChange = percentChange.negate();
            }
            
            BigDecimal previousClose = new BigDecimal(output.get("base").asText());
            
            return QuoteDto.QuoteResponse.builder()
                    .symbol(symbol)
                    .currentPrice(currentPrice)
                    .change(change)
                    .percentChange(percentChange)
                    .high(new BigDecimal(output.get("high").asText()))
                    .low(new BigDecimal(output.get("low").asText()))
                    .open(new BigDecimal(output.get("open").asText()))
                    .previousClose(previousClose)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .build();
                    
        } catch (Exception e) {
            log.error("응답 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("해외주식 데이터 파싱 실패", e);
        }
    }
    
    /**
     * 조회 시작일 계산 (해외주식용)
     */
    private String calculateOverseasStartDate(String range) {
        LocalDate startDate = switch (range) {
            case "1D" -> LocalDate.now().minusDays(1);
            case "1W" -> LocalDate.now().minusWeeks(1);
            case "1M" -> LocalDate.now().minusMonths(1);
            case "3M" -> LocalDate.now().minusMonths(3);
            case "1Y" -> LocalDate.now().minusYears(1);
            default -> LocalDate.now().minusMonths(1);
        };
        
        return startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
    
    /**
     * 날짜 문자열을 Unix timestamp로 변환
     * "20250115" → 1736899200 (초 단위)
     */
    private Long parseDateToTimestamp(String dateStr) {
        try {
            int year = Integer.parseInt(dateStr.substring(0, 4));
            int month = Integer.parseInt(dateStr.substring(4, 6));
            int day = Integer.parseInt(dateStr.substring(6, 8));
            
            return LocalDate.of(year, month, day)
                    .atStartOfDay()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toEpochSecond();
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * Double 파싱 (오류 시 0 반환)
     */
    private Double parseDoubleOrZero(JsonNode node) {
        try {
            return node == null ? 0.0 : Double.parseDouble(node.asText());
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Long 파싱 (오류 시 0 반환)
     */
    private Long parseLongOrZero(JsonNode node) {
        try {
            return node == null ? 0L : Long.parseLong(node.asText());
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * 심볼 정리 (접미사 제거)
     */
    private String cleanSymbol(String symbol) {
        return symbol.replace(".HK", "").replace(".SS", "").replace(".SZ", "");
    }
    
    /**
     * 거래소 이름 → KIS 거래소 코드 변환
     */
    private String getExchangeCode(String exchange) {
        String code = EXCHANGE_CODES.get(exchange.toUpperCase());
        if (code == null) {
            throw new IllegalArgumentException("지원하지 않는 거래소: " + exchange);
        }
        return code;
    }
    
    /**
     * NASDAQ 종목 여부 판단 (간단한 휴리스틱)
     */
    private boolean isNasdaqStock(String symbol) {
        // 주요 NASDAQ 종목들
        Set<String> nasdaqStocks = Set.of(
                "AAPL", "MSFT", "GOOGL", "GOOG", "AMZN", "NVDA", "TSLA",
                "META", "NFLX", "AMD", "INTC", "CSCO", "ADBE", "AVGO",
                "QCOM", "TXN", "COST", "CMCSA", "PEP", "HON"
        );
        return nasdaqStocks.contains(symbol.toUpperCase());
    }
    
    /**
     * 해외주식 요청 DTO
     */
    public static class OverseasStockRequest {
        private String symbol;
        private String exchange;
        
        public OverseasStockRequest(String symbol, String exchange) {
            this.symbol = symbol;
            this.exchange = exchange;
        }
        
        public String getSymbol() { return symbol; }
        public String getExchange() { return exchange; }
    }
}