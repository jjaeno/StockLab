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
import java.time.Duration;
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
    private final String appSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 60초 TTL 캐시
    private final Cache<String, QuoteDto.QuoteResponse> quoteCache;
    
    // TR_ID 상수
    private static final String TR_ID_OVERSEAS_PRICE = "HHDFS00000300";   // 해외주식 현재 체결가
    private static final String TR_ID_OVERSEAS_CANDLE = "HHDFS76240000";  // 캔들 차트 조회

    // 거래소 코드 매핑
    private static final Map<String, String> EXCHANGE_CODES = Map.of(
        "NYSE", "NYS",       // 뉴욕증권거래소
        "NASDAQ", "NAS",    // 나스닥
        "AMEX", "AMS"       // 아멕스
    );
    
    public KisOverseasQuoteService(WebClient.Builder webClientBuilder,
                                   @Value("${api.kis.base-url}") String baseUrl,
                                   @Value("${api.kis.appkey}") String appKey,
                                   @Value("${api.kis.appsecret}") String appSecret,
                                   KisTokenManager tokenManager) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.appKey = appKey;
        this.tokenManager = tokenManager;
        this.appSecret = appSecret;
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
     * @param symbol 종목 심볼 (예: AAPL, TSLA)
     * @param exchange 거래소 코드 (예: NASDAQ, NYSE)
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
            QuoteDto.QuoteResponse quote = requestOverseasQuoteInternal(symbol, exchange);
            quoteCache.put(cacheKey, quote);
            log.info("해외주식 시세 조회 성공: {} ({}) - ${}",
                    symbol, exchange, quote.getCurrentPrice());
            return quote;
            
        } catch (Exception primaryException) {
            if (!"NASDAQ".equalsIgnoreCase(exchange)) {
                log.error("해외주식 시세 조회 실패: {} - {}", symbol, primaryException.getMessage());
                throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                        "해외주식 시세 조회 실패: " + symbol);
            }

            log.warn("NASDAQ 조회 실패, NYSE로 재시도: {}", symbol);
            try {
                QuoteDto.QuoteResponse quote = requestOverseasQuoteInternal(symbol, "NYSE");
                quoteCache.put(cacheKey, quote);
                quoteCache.put("NYSE:" + symbol, quote);
                log.info("해외주식 시세 조회 성공(대체 거래소): {} ({}) - ${}",
                        symbol, "NYSE", quote.getCurrentPrice());
                return quote;
            } catch (Exception fallbackException) {
                log.error("해외주식 시세 조회 실패(재시도 포함): {} - {}", symbol, fallbackException.getMessage());
                throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                        "해외주식 시세 조회 실패: " + symbol);
            }
        }
    }

    private QuoteDto.QuoteResponse requestOverseasQuoteInternal(String symbol, String exchange) {
        String accessToken = tokenManager.getAccessToken();
        String exchangeCode = getExchangeCode(exchange);

        String cleanSymbol = cleanSymbol(symbol);

        String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/price")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", exchangeCode)
                        .queryParam("SYMB", cleanSymbol)
                        .build())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", TR_ID_OVERSEAS_PRICE)
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(3)); //3초 타임아웃

        return parseOverseasQuoteResponse(symbol, exchange, response);
    }
    
    /**
     * 해외주식 캔들 데이터 조회
     * 
     * API: GET /uapi/overseas-price/v1/quotations/dailyprice
     * 
     * @param symbol 종목 심볼 (예: AAPL, TSLA)
     * @param exchange 거래소 코드 (예: NASDAQ, NYSE)
     * @param range 조회 기간 (1D, 1W, 1M, 3M, 1Y)
     * @return 캔들 차트 데이터 (시가, 고가, 저가, 종가, 거래량)
     */
    public QuoteDto.CandleResponse getOverseasCandles(String symbol, String exchange, String range) {
        log.debug("해외주식 캔들 조회: {} ({}) - {}", symbol, exchange, range);
        
        try {
            String accessToken = tokenManager.getAccessToken();
            String exchangeCode = getExchangeCode(exchange);
            String cleanSymbol = cleanSymbol(symbol);

            //기간 구분 코드 매핑 (국내와 동일)
            String gubn;
            switch (range.toUpperCase()) {
                case "1Y" -> gubn = "1";  // 1년은 주봉
                default -> gubn = "0";    // 나머지는 일봉
            }
            // 조회 시작일 계산
            String startDate = calculateOverseasStartDate(range);
            
            // API 호출
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/overseas-price/v1/quotations/dailyprice")
                            .queryParam("AUTH", "")
                            .queryParam("EXCD", exchangeCode)      // 거래소 코드
                            .queryParam("SYMB", cleanSymbol)       // 종목 심볼
                            .queryParam("GUBN", gubn) // 0: 일봉, 1: 주봉
                            .queryParam("BYMD", startDate)         // 조회 시작일 (YYYYMMDD)
                            .queryParam("MODP", "1")               // 1: 수정주가 반영
                            .build())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", TR_ID_OVERSEAS_CANDLE)
                    .header("custtype", "P")
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
                .map(req -> {
                    try {
                        return getOverseasQuote(req.getSymbol(), req.getExchange());
                    } catch (Exception e) {
                        log.warn("해외주식 조회 스킵: {}", req.getSymbol());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
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
     * 해외주식 캔들 응답 파싱
     *
     * KIS 해외주식 캔들 응답 구조:
     * {
     *   "output2": [
     *     { "xymd": "20250115", "clos": "180.50", "open": "179.00",
     *       "high": "182.00", "low": "178.50", "tvol": "12345678" },
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

            //전체 데이터를 "오래된 순 → 최신 순"으로 쌓기
            List<Long> allTimestamps = new ArrayList<>();
            List<Double> allOpen = new ArrayList<>();
            List<Double> allHigh = new ArrayList<>();
            List<Double> allLow = new ArrayList<>();
            List<Double> allClose = new ArrayList<>();
            List<Long> allVolume = new ArrayList<>();

            //KIS는 최신이 앞에 오기 때문에 역순으로 돌려서 오래된 것부터 쌓음
            for (int i = output2.size() - 1; i >= 0; i--) {
                JsonNode candle = output2.get(i);

                String dateStr = candle.get("xymd").asText();
                allTimestamps.add(parseDateToTimestamp(dateStr));

                allOpen.add(parseDoubleOrZero(candle.get("open")));
                allHigh.add(parseDoubleOrZero(candle.get("high")));
                allLow.add(parseDoubleOrZero(candle.get("low")));
                allClose.add(parseDoubleOrZero(candle.get("clos")));
                allVolume.add(parseLongOrZero(candle.get("tvol")));
            }

            //range에 따라 "뒤에서 몇 개만" 자를지 결정
            int maxCount = switch (range.toUpperCase()) {
                case "1D" -> 1;    // 최근 1개
                case "1W" -> 7;    // 최근 7개 (영업일 감안하면 실제로는 5개 정도 들어옴)
                case "1M" -> 22;   // 대략 한 달 영업일
                case "3M" -> 66;   // 3개월
                case "1Y" -> 52;  // 주봉 기준 52주
                default -> allTimestamps.size();
            };

            int size = allTimestamps.size();
            int fromIndex = Math.max(0, size - maxCount);

            List<Long> timestamps = new ArrayList<>(allTimestamps.subList(fromIndex, size));
            List<Double> open = new ArrayList<>(allOpen.subList(fromIndex, size));
            List<Double> high = new ArrayList<>(allHigh.subList(fromIndex, size));
            List<Double> low = new ArrayList<>(allLow.subList(fromIndex, size));
            List<Double> close = new ArrayList<>(allClose.subList(fromIndex, size));
            List<Long> volume = new ArrayList<>(allVolume.subList(fromIndex, size));

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
     *   "rt_cd": "0",
     *   "msg_cd": "...",
     *   "msg1": "...",
     *   "output": {
     *     "rsym": "DNASAAPL",
     *     "zdiv": "2",
     *     "base": "178.20",   // 전일 종가
     *     "pvol": "12345678", // 전일 거래량
     *     "last": "180.50",   // 현재가
     *     "sign": "2",        // 2:상승, 5:하락
     *     "diff": "2.30",     // 전일 대비
     *     "rate": "1.29",     // 등락률
     *     "tvol": "6543210",  // 당일 거래량
     *     "tamt": "1000000",  // 당일 거래대금
     *     "ordy": "Y"
     *   }
     * }
     */
    private QuoteDto.QuoteResponse parseOverseasQuoteResponse(String symbol, String exchange,
                                                            String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            //응답 성공 여부 확인
            String rtCd = root.path("rt_cd").asText("");
            String msgCd = root.path("msg_cd").asText("");
            String msg1  = root.path("msg1").asText("");

            if (!"0".equals(rtCd)) {
                log.error("해외주식 응답 실패: symbol={}, exchange={}, rt_cd={}, msg_cd={}, msg1={}",
                        symbol, exchange, rtCd, msgCd, msg1);
                throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                        "해외주식 응답 실패: " + msg1);
            }

            //output 노드 가져오기
            JsonNode output = root.path("output");
            if (output.isMissingNode() || output.isNull()) {
                log.error("해외주식 output 데이터 없음: symbol={}, exchange={}, response={}",
                        symbol, exchange, response);
                throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                        "해외주식 데이터가 없습니다");
            }

            //필수 값 파싱 (없으면 0으로 기본 처리)
            BigDecimal currentPrice  = parseOverseasBigDecimal(output.path("last").asText(null));
            BigDecimal diff          = parseOverseasBigDecimal(output.path("diff").asText(null));
            BigDecimal percentChange = parseOverseasBigDecimal(output.path("rate").asText(null));
            BigDecimal previousClose = parseOverseasBigDecimal(output.path("base").asText(null));

            // 전일 대비 부호 확인 (5/4: 하락)
            String sign = output.path("sign").asText("3"); // 3: 보합 가정
            if ("5".equals(sign) || "4".equals(sign)) {
                diff = diff.negate();
                percentChange = percentChange.negate();
            }

            //해외 현재가 API는 high/low/open을 안 주기 때문에, previousClose / currentPrice 기반으로 합리적 기본값 세팅
            BigDecimal open = previousClose; // 전일 종가를 시가 비슷하게 사용
            BigDecimal high = currentPrice.max(previousClose);
            BigDecimal low  = currentPrice.min(previousClose);

            return QuoteDto.QuoteResponse.builder()
                    .symbol(symbol)
                    .currentPrice(currentPrice)
                    .change(diff)
                    .percentChange(percentChange)
                    .high(high)
                    .low(low)
                    .open(open)
                    .previousClose(previousClose)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .build();

        } catch (BusinessException e) {
            // 위에서 이미 로깅했으니 그대로 던짐
            throw e;
        } catch (Exception e) {
            log.error("해외주식 응답 파싱 실패: symbol={}, exchange={}, error={}, response={}",
                    symbol, exchange, e.getMessage(), response);
            throw new BusinessException(ErrorCode.API_ERROR, "해외주식 데이터 파싱 실패");
        }
    }
    /**
     * BigDecimal 안전 파싱 (공백/빈 문자열/"--" 포함)
     */
    private BigDecimal parseOverseasBigDecimal(String rawValue) {
        if (rawValue == null) {
            return BigDecimal.ZERO;
        }
        try {
            String value = rawValue.trim();
            if (value.isEmpty() || value.equals("--")) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
    /**
     * BigDecimal 안전 파싱 (오류 시 0 반환)
     */
    private BigDecimal parseBigDecimalSafe(JsonNode node) {
        return parseOverseasBigDecimal(node == null || node.isNull() ? null : node.asText());
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
