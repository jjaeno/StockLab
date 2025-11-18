package com.StockLab.backend.Order;

import com.StockLab.backend.Auth.AuthService;
import com.StockLab.backend.Auth.UserEntity;
import com.StockLab.backend.Quote.KisQuoteService;
import com.StockLab.backend.Quote.KisOverseasQuoteService;
import com.StockLab.backend.Quote.QuoteDto;
import com.StockLab.backend.common.BusinessException;
import com.StockLab.backend.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 주문 실행 서비스 (KRW/USD 이중 화폐 지원)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final CashLedgerRepository cashLedgerRepository;
    private final AuthService authService;
    private final KisQuoteService domesticQuoteService;
    private final KisOverseasQuoteService overseasQuoteService;
    
    /**
     * 주문 생성 및 즉시 체결
     */
    @Transactional
    public OrderDto.OrderResponse createOrder(String uid, OrderDto.OrderRequest request) {
        log.info("주문 생성: {} {} {} 주 - {}",
                uid, request.getSide(), request.getQuantity(), request.getSymbol());
        
        // 1. 종목 타입 및 통화 판단
        boolean isDomestic = isDomesticStock(request.getSymbol());
        UserEntity.Currency currency = isDomestic ? UserEntity.Currency.KRW : UserEntity.Currency.USD;
        
        // 2. 현재가 조회
        QuoteDto.QuoteResponse quote;
        if (isDomestic) {
            log.debug("   → 국내주식 시세 조회 (KRW)");
            quote = domesticQuoteService.getQuote(request.getSymbol());
        } else {
            log.debug("   → 해외주식 시세 조회 (USD)");
            String exchange = request.getExchange() != null ? request.getExchange() : "NASDAQ";
            quote = overseasQuoteService.getOverseasQuote(request.getSymbol(), exchange);
        }
        
        BigDecimal price = quote.getCurrentPrice();
        log.debug("   현재가: {} {}", price, currency);
        
        // 3. 주문 처리 (통화별)
        if (request.getSide() == OrderEntity.OrderSide.BUY) {
            return processBuyOrder(uid, request, price, currency);
        } else {
            return processSellOrder(uid, request, price, currency);
        }
    }
    
    /**
     * 매수 주문 처리 (통화별)
     */
    private OrderDto.OrderResponse processBuyOrder(String uid, OrderDto.OrderRequest request,
                                                   BigDecimal price, UserEntity.Currency currency) {
        UserEntity user = authService.getUserByUid(uid);
        BigDecimal totalCost = price.multiply(request.getQuantity());
        BigDecimal userBalance = user.getCash(currency);
        
        log.debug("   매수 금액: {} {}", totalCost, currency);
        log.debug("   보유 현금: {} {}", userBalance, currency);
        
        // 잔액 확인
        if (userBalance.compareTo(totalCost) < 0) {
            log.warn("잔액 부족: 필요 {} {}, 보유 {} {}", 
                    totalCost, currency, userBalance, currency);
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS);
        }
        
        // 현금 차감
        BigDecimal newBalance = userBalance.subtract(totalCost);
        authService.updateUserCash(uid, currency, newBalance);
        
        // 현금 변동 기록
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(totalCost.negate())
                .currency(currency)
                .transactionType(CashLedgerEntity.TransactionType.BUY)
                .symbol(request.getSymbol())
                .build());
        
        // 포지션 업데이트
        updatePositionForBuy(uid, request.getSymbol(), request.getQuantity(), price);
        
        // 주문 저장
        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .uid(uid)
                .symbol(request.getSymbol())
                .side(OrderEntity.OrderSide.BUY)
                .quantity(request.getQuantity())
                .price(price)
                .build());
        
        log.info("매수 체결 완료: 주문 #{} (잔액: {} {})", order.getId(), newBalance, currency);
        
        return toOrderResponse(order, totalCost, currency);
    }
    
    /**
     * 매도 주문 처리 (통화별)
     */
    private OrderDto.OrderResponse processSellOrder(String uid, OrderDto.OrderRequest request,
                                                    BigDecimal price, UserEntity.Currency currency) {
        // 보유 수량 확인
        PositionEntity position = positionRepository
                .findByUidAndSymbol(uid, request.getSymbol())
                .orElseThrow(() -> {
                    log.warn("보유하지 않은 종목: {}", request.getSymbol());
                    return new BusinessException(ErrorCode.POSITION_NOT_FOUND);
                });
        
        if (position.getQuantity().compareTo(request.getQuantity()) < 0) {
            log.warn("수량 부족: 보유 {}주, 매도 요청 {}주",
                    position.getQuantity(), request.getQuantity());
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
        }
        
        UserEntity user = authService.getUserByUid(uid);
        BigDecimal totalProceeds = price.multiply(request.getQuantity());
        BigDecimal userBalance = user.getCash(currency);
        
        log.debug("   매도 금액: {} {}", totalProceeds, currency);
        
        // 현금 추가
        BigDecimal newBalance = userBalance.add(totalProceeds);
        authService.updateUserCash(uid, currency, newBalance);
        
        // 현금 변동 기록
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(totalProceeds)
                .currency(currency)
                .transactionType(CashLedgerEntity.TransactionType.SELL)
                .symbol(request.getSymbol())
                .build());
        
        // 포지션 업데이트
        updatePositionForSell(position, request.getQuantity());
        
        // 주문 저장
        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .uid(uid)
                .symbol(request.getSymbol())
                .side(OrderEntity.OrderSide.SELL)
                .quantity(request.getQuantity())
                .price(price)
                .build());
        
        log.info("매도 체결 완료: 주문 #{} (잔액: {} {})", order.getId(), newBalance, currency);
        
        return toOrderResponse(order, totalProceeds, currency);
    }
    
    /**
     * 입금 처리 (통화별)
     */
    @Transactional
    public OrderDto.CashResponse deposit(String uid, OrderDto.CashRequest request) {
        UserEntity user = authService.getUserByUid(uid);
        UserEntity.Currency currency = request.getCurrency();
        BigDecimal currentBalance = user.getCash(currency);
        BigDecimal newBalance = currentBalance.add(request.getAmount());
        
        authService.updateUserCash(uid, currency, newBalance);
        
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(request.getAmount())
                .currency(currency)
                .transactionType(CashLedgerEntity.TransactionType.DEPOSIT)
                .build());
        
        log.info("입금 완료: {} → {} {} (잔액: {} {})", 
                uid, request.getAmount(), currency, newBalance, currency);
        
        return OrderDto.CashResponse.builder()
                .amount(request.getAmount())
                .currency(currency)
                .transactionType(CashLedgerEntity.TransactionType.DEPOSIT)
                .balanceAfter(newBalance)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }
    
    /**
     * 출금 처리 (통화별)
     */
    @Transactional
    public OrderDto.CashResponse withdraw(String uid, OrderDto.CashRequest request) {
        UserEntity user = authService.getUserByUid(uid);
        UserEntity.Currency currency = request.getCurrency();
        BigDecimal currentBalance = user.getCash(currency);
        
        if (currentBalance.compareTo(request.getAmount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS);
        }
        
        BigDecimal newBalance = currentBalance.subtract(request.getAmount());
        authService.updateUserCash(uid, currency, newBalance);
        
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(request.getAmount().negate())
                .currency(currency)
                .transactionType(CashLedgerEntity.TransactionType.WITHDRAW)
                .build());
        
        log.info("출금 완료: {} → {} {} (잔액: {} {})", 
                uid, request.getAmount(), currency, newBalance, currency);
        
        return OrderDto.CashResponse.builder()
                .amount(request.getAmount())
                .currency(currency)
                .transactionType(CashLedgerEntity.TransactionType.WITHDRAW)
                .balanceAfter(newBalance)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }
    
    /**
     * 매수 시 포지션 업데이트 (평균단가 재계산)
     */
    private void updatePositionForBuy(String uid, String symbol, BigDecimal quantity,
                                     BigDecimal price) {
        Optional<PositionEntity> existing = positionRepository.findByUidAndSymbol(uid, symbol);
        
        if (existing.isPresent()) {
            PositionEntity position = existing.get();
            
            BigDecimal existingValue = position.getAvgPrice().multiply(position.getQuantity());
            BigDecimal newValue = price.multiply(quantity);
            BigDecimal totalValue = existingValue.add(newValue);
            
            BigDecimal totalQuantity = position.getQuantity().add(quantity);
            BigDecimal newAvgPrice = totalValue.divide(totalQuantity, 6, RoundingMode.HALF_UP);
            
            log.debug("   평균단가 재계산: {} → {}", position.getAvgPrice(), newAvgPrice);
            
            position.setQuantity(totalQuantity);
            position.setAvgPrice(newAvgPrice);
            positionRepository.save(position);
            
        } else {
            log.debug("   신규 포지션 생성");
            positionRepository.save(PositionEntity.builder()
                    .uid(uid)
                    .symbol(symbol)
                    .quantity(quantity)
                    .avgPrice(price)
                    .build());
        }
    }
    
    /**
     * 매도 시 포지션 업데이트
     */
    private void updatePositionForSell(PositionEntity position, BigDecimal quantity) {
        BigDecimal newQuantity = position.getQuantity().subtract(quantity);
        
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("   포지션 전량 매도 (삭제)");
            positionRepository.delete(position);
        } else {
            log.debug("   잔여 수량: {}주", newQuantity);
            position.setQuantity(newQuantity);
            positionRepository.save(position);
        }
    }
    
    /**
     * 사용자 주문 내역 조회
     */
    public List<OrderEntity> getUserOrders(String uid) {
        return orderRepository.findByUidOrderByCreatedAtDesc(uid);
    }
    
    /**
     * 국내주식 여부 판단
     */
    private boolean isDomesticStock(String symbol) {
        return symbol.matches("\\d{6}");
    }
    
    private OrderDto.OrderResponse toOrderResponse(OrderEntity order, BigDecimal totalAmount,
                                                   UserEntity.Currency currency) {
        return OrderDto.OrderResponse.builder()
                .orderId(order.getId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .totalAmount(totalAmount)
                .currency(currency)
                .createdAt(order.getCreatedAt())
                .build();
    }
}