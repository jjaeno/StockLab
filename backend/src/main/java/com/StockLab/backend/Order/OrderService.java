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
 * 주문 실행 서비스 (KIS 시세 연동)
 * 
 * 기능:
 * - 국내주식 + 해외주식 매수/매도
 * - 실시간 시세 기반 체결
 * - 평균단가 자동 계산
 * - 입출금 처리
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
     * 
     * @param uid 사용자 UID
     * @param request 주문 요청 (symbol, side, quantity, exchange?)
     * @return 체결된 주문 정보
     */
    @Transactional
    public OrderDto.OrderResponse createOrder(String uid, OrderDto.OrderRequest request) {
        log.info("주문 생성: {} {} {} 주 - {}",
                uid, request.getSide(), request.getQuantity(), request.getSymbol());
        
        // 1. 종목 타입 판단 (국내 or 해외)
        boolean isDomestic = isDomesticStock(request.getSymbol());
        
        // 2. 현재가 조회 (KIS API)
        QuoteDto.QuoteResponse quote;
        if (isDomestic) {
            log.debug("   → 국내주식 시세 조회");
            quote = domesticQuoteService.getQuote(request.getSymbol());
        } else {
            log.debug("   → 해외주식 시세 조회");
            String exchange = request.getExchange() != null 
                    ? request.getExchange() 
                    : "NASDAQ"; // 기본값
            quote = overseasQuoteService.getOverseasQuote(request.getSymbol(), exchange);
        }
        
        BigDecimal price = quote.getCurrentPrice();
        log.debug("   현재가: {}원/달러", price);
        
        // 3. 주문 처리
        if (request.getSide() == OrderEntity.OrderSide.BUY) {
            return processBuyOrder(uid, request, price);
        } else {
            return processSellOrder(uid, request, price);
        }
    }
    
    /**
     * 매수 주문 처리
     */
    private OrderDto.OrderResponse processBuyOrder(String uid, OrderDto.OrderRequest request,
                                                   BigDecimal price) {
        UserEntity user = authService.getUserByUid(uid);
        BigDecimal totalCost = price.multiply(request.getQuantity());
        
        log.debug("   매수 금액: {}원", totalCost);
        log.debug("   보유 현금: {}원", user.getCash());
        
        // 잔액 확인
        if (user.getCash().compareTo(totalCost) < 0) {
            log.warn("잔액 부족: 필요 {}원, 보유 {}원", totalCost, user.getCash());
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS);
        }
        
        // 현금 차감
        user.setCash(user.getCash().subtract(totalCost));
        authService.updateUserCash(uid, user.getCash());
        
        // 현금 변동 기록
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(totalCost.negate())
                .reason("BUY")
                .build());
        
        // 포지션 업데이트 (평균단가 재계산)
        updatePositionForBuy(uid, request.getSymbol(), request.getQuantity(), price);
        
        // 주문 저장
        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .uid(uid)
                .symbol(request.getSymbol())
                .side(OrderEntity.OrderSide.BUY)
                .quantity(request.getQuantity())
                .price(price)
                .build());
        
        log.info("매수 체결 완료: 주문 #{}", order.getId());
        
        return toOrderResponse(order, totalCost);
    }
    
    /**
     * 매도 주문 처리
     */
    private OrderDto.OrderResponse processSellOrder(String uid, OrderDto.OrderRequest request,
                                                    BigDecimal price) {
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
        
        log.debug("   매도 금액: {}원", totalProceeds);
        
        // 현금 추가
        user.setCash(user.getCash().add(totalProceeds));
        authService.updateUserCash(uid, user.getCash());
        
        // 현금 변동 기록
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(totalProceeds)
                .reason("SELL")
                .build());
        
        // 포지션 업데이트 (수량 차감)
        updatePositionForSell(position, request.getQuantity());
        
        // 주문 저장
        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .uid(uid)
                .symbol(request.getSymbol())
                .side(OrderEntity.OrderSide.SELL)
                .quantity(request.getQuantity())
                .price(price)
                .build());
        
        log.info("매도 체결 완료: 주문 #{}", order.getId());
        
        return toOrderResponse(order, totalProceeds);
    }
    
    /**
     * 매수 시 포지션 업데이트 (평균단가 재계산)
     */
    private void updatePositionForBuy(String uid, String symbol, BigDecimal quantity,
                                     BigDecimal price) {
        Optional<PositionEntity> existing = positionRepository.findByUidAndSymbol(uid, symbol);
        
        if (existing.isPresent()) {
            // 기존 포지션 있음 → 평균단가 재계산
            PositionEntity position = existing.get();
            
            BigDecimal existingValue = position.getAvgPrice().multiply(position.getQuantity());
            BigDecimal newValue = price.multiply(quantity);
            BigDecimal totalValue = existingValue.add(newValue);
            
            BigDecimal totalQuantity = position.getQuantity().add(quantity);
            BigDecimal newAvgPrice = totalValue.divide(totalQuantity, 6, RoundingMode.HALF_UP);
            
            log.debug("   평균단가 재계산: {}원 → {}원", position.getAvgPrice(), newAvgPrice);
            
            position.setQuantity(totalQuantity);
            position.setAvgPrice(newAvgPrice);
            positionRepository.save(position);
            
        } else {
            // 신규 포지션 생성
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
     * 매도 시 포지션 업데이트 (수량 차감)
     */
    private void updatePositionForSell(PositionEntity position, BigDecimal quantity) {
        BigDecimal newQuantity = position.getQuantity().subtract(quantity);
        
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            // 전량 매도: 포지션 삭제
            log.debug("   포지션 전량 매도 (삭제)");
            positionRepository.delete(position);
        } else {
            // 일부 매도: 수량만 차감
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
     * 입금 처리
     */
    @Transactional
    public void deposit(String uid, BigDecimal amount) {
        UserEntity user = authService.getUserByUid(uid);
        user.setCash(user.getCash().add(amount));
        authService.updateUserCash(uid, user.getCash());
        
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(amount)
                .reason("DEPOSIT")
                .build());
        
        log.info("입금 완료: {} → {}원", uid, amount);
    }
    
    /**
     * 출금 처리
     */
    @Transactional
    public void withdraw(String uid, BigDecimal amount) {
        UserEntity user = authService.getUserByUid(uid);
        
        if (user.getCash().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS);
        }
        
        user.setCash(user.getCash().subtract(amount));
        authService.updateUserCash(uid, user.getCash());
        
        cashLedgerRepository.save(CashLedgerEntity.builder()
                .uid(uid)
                .amount(amount.negate())
                .reason("WITHDRAW")
                .build());
        
        log.info("출금 완료: {} → {}원", uid, amount);
    }
    
    /**
     * 국내주식 여부 판단
     */
    private boolean isDomesticStock(String symbol) {
        return symbol.matches("\\d{6}");
    }
    
    private OrderDto.OrderResponse toOrderResponse(OrderEntity order, BigDecimal totalAmount) {
        return OrderDto.OrderResponse.builder()
                .orderId(order.getId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .totalAmount(totalAmount)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
