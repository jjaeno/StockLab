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
 * 거래 실행 Controller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order", description = "주문 실행 API (국내+해외)")
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
     */
    @PostMapping("/orders")
    @Operation(summary = "주문 생성", 
               description = "매수 또는 매도 주문을 즉시 체결합니다 (국내+해외 지원)")
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
     * 입금
     * 
     * POST /api/v1/account/deposit
     * Header: X-User-UID: firebase-uid-123
     * Body:
     * {
     *   "amount": 100000
     * }
     */
    @PostMapping("/account/deposit")
    @Operation(summary = "입금", description = "계좌에 현금을 입금합니다")
    public ApiResponse<Void> deposit(
            @RequestHeader("X-User-UID") String uid,
            @Valid @RequestBody OrderDto.CashRequest request) {
        
        orderService.deposit(uid, request.getAmount());
        return ApiResponse.success(null, "입금이 완료되었습니다");
    }
    
    /**
     * 출금
     * 
     * POST /api/v1/account/withdraw
     * Header: X-User-UID: firebase-uid-123
     * Body:
     * {
     *   "amount": 100000
     * }
     */
    @PostMapping("/account/withdraw")
    @Operation(summary = "출금", description = "계좌에서 현금을 출금합니다")
    public ApiResponse<Void> withdraw(
            @RequestHeader("X-User-UID") String uid,
            @Valid @RequestBody OrderDto.CashRequest request) {
        
        orderService.withdraw(uid, request.getAmount());
        return ApiResponse.success(null, "출금이 완료되었습니다");
    }
}
