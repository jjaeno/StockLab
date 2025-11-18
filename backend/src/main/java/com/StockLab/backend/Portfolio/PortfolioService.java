package com.StockLab.backend.Portfolio;

import com.StockLab.backend.Auth.AuthService;
import com.StockLab.backend.Auth.UserEntity;
import com.StockLab.backend.Order.PositionEntity;
import com.StockLab.backend.Order.PositionRepository;
import com.StockLab.backend.Quote.KisOverseasQuoteService;
import com.StockLab.backend.Quote.KisQuoteService;
import com.StockLab.backend.Quote.QuoteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {
    
    private final PositionRepository positionRepository;
    private final AuthService authService;
    private final KisQuoteService domesticQuoteService;
    private final KisOverseasQuoteService overseasQuoteService;
    
    /**
     * 포트폴리오 조회 (KRW, USD 분리)
     */
    public PortfolioDto.PortfolioResponse getPortfolio(String uid) {
        UserEntity user = authService.getUserByUid(uid);
        List<PositionEntity> positions = positionRepository.findByUid(uid);
        List<PortfolioDto.PositionView> positionViews = new ArrayList<>();
        
        BigDecimal totalKrw = BigDecimal.ZERO;
        BigDecimal totalUsd = BigDecimal.ZERO;
        
        for (PositionEntity position : positions) {
            boolean isDomestic = isDomesticStock(position.getSymbol());
            UserEntity.Currency currency = isDomestic ? UserEntity.Currency.KRW : UserEntity.Currency.USD;
            QuoteDto.QuoteResponse quote = isDomestic
                    ? domesticQuoteService.getQuote(position.getSymbol())
                    : overseasQuoteService.getUsStock(position.getSymbol());
            
            BigDecimal currentPrice = quote.getCurrentPrice();
            BigDecimal marketValue = currentPrice.multiply(position.getQuantity());
            BigDecimal costBasis = position.getAvgPrice().multiply(position.getQuantity());
            BigDecimal profitLoss = marketValue.subtract(costBasis);
            
            if (currency == UserEntity.Currency.KRW) {
                totalKrw = totalKrw.add(marketValue);
            } else {
                totalUsd = totalUsd.add(marketValue);
            }
            
            positionViews.add(PortfolioDto.PositionView.builder()
                    .symbol(position.getSymbol())
                    .quantity(position.getQuantity())
                    .avgPrice(position.getAvgPrice())
                    .currentPrice(currentPrice)
                    .marketValue(marketValue.setScale(2, RoundingMode.HALF_UP))
                    .profitLoss(profitLoss.setScale(2, RoundingMode.HALF_UP))
                    .currency(currency)
                    .build());
        }
        
        return PortfolioDto.PortfolioResponse.builder()
                .uid(uid)
                .cashKrw(user.getCash(UserEntity.Currency.KRW))
                .cashUsd(user.getCash(UserEntity.Currency.USD))
                .positions(positionViews)
                .totalMarketValueKrw(totalKrw.setScale(2, RoundingMode.HALF_UP))
                .totalMarketValueUsd(totalUsd.setScale(2, RoundingMode.HALF_UP))
                .build();
    }
    
    // 6자리 숫자면 국내 주식으로 간주
    private boolean isDomesticStock(String symbol) {
        return symbol != null && symbol.matches("\\d{6}");
    }
}
