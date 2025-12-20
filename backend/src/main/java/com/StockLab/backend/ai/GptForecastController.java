package com.StockLab.backend.ai;

import com.StockLab.backend.ai.AiDto.GptForecastRequest;
import com.StockLab.backend.ai.AiDto.GptForecastResponse;
import com.StockLab.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class GptForecastController {

    private final GptForecastService gptForecastService;

    @PostMapping("/gpt-forecast")
    public ResponseEntity<ApiResponse<GptForecastResponse>> forecast(@RequestBody GptForecastRequest request) {
        GptForecastResponse response = gptForecastService.forecast(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
