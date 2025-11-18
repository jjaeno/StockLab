package com.StockLab.backend.Watchlist;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class WatchlistDto {
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddRequest {
        @NotBlank(message = "종목 코드/심볼을 입력해주세요")
        private String symbol;
        private String exchange; // 해외 종목일 경우 거래소(Optional)
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private String symbol;
        private String exchange;
        private LocalDateTime createdAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private String uid;
        private List<Item> items;
    }
}
