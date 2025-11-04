package com.StockLab.backend.common;

import lombok.Getter;

/*=====에러 코드 정의 Enum 클래스======*/

@Getter
public enum ErrorCode {
    
    // 인증 관련 (1xxx)
    INVALID_TOKEN(1001, "유효하지 않은 토큰입니다"),
    UNAUTHORIZED(1002, "인증이 필요합니다"),
    USER_NOT_FOUND(1003, "사용자를 찾을 수 없습니다"),
    
    // 거래 관련 (2xxx)
    INSUFFICIENT_FUNDS(2001, "잔액이 부족합니다"),
    INSUFFICIENT_QUANTITY(2002, "보유 수량이 부족합니다"),
    INVALID_ORDER(2003, "유효하지 않은 주문입니다"),
    ORDER_NOT_FOUND(2004, "주문을 찾을 수 없습니다"),
    POSITION_NOT_FOUND(2005, "보유 종목을 찾을 수 없습니다"),
    
    // 시세 관련 (3xxx)
    QUOTE_NOT_FOUND(3001, "시세를 조회할 수 없습니다"),
    API_ERROR(3002, "외부 API 오류가 발생했습니다"),
    INVALID_SYMBOL(3003, "유효하지 않은 종목 심볼입니다"),
    
    // 데이터 관련 (4xxx)
    RESOURCE_NOT_FOUND(4001, "리소스를 찾을 수 없습니다"),
    DUPLICATE_RESOURCE(4002, "이미 존재하는 리소스입니다"),
    INVALID_REQUEST(4003, "잘못된 요청입니다"),
    
    // 시스템 관련 (5xxx)
    INTERNAL_ERROR(5001, "내부 서버 오류가 발생했습니다"),
    DATABASE_ERROR(5002, "데이터베이스 오류가 발생했습니다");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}