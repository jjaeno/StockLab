# StockLab

**StockLab**은 실제 주식 시장 데이터를 기반으로  
국내·해외 주식을 분석하고 **AI의 도움을 받아 모의 투자**를 진행할 수 있는 모바일 애플리케이션입니다.

단순한 가상 매매를 넘어,  
**뉴스 데이터와 GPT 기반 AI 분석을 결합**하여  
사용자의 투자 판단을 보조하는 것을 목표로 합니다.

## 주요 특징

- 국내(KOSPI/KOSDAQ) 및 해외(NYSE/NASDAQ) 주식 실시간 시세 조회
- 가상 자산 기반 모의 매수·매도 기능
- 사용자별 포트폴리오 및 거래 내역 관리
- 관심 종목(Watchlist) 등록 및 관리
- 네이버 뉴스 기반 종목별 뉴스 제공
- GPT 기반 AI 시장 분석 및 종목 추천


## AI 기능

### AI 주목 종목 (Hot Stocks)
- 최근 뉴스 이슈를 분석해 **AI가 주목하는 종목 TOP 3**를 홈 화면에 표시
- 각 종목에 대해 다음 정보를 함께 제공
  - 주목 이유
  - 주요 리스크 요인

### 종목별 AI 시장 분석
- 종목 상세 화면에서 해당 종목과 관련된 뉴스를 기반으로 AI 분석 수행
- 분석 결과로 다음 정보를 제공
  - 시장 방향성: **상승 / 하락 / 중립 / 불확실**
  - 분석 신뢰도(Confidence)
  - 요약 및 주요 리스크
## 시스템 아키텍처

Android App (Kotlin / Jetpack Compose)  
→ Spring Boot Backend  
→ External APIs (한국투자증권, 네이버 뉴스, OpenAI GPT)

- 프론트엔드: Kotlin + Jetpack Compose (MVVM)
- 백엔드: Spring Boot + JPA + MySQL
- 인증: Firebase Authentication


## 기술 스택

### Frontend
- Kotlin
- Jetpack Compose
- MVVM Architecture
- Retrofit / OkHttp
- Hilt (DI)
- MPAndroidChart

### Backend
- Spring Boot
- Spring Data JPA
- Spring WebFlux (비동기 처리)
- MySQL
- Caffeine Cache
- Resilience4j (Rate Limiting)

### Authentication
- Firebase Authentication (Email / Password)

### External APIs
- 한국투자증권 Open API (국내·해외 주식 시세)
- 네이버 뉴스 검색 API
- OpenAI GPT API

## 앱 화면 미리보기


### 주요 화면

<img src="https://github.com/user-attachments/assets/44497f11-4426-4328-ad5c-160d64ba3e34" width="200"/>
<img src="https://github.com/user-attachments/assets/70cde187-fa13-45a8-8ceb-a9e927c75d0e" width="200"/>
<img src="https://github.com/user-attachments/assets/dd829b26-d549-4c88-9839-b410205e1e01" width="200"/>
<img src="https://github.com/user-attachments/assets/3b98de28-51a3-4ceb-9c92-b2bbe39089b9" width="200"/>
<img src="https://github.com/user-attachments/assets/3d271f72-01ce-4354-a509-4f7fc8c5b5f4" width="200"/>
<img src="https://github.com/user-attachments/assets/16cbc3c0-129b-4e56-a94a-c921d21b59bf" width="200"/>

## 주요 기능

### 사용자 인증
- Firebase Authentication 기반 로그인 / 회원가입
- 사용자별 데이터 완전 분리
- 초기 가상 자산 지급
  - KRW 10,000,000
  - USD 10,000

### 실시간 주식 시세
- 국내/해외 주식 단일 종목 조회
- 다중 종목 Batch 조회 API 제공
- 캐시 기반 응답 속도 개선 (TTL 60초)
- 일부 종목 실패 시에도 응답 누락 없이 결과 반환


### 차트 시각화
- 캔들 차트 제공 (1D, 1W, 1M, 3M, 1Y)
- 확대/축소(Pinch Zoom), 드래그 스크롤 지원

### 모의 매수 · 매도
- 실시간 시세 기준 주문 체결
- 평균 단가 자동 계산
- 주문 / 보유 종목 / 현금 흐름 분리 관리

### 포트폴리오
- 보유 종목 및 수익률 확인
- 국내/해외 자산 분리 관리


### 관심 종목 (Watchlist)
- 관심 종목 추가 / 삭제
- 사용자별 중복 등록 방지

### 뉴스 조회
- 네이버 뉴스 API 연동
- 종목별 최신 뉴스 제공
- 뉴스 캐시 적용 (TTL 10분)

## 성능 및 안정성

- Caffeine Cache 적용 (시세, 뉴스)
- 한국투자증권 API Rate Limit 준수
- Reactor 기반 병렬 시세 조회
- Android 화면 재진입 시 중복 API 호출 방지

## 향후 개선 계획

- 주문 대기 / 취소 기능
- 기술적 지표(MA, RSI, MACD) 추가
- 푸시 알림(가격·체결 알림)
- Redis 기반 분산 캐시
- HTTPS 강제 및 보안 강화
