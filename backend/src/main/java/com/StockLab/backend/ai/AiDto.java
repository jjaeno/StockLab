package com.StockLab.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AiDto {

    public enum ForecastDirection {
        UP,
        DOWN,
        NEUTRAL,
        UNCERTAIN
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsedArticle {
        private String title;
        private String url;
        private String publishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GptForecastRequest {
        private String symbol;
        private String displayName;
        @Builder.Default
        private Integer limit = 5;
        private String query;
        private String model;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GptForecastResponse {
        private String summary;
        private ForecastDirection direction;
        private double confidence;
        private String risks;
        private List<UsedArticle> usedArticles;
        private String model;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAiChatMessage {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAiChatRequest {
        private String model;
        private List<OpenAiChatMessage> messages;
        private Double temperature;
        private Integer max_tokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAiChatResponse {
        private List<Choice> choices;
        private Usage usage;
        private String model;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Choice {
            private int index;
            private Message message;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Message {
            private String role;
            private String content;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Usage {
            private Integer prompt_tokens;
            private Integer completion_tokens;
        }
    }
}
