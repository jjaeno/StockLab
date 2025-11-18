package com.StockLab.backend.Order;

import com.StockLab.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 거래 실행 Controller (KRW/USD 이중 화폐 지원)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order", description = "주문 실행 API (국내+해외, KRW/USD 분리)")
public class OrderController {
    
    private final OrderService orderService;
    
    /**
     * 주문 생성 (매수/매도)
     * 
     * POST /api/v1/orders
     * Header: X-User-UID: firebase-uid-123
     * Body:
     * {
     *   "symbol": "005930",        // 또는 "AAPL"
     *   "side": "BUY",
     *   "quantity": 5,
     *   "exchange": "NASDAQ"       // 해외주식일 경우 (선택)
     * }
     * 
     * Response:
     * {
     *   "orderId": 123,
     *   "symbol": "AAPL",
     *   "side": "BUY",
     *   "quantity": 5,
     *   "price": 180.50,
     *   "totalAmount": 902.50,
     *   "currency": "USD",         // 자동 판단
     *   "createdAt": "2025-01-15T..."
     * }
     */
    @PostMapping("/orders")
    @Operation(summary = "주문 생성", 
               description = "매수 또는 매도 주문을 즉시 체결합니다 (국내=KRW, 해외=USD 자동 처리)")
    public ApiResponse<OrderDto.OrderResponse> createOrder(
            @RequestHeader("X-User-UID") String uid,
            @Valid @RequestBody OrderDto.OrderRequest request) {
        
        OrderDto.OrderResponse response = orderService.createOrder(uid, request);
        return ApiResponse.success(response, "주문이 체결되었습니다");
    }
    
    /**
     * 사용자 주문 내역 조회
     */
    @GetMapping("/orders/{uid}")
    @Operation(summary = "주문 내역", description = "사용자의 모든 주문 내역을 조회합니다")
    public ApiResponse<List<OrderEntity>> getUserOrders(@PathVariable String uid) {
        List<OrderEntity> orders = orderService.getUserOrders(uid);
        return ApiResponse.success(orders);
    }
    
    /**
     * 입금 (통화별)
     * 
     * POST /api/v1/account/deposit
     * Header: X-User-UID: firebase-uid-123
     * Body:
     * {
     *   "amount": 1000000,
     *   "currency": "KRW"      // "KRW" 또는 "USD"
     * }
     * 
     * Response:
     * {
     *   "amount": 1000000,
     *   "currency": "KRW",
     *   "transactionType": "DEPOSIT",
     *   "balanceAfter": 11000000,
     *   "timestamp": "2025-01-15T..."
     * }
     */
    @PostMapping("/account/deposit")
    @Operation(summary = "입금 (통화별)", 
               description = "KRW 또는 USD 계좌에 현금을 입금합니다")
    public ApiResponse<OrderDto.CashResponse> deposit(
            @RequestHeader("X-User-UID") String uid,
            @Valid @RequestBody OrderDto.CashRequest request) {
        
        OrderDto.CashResponse response = orderService.deposit(uid, request);
        return ApiResponse.success(response, "입금이 완료되었습니다");
    }
    
    /**
     * 출금 (통화별)
     * 
     * POST /api/v1/account/withdraw
     * Header: X-User-UID: firebase-uid-123
     * Body:
     * {
     *   "amount": 500000,
     *   "currency": "USD"      // "KRW" 또는 "USD"
     * }
     * 
     * Response:
     * {
     *   "amount": 500000,
     *   "currency": "USD",
     *   "transactionType": "WITHDRAW",
     *   "balanceAfter": 9500000,
     *   "timestamp": "2025-01-15T..."
     * }
     */
    @PostMapping("/account/withdraw")
    @Operation(summary = "출금 (통화별)", 
               description = "KRW 또는 USD 계좌에서 현금을 출금합니다")
    public ApiResponse<OrderDto.CashResponse> withdraw(
            @RequestHeader("X-User-UID") String uid,
            @Valid @RequestBody OrderDto.CashRequest request) {
        
        OrderDto.CashResponse response = orderService.withdraw(uid, request);
        return ApiResponse.success(response, "출금이 완료되었습니다");
    }
}