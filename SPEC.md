# Serox — AI-Native Web Browser

## 통합 설계 명세서 v1.0

**프로젝트명:** Serox  
**프로젝트 유형:** 독립형 AI 통합 범용 웹 브라우저  
**주 개발 언어:** Java  
**빌드 시스템:** Maven  
**기본 UI:** JavaFX  
**검색:** 외부 Search API  
**인공지능:** Claude API  
**외부 연동:** Model Context Protocol(MCP)  
**데이터베이스:** SQLite  
**프로젝트 성격:** 자바 수행평가 및 장기 소프트웨어 개발 프로젝트

---

### 1. 프로젝트 개요

### 1.1 프로젝트 정의

Serox는 Java를 기반으로 브라우저의 핵심 구성 요소를 직접 구현하는 AI 통합 범용 웹 브라우저이다.

기존 브라우저 엔진인 Chromium, WebKit, Gecko 및 이를 임베딩하는 JCEF, JavaFX WebView 등에 의존하지 않는
독립적인 렌더링 엔진을 구축한다.

웹 검색에는 외부 Search API를 사용하며, 웹페이지 자체의 다운로드, 해석, 렌더링 및 탐색은 Serox가 담당한다.

AI는 브라우저의 보조 기능으로 통합되며, 일반 웹 탐색과 독립적으로 작동한다.

### 1.2 개발 배경

기존 웹 검색은 검색 결과를 확인하고 여러 웹페이지를 직접 열어 필요한 정보를 수집해야 한다.

검색 과정에서 광고, 중복 정보, 불필요한 내용으로 인해 탐색 시간이 늘어날 수 있다.

Serox는 다음 문제를 해결하는 것을 목표로 한다.

1. 일반적인 웹 탐색과 AI 검색의 분리
2. 여러 웹페이지에 분산된 정보의 수동 수집
3. 검색 결과의 근거 및 출처 확인에 필요한 시간
4. 기존 AI 에이전트가 브라우저 검색·분석 기능을 재사용하기 어려운 문제

이를 위해 하나의 애플리케이션에서 일반 웹 탐색, AI 검색, 페이지 분석 및 MCP 연동 기능을 제공한다.

### 1.3 프로젝트 목표

#### 핵심 목표

- 독립적인 브라우저 엔진 구축
- HTTP/HTTPS 기반 웹페이지 요청 및 수신
- HTML 문서 파싱과 DOM 생성
- CSS 처리 및 화면 레이아웃 계산
- 웹페이지 렌더링
- 웹페이지 탐색 및 다중 탭 관리
- Search API 기반 일반 웹 검색
- 검색 결과의 본문 추출
- Claude API 기반 AI 요약 및 분석
- 출처와 함께 AI 답변 표시
- 원문 페이지와 AI 답변의 연결
- MCP 서버를 통한 외부 AI 에이전트 연동
- 방문 기록, 북마크, 설정 및 브라우저 세션 관리

#### 장기 목표

- JavaScript 기반 동적 웹사이트 지원
- 웹 표준 호환성 확대
- 성능 및 메모리 최적화
- 브라우저 보안 강화
- 사용자 프로필과 시크릿 모드
- 확장 가능한 AI Provider 시스템
- 외부 AI 에이전트를 통한 브라우저 제어

### 1.4 개발 원칙

1. 브라우저 기능은 AI 기능과 독립적으로 작동한다.
2. Search API는 웹 검색 용도로만 사용한다.
3. 웹 렌더링은 자체 엔진이 담당한다.
4. UI와 브라우저 엔진을 분리한다.
5. AI가 접근할 수 있는 데이터 범위를 제한한다.
6. 렌더링, 네트워크, AI 요청을 UI 스레드에서 실행하지 않는다.
7. 모듈 간 결합도를 낮추고 인터페이스 기반으로 설계한다.
8. 기능 구현은 단계적으로 진행한다.
9. 주요 기능은 단위 테스트 및 통합 테스트를 제공한다.
10. 지원하지 않는 웹 표준 및 제한 사항을 명확하게 관리한다.

---

## 2. 기술 스택

| 영역 | 기술 | 역할 |
| --- | --- | --- |
| Core | Java 25 이상 | 핵심 애플리케이션 |
| Build | Maven | 빌드 및 의존성 관리 |
| Desktop UI | JavaFX | 브라우저 외부 UI |
| Network | Java HttpClient 및 자체 네트워크 추상화 | HTTP 요청 |
| HTML | 자체 Parser | HTML 토큰화 및 DOM 생성 |
| CSS | 자체 Parser | 스타일 규칙 해석 |
| Layout | 자체 Layout Engine | 요소 크기 및 위치 계산 |
| Rendering | JavaFX Canvas/GraphicsContext | 초기 화면 그리기 |
| JavaScript | GraalJS 검토 | ECMAScript 실행 |
| Search | 외부 Search API | 검색 결과 제공 |
| HTML Extraction | Jsoup | 검색 대상 문서 본문 추출 보조 |
| AI | Claude API | AI 분석 및 답변 |
| Database | SQLite | 로컬 데이터 저장 |
| MCP | MCP Java SDK | MCP 서버 구현 |
| Concurrency | Virtual Threads, CompletableFuture | 비동기 작업 |
| Testing | JUnit | 자동 테스트 |

JavaFX는 브라우저 창과 렌더링 표면을 제공한다. 웹페이지의 HTML/CSS 해석과 레이아웃 계산은 Serox 자체 엔진에서 수행한다.

Jsoup은 웹페이지를 렌더링하는 엔진이 아니라 검색 결과 본문 추출을 돕는 보조 라이브러리로 사용한다.

JavaScript 지원은 ECMAScript 런타임과 브라우저 Web API를 분리해 구현한다. GraalJS 채택 여부는 초기 기술 검증 후
확정한다.

---

## 3. 전체 시스템 아키텍처

```text
Serox Application
│
├── Presentation Layer
│   ├── MainWindow
│   ├── BrowserView
│   ├── TabView
│   ├── SearchView
│   ├── AiPanel
│   └── SettingsView
│
├── Application Core
│   ├── BrowserController
│   ├── TabManager
│   ├── NavigationManager
│   ├── SessionManager
│   ├── PermissionManager
│   └── BrowserEventBus
│
├── Browser Engine
│   ├── Network Engine
│   ├── HTML Parser
│   ├── DOM Engine
│   ├── CSS Engine
│   ├── Layout Engine
│   ├── Rendering Engine
│   ├── JavaScript Engine
│   └── Event Engine
│
├── Search Engine
│   ├── SearchCoordinator
│   ├── SearchApiClient
│   ├── PageFetcher
│   ├── ContentExtractor
│   ├── ResultRanker
│   └── EvidenceStore
│
├── AI Engine
│   ├── AiProvider
│   ├── ClaudeProvider
│   ├── ContextBuilder
│   ├── PageAnalyzer
│   ├── ConversationManager
│   ├── AnswerGenerator
│   └── CitationManager
│
├── MCP Server
│   ├── ToolRegistry
│   ├── ToolDispatcher
│   ├── AccessController
│   └── MCP Tools
│
└── Storage
    ├── HistoryRepository
    ├── BookmarkRepository
    ├── SessionRepository
    ├── SettingsRepository
    ├── ConversationRepository
    └── SearchCache
```

### 3.1 모듈 간 관계

- Presentation Layer는 Core에 명령을 전달한다.
- Application Core는 전체 애플리케이션 상태를 관리한다.
- Browser Engine은 네트워크 요청, 문서 해석 및 렌더링을 담당한다.
- Search Engine은 검색 API 및 검색 대상 웹페이지를 처리한다.
- AI Engine은 Search Engine과 Browser Engine에서 전달받은 콘텐츠를 분석한다.
- MCP Server는 허용된 기능을 외부에 노출한다.
- Storage는 브라우저 메타데이터와 로컬 상태를 보관한다.

각 모듈은 공개 인터페이스로 연결하며, 다른 모듈의 내부 구현에 직접 접근하지 않는다.

---

## 4. Browser Engine 설계

### 4.1 Network Engine

#### 목적

브라우저가 웹 서버에 직접 연결하여 HTML, CSS, JavaScript, 이미지 및 기타 리소스를 수신한다.

#### 주요 기능

- HTTP/HTTPS 요청
- GET/POST 요청
- 요청 헤더 및 응답 헤더 처리
- Redirect 처리
- Cookie 관리
- 압축 응답 처리
- Content-Type 판별
- 문자 인코딩 처리
- Timeout
- 요청 취소
- 다운로드
- 리소스 캐싱
- TLS 인증서 검증

#### 주요 클래스

```text
NetworkManager
HttpRequestDispatcher
HttpResponseHandler
RedirectHandler
CookieStore
ResourceLoader
CacheManager
DownloadManager
```

#### HTTP 처리 과정

```text
URL
 ↓
URL Parser
 ↓
Request Builder
 ↓
DNS / TLS / HTTP
 ↓
Response Handler
 ↓
Status / Headers / Body
 ↓
Resource Dispatcher
 ↓
HTML / CSS / JS / Image
```

초기에는 Java 표준 HTTP 클라이언트를 활용하고, 브라우저 전용 캐시·쿠키·리소스 스케줄링을 별도로 구현한다.

HTTP 프로토콜 구현까지 완전히 직접 작성하는 것은 초기 목표에 포함하지 않는다.

### 4.2 HTML Parser

#### HTML Parser 목적

수신한 HTML 문서를 분석하여 DOM 트리를 생성한다.

#### 처리 단계

1. 입력 바이트를 문자 데이터로 디코딩한다.
2. HTML Tokenizer가 토큰을 생성한다.
3. Tree Builder가 토큰을 해석한다.
4. HTML 요소와 속성을 생성한다.
5. 부모·자식 관계를 연결한다.
6. DOM Document를 반환한다.

#### 주요 객체

```text
HtmlTokenizer
HtmlToken
HtmlTreeBuilder
Document
Node
Element
TextNode
CommentNode
Attribute
```

#### 초기 지원 요소

- html, head, body, title, meta
- div, span, p
- h1~h6
- a, img
- ul, ol, li
- table, tr, td, th
- form, input, button
- style, link, script
- br, hr
- strong, em

HTML 오류 복구, 잘못 중첩된 태그 처리 및 문서 인코딩은 단계적으로 표준 호환성을 높인다.

### 4.3 DOM Engine

#### DOM Engine 목적

HTML 문서의 구조와 상태를 메모리에 표현한다.

#### 필수 기능

- 부모 및 자식 노드 접근
- 노드 추가·삭제·교체
- 속성 조회 및 수정
- ID 및 Class 기반 검색
- CSS Selector 기반 검색
- Text Content 조회
- DOM 변경 이벤트 전달
- 변경 영역의 스타일 및 레이아웃 재계산 요청

#### 기본 구조

```java
public abstract class Node {
    private Node parent;
    private List<Node> children;
}

public class Element extends Node {
    private String tagName;
    private Map<String, String> attributes;
}

public class Document extends Node {
    private String documentUrl;
}
```

위 코드는 개념적 데이터 모델이며, 실제 구현에서는 접근 제어와 변경 이벤트를 포함한다.

### 4.4 CSS Engine

#### CSS Engine 목적

CSS 규칙을 파싱하고 DOM 요소에 최종 스타일을 적용한다.

#### 하위 구성

```text
CssTokenizer
CssParser
SelectorMatcher
CascadeResolver
InheritanceResolver
ComputedStyleBuilder
```

#### 지원 순서

#### 초기 지원

- 색상 및 배경
- 글꼴 크기·굵기
- margin, padding, border
- width, height
- display: block, inline, none
- 기본 Selector
- 스타일 상속
- CSS 우선순위

#### 확장 지원

- Positioning
- Flexbox
- Grid
- Media Queries
- Pseudo Classes
- Transforms
- Transitions
- Animations
- Custom Properties

CSS의 Cascade, Specificity 및 상속 규칙을 별도의 계산 과정으로 설계한다.

### 4.5 Layout Engine

#### Layout Engine 목적

DOM과 계산된 CSS 스타일을 이용해 웹페이지 요소의 크기와 위치를 결정한다.

#### Layout Engine 처리 과정

```text
DOM Tree + Computed Styles
              ↓
          Box Tree
              ↓
       Layout Calculation
              ↓
       Positioned Boxes
              ↓
          Paint Tree
```

#### 초기 지원 레이아웃

- Block Formatting
- Inline Formatting
- Box Model
- Text Wrapping
- Line Breaking
- Viewport
- Scroll Offset
- 기본 이미지 배치

#### 확장 레이아웃

- Flexbox
- Grid
- Absolute Positioning
- Fixed Positioning
- Sticky Positioning
- Float
- Overflow 및 Clipping

#### Layout Engine 주요 클래스

```text
LayoutEngine
LayoutBox
BlockLayout
InlineLayout
TextLayout
LayoutConstraints
LayoutResult
```

DOM Tree와 Layout Tree를 분리하여 `display: none` 등 DOM 요소와 렌더링 요소 사이의 차이를 처리한다.

### 4.6 Rendering Engine

#### Rendering Engine 목적

계산된 레이아웃을 바탕으로 화면을 그린다.

#### 초기 렌더링 대상

- 텍스트
- 배경색
- 테두리
- 이미지
- 링크
- 기본 폼 컨트롤
- 스크롤 영역

#### 주요 구성

```text
RenderCoordinator
PaintCommand
DisplayList
CanvasRenderer
TextPainter
ImagePainter
LayerManager
```

#### 렌더링 흐름

```text
Layout Result
      ↓
Paint Commands
      ↓
Display List
      ↓
Canvas Renderer
      ↓
Screen
```

초기에는 JavaFX Canvas를 사용하고, 장기적으로 별도 그래픽 백엔드 추상화를 추가한다.

#### 성능 최적화

- 변경된 영역만 다시 그리기
- 이미지 캐싱
- 레이아웃 결과 재사용
- 불필요한 Paint 방지
- 화면 밖 요소의 그리기 최소화
- 렌더링 작업의 단계적 분리

### 4.7 JavaScript Engine

#### JavaScript Engine 목적

JavaScript를 실행하고 DOM 및 사용자 이벤트와 연결한다.

#### 구성

```text
JavaScriptRuntime
ExecutionContext
DomBindings
WindowBindings
EventLoop
TaskQueue
MicrotaskQueue
TimerManager
```

#### 필수 연동 대상

- window
- document
- console
- DOM Element
- EventTarget
- addEventListener
- setTimeout
- setInterval
- fetch
- Promise
- DOM 조작

JavaScript 언어 실행에는 GraalJS 사용을 우선 검토한다.

단, GraalJS는 브라우저의 DOM 및 Web API를 자동으로 제공하는 구성 요소가 아니므로 Serox 자체 바인딩 계층이 필요하다.

실제 웹사이트의 스크립트 실행은 격리된 컨텍스트에서 수행하고, Java 내부 객체 및 파일 시스템에 직접 접근할 수 없도록 제한한다.

### 4.8 Event Engine

#### Event Engine 목적

사용자의 입력을 DOM 요소 및 JavaScript 이벤트에 전달한다.

#### 처리 이벤트

- Mouse Move
- Mouse Click
- Mouse Down / Up
- Keyboard Input
- Focus / Blur
- Scroll
- Form Submit
- Link Navigation
- DOM Events

#### 이벤트 흐름

```text
JavaFX Input Event
       ↓
Hit Testing
       ↓
Target Element
       ↓
DOM Event Dispatch
       ↓
JavaScript Handler
       ↓
State Change
       ↓
Layout / Paint Update
```

DOM 이벤트의 Capturing, Target, Bubbling 단계를 지원하도록 설계한다.

---

## 5. Application Core

### 5.1 BrowserController

브라우저 전체 상태를 관리하는 진입점이다.

#### 역할

- 브라우저 시작 및 종료
- 탭 및 창 관리 명령 전달
- 서비스 초기화
- 브라우저 상태 변경 이벤트 전달
- 사용자 프로필 및 설정 연동

### 5.2 TabManager

#### TabManager 역할

- 새 탭 생성
- 탭 닫기
- 탭 전환
- 탭 순서 변경
- 닫힌 탭 복구
- 탭별 문서 및 탐색 상태 관리

각 탭은 독립적인 `TabSession`을 보유한다.

```text
TabSession
 ├── TabId
 ├── NavigationHistory
 ├── CurrentDocument
 ├── DocumentContext
 ├── LoadingState
 ├── ScrollPosition
 └── ProfileReference
```

### 5.3 NavigationManager

#### NavigationManager 역할

- 주소창 입력 처리
- URL 유효성 검사
- 검색어와 URL 구분
- 뒤로 가기
- 앞으로 가기
- 새로고침
- 탐색 중단
- HTTP Redirect 추적

주소창 입력이 검색어로 판단되면 Search Engine에 전달하고, URL이면 Browser Engine에 전달한다.

### 5.4 SessionManager

브라우저가 종료되기 전의 상태를 복원한다.

#### 저장 대상

- 열려 있던 탭
- 탭 순서
- 마지막 활성 탭
- 창 상태
- 각 탭 URL

시크릿 탭은 영구 세션 기록에 포함하지 않는다.

### 5.5 BrowserEventBus

주요 이벤트 예시는 다음과 같다.

```text
TabCreated
TabClosed
TabActivated
NavigationStarted
NavigationCompleted
NavigationFailed
DocumentChanged
PageTitleChanged
SearchStarted
SearchCompleted
AiResponseUpdated
DownloadProgressChanged
```

UI, Storage 및 AI Engine은 필요한 이벤트를 구독하여 상태를 갱신한다.

---

## 6. Search Engine

### 6.1 기본 원칙

Serox는 자체 검색 색인을 구축하지 않고 외부 Search API를 사용한다.

Search API는 웹 검색 결과를 제공하며, HTML 렌더링과 브라우저 탐색은 Serox에서 처리한다.

검색 제공자를 교체할 수 있도록 인터페이스를 분리한다.

```java
public interface SearchProvider {
    CompletableFuture<SearchResponse> search(
        SearchRequest request
    );
}
```

### 6.2 검색 처리 과정

```text
User Query
    ↓
QueryProcessor
    ↓
SearchProvider
    ↓
Search API
    ↓
Search Results
    ↓
Deduplication / Ranking
    ↓
ResultView
```

#### 기본 결과

- 제목
- URL
- 검색 요약문
- 검색 결과 순위
- 제공자 정보

### 6.3 AI 검색 처리

```text
User Query
    ↓
Search API
    ↓
Top N Results
    ↓
PageFetcher
    ↓
ContentExtractor
    ↓
EvidenceStore
    ↓
ContextBuilder
    ↓
Claude API
    ↓
Answer + Citations
    ↓
ResultView
```

검색 결과 본문 수집은 병렬로 진행하고, 개별 웹사이트의 실패가 전체 검색 실패로 이어지지 않도록 한다.

### 6.4 PageFetcher

#### PageFetcher 역할

- 검색 결과 URL 요청
- HTTP 응답 확인
- Redirect 처리
- HTML 문서 획득
- 인코딩 처리
- Timeout 처리
- 비정상 응답 처리

공개 웹페이지 수집에는 Jsoup을 보조적으로 활용한다.

수집이 차단되거나 JavaScript 렌더링이 필요한 경우 실패 원인을 기록하고, 확보 가능한 검색 스니펫만 사용하거나 해당 결과를 제외한다.

### 6.5 ContentExtractor

#### ContentExtractor 역할

- 광고성 요소 제거
- 메뉴와 탐색 영역 제거
- 본문 텍스트 추출
- 제목·소제목 추출
- 중복 문장 제거
- 길이 제한
- AI 입력용 구조화

#### 결과 모델

```text
ExtractedContent
 ├── DocumentId
 ├── URL
 ├── Title
 ├── MainText
 ├── Sections
 ├── CapturedAt
 └── ExtractionStatus
```

### 6.6 ResultRanker

초기에는 검색 제공자의 순위를 기본으로 사용한다.

확장 단계에서는 다음을 고려한다.

- 검색어와 본문 관련성
- 결과 중복 여부
- 콘텐츠 품질
- 원문 접근 가능 여부
- 정보의 최신성
- 출처의 신뢰성

검색 결과 순위와 정보의 사실 여부를 동일하게 취급하지 않는다.

### 6.7 Search Cache

#### Search Cache 목적

동일한 검색어에 대한 반복적인 API 호출을 줄인다.

#### 캐시 데이터

- 정규화된 검색어
- 검색 옵션
- 검색 결과
- 수집 시각
- 만료 시각

검색 결과와 AI 답변은 서로 다른 캐시 정책을 적용한다.

---

## 7. AI Engine

### 7.1 기본 구조

```text
AI Engine
 ├── AiProvider
 │    └── ClaudeProvider
 ├── PromptBuilder
 ├── ContextBuilder
 ├── PageAnalyzer
 ├── SearchSummarizer
 ├── ConversationManager
 ├── AnswerGenerator
 └── CitationManager
```

### 7.2 AiProvider

AI 모델 공급자를 추상화한다.

#### 기본 기능

- AI 요청 전송
- Streaming 응답
- 요청 취소
- Timeout
- API 오류 처리
- 입력 및 출력 사용량 추적
- 모델별 설정

기본 공급자는 Claude API로 정한다.

추후 다른 클라우드 AI 및 로컬 AI 모델을 연결할 수 있도록 한다.

### 7.3 AI 기능 모드

#### Page AI

현재 탭에서 사용자가 허용한 콘텐츠를 분석한다.

기능:

- 현재 페이지 요약
- 특정 문단 설명
- 선택 텍스트 분석
- 기술 문서 설명
- 코드 분석
- 후속 질문

#### Search AI

Search API의 결과를 수집하여 종합한다.

기능:

- 검색 결과 요약
- 여러 출처 비교
- 핵심 사실 정리
- 출처 표시
- 근거 부족 여부 안내
- 후속 질문

#### Multi-tab AI

사용자가 선택한 여러 탭을 분석한다.

기능:

- 문서 간 비교
- 공통점과 차이점 추출
- 여러 자료 종합
- 출처별 주장 분류

Multi-tab AI는 초기 버전 이후 확장 기능으로 구현한다.

### 7.4 ContextBuilder

AI에 전달할 컨텍스트를 구성한다.

#### ContextBuilder 처리 과정

1. 사용자 질문 확인
2. 허용된 콘텐츠 수집
3. 관련 없는 콘텐츠 제거
4. 민감정보 필터링
5. 문서별 출처 ID 부여
6. 입력 길이 제한
7. 필요시 문서 분할
8. AI 요청 메시지 구성

웹페이지에서 얻은 텍스트는 신뢰할 수 없는 외부 데이터로 구분한다.

### 7.5 AnswerGenerator

#### 출력 요구사항

- 질문에 대한 직접적인 답변
- 핵심 내용 요약
- 필요한 경우 항목별 정리
- 관련 출처 인용
- 근거가 부족한 내용 표시
- 서로 충돌하는 정보 구분

AI 응답은 가능한 경우 구조화된 형식으로 처리하여 화면 표시와 출처 연결을 안정적으로 수행한다.

### 7.6 CitationManager

#### CitationManager 목적

AI 답변과 실제 근거 문서를 연결한다.

#### 출처 정보

```text
Citation
 ├── CitationId
 ├── DocumentId
 ├── URL
 ├── Title
 ├── ReferencedText
 └── RetrievedAt
```

AI가 존재하지 않는 출처 ID를 생성할 경우 유효하지 않은 인용으로 처리한다.

단순히 URL이 존재한다는 사실만으로 해당 URL이 AI의 주장을 뒷받침한다고 판단하지 않는다.

출처를 클릭하면 원문을 새 탭 또는 기존 UI에서 제공하는 원문 표시 영역으로 연다.

### 7.7 ConversationManager

#### ConversationManager 역할

- AI 대화 생성
- 메시지 추가
- 대화별 컨텍스트 관리
- 대화 기록 조회
- 대화 삭제
- 스트리밍 상태 관리

대화와 탭의 관계를 별도로 관리하여 탭을 닫더라도 보존 정책에 따라 AI 대화를 유지할 수 있게 한다.

---

## 8. MCP Server

### 8.1 목적

Serox의 검색·분석 기능을 외부 AI 에이전트가 표준화된 도구로 호출하도록 제공한다.

### 8.2 구성

```text
MCP Client
     ↓
MCP Transport
     ↓
SeroxMcpServer
     ↓
ToolDispatcher
     ↓
AccessController
     ↓
Application Services
```

### 8.3 MCP 도구

| 도구 | 설명 | 우선순위 |
| --- | --- | --- |
| web_search | 웹 검색 결과 반환 | 필수 |
| search_and_summarize | 검색 및 AI 요약 | 필수 |
| fetch_page | 공개 페이지 본문 수집 | 필수 |
| list_tabs | 열린 탭 목록 조회 | 확장 |
| read_tab | 허용된 탭 콘텐츠 조회 | 확장 |
| navigate_tab | 특정 탭 URL 변경 | 확장 |
| create_tab | 새 탭 생성 | 확장 |

### 8.4 search_and_summarize

#### 입력 예시

```json
{
  "query": "Java Virtual Threads",
  "maxResults": 5,
  "includeSources": true
}
```

#### 출력 예시

```json
{
  "query": "Java Virtual Threads",
  "answer": "검색 결과를 종합한 AI 답변",
  "sources": [
    {
      "id": "source-1",
      "title": "문서 제목",
      "url": "https://example.com/article"
    }
  ]
}
```

위 URL과 응답 데이터는 구조를 설명하기 위한 예시이며 실제 검색 결과가 아니다.

### 8.5 보안

- 초기 전송 방식은 로컬 STDIO를 기본으로 한다.
- 외부 네트워크 공개는 기본적으로 비활성화한다.
- 도구별 권한을 분리한다.
- 사용자 동의 없이 민감한 탭을 읽지 않는다.
- 웹페이지가 MCP 도구를 직접 호출할 수 없도록 한다.
- 요청 취소 및 리소스 제한을 지원한다.
- 실행 결과와 오류를 호출자에게 명확하게 전달한다.

---

## 9. UI 연동 구조

### 9.1 기본 원칙

Serox의 UI 디자인은 이미 별도로 정해져 있으므로 본 명세서에서는 디자인을 변경하지 않는다.

UI는 Application Core의 공개 API와 이벤트를 통해 동작한다.

### 9.2 UI 담당 기능

- 브라우저 창
- 주소창
- 검색 입력
- 탭
- 웹페이지 렌더링 영역
- 일반 검색 결과
- AI 검색 결과
- 출처 링크
- AI 대화 영역
- 다운로드 상태
- 설정

### 9.3 화면 갱신

```text
User Action
     ↓
UI Controller
     ↓
Application Core
     ↓
Service / Engine
     ↓
Application Event
     ↓
UI State Update
```

JavaFX Application Thread에서는 UI 갱신만 수행한다.

네트워크 및 AI 처리 결과는 적절한 스레드 전환을 거쳐 UI에 반영한다.

### 9.4 작업 상태

검색, 페이지 이동 및 AI 응답에는 다음 상태를 적용한다.

```text
IDLE
LOADING
SUCCESS
FAILED
CANCELLED
```

이전 요청의 결과가 최신 요청의 UI 상태를 덮어쓰지 않도록 요청 식별자를 사용한다.

---

## 10. Storage 설계

### 10.1 SQLite 테이블

#### history

```text
id
url
title
visited_at
```

#### bookmarks

```text
id
url
title
folder_id
created_at
```

#### sessions

```text
id
profile_id
created_at
updated_at
```

#### tabs

```text
id
session_id
url
position
active
```

#### settings

```text
key
value
```

#### conversations

```text
id
title
created_at
updated_at
```

#### messages

```text
id
conversation_id
role
content
created_at
```

#### search_cache

```text
cache_key
response
created_at
expires_at
```

### 10.2 저장 정책

- 방문 기록은 일반 모드에서 저장한다.
- 북마크는 사용자 요청에 따라 보관한다.
- AI 대화는 설정된 저장 정책에 따라 보관한다.
- 검색 캐시는 만료 시간을 설정한다.
- 시크릿 모드의 방문 기록과 AI 대화는 영구 저장하지 않는다.
- API Key는 운영체제 Credential Store를 우선 사용한다.
- 쿠키와 사이트별 저장 데이터는 프로필별로 분리한다.

---

## 11. 보안 설계

### 11.1 웹사이트 격리

웹사이트에서 실행된 코드가 Java 애플리케이션 내부 권한을 획득하지 못하도록 제한한다.

DOM 및 JavaScript 런타임에는 필요한 기능만 노출한다.

장기적으로 탭별 프로세스 격리와 OS 수준 샌드박싱을 검토한다.

단, JVM 내부의 실행 컨텍스트 분리만으로 악성 웹사이트에 대한 강한 보안 격리가 보장되는 것은 아니다.

### 11.2 네트워크 보안

- HTTPS 인증서 검증
- Redirect 정책
- 안전하지 않은 URL Scheme 제한
- 파일 접근 권한 제한
- 다운로드 경로 검증
- 사이트별 쿠키 격리
- Same-Origin Policy
- CORS 및 관련 웹 보안 정책의 단계적 구현
- 검색 수집에서 사설 IP 및 내부 네트워크 접근 제한

### 11.3 AI 보안

- 사용자 동의 없는 민감정보 수집 제한
- Prompt Injection 대응
- 외부 페이지 내용과 시스템 지시 분리
- AI 도구 실행 권한 제한
- AI의 자동 탐색 및 외부 전송 제어
- API Key 보호

### 11.4 MCP 보안

- 허용된 클라이언트만 접근
- 도구별 권한 적용
- 브라우저 내부 서비스 직접 노출 금지
- 요청 데이터 검증
- 민감정보 반환 제한

### 11.5 초기 버전 보안 제한

독립적인 웹 엔진은 충분한 보안 검증 이전에는 일반적인 상용 브라우저와 동일한 안전성을 보장할 수 없다.

따라서 초기 테스트 버전에서는 실사용 계정 로그인, 금융 거래, 민감정보 입력 등 보안 위험이 높은 사용을 공식 지원 범위에서 제외한다.

---

## 12. 오류 처리

### 12.1 네트워크 오류

| 상황 | 처리 |
| --- | --- |
| DNS 실패 | 오류 페이지 |
| 연결 시간 초과 | 재시도 또는 중단 |
| TLS 인증서 오류 | 탐색 차단 및 사유 표시 |
| HTTP 오류 | 상태 코드에 맞는 처리 |
| Redirect 반복 | 탐색 중단 |
| 응답 크기 초과 | 리소스 제한 및 오류 반환 |

### 12.2 렌더링 오류

- HTML 파싱 실패 시 복구 가능한 범위에서 렌더링
- 지원하지 않는 CSS 속성은 무시하고 기록
- 이미지 로딩 실패 시 대체 표시
- JavaScript 실행 실패 시 해당 문서 컨텍스트에 오류 기록
- 렌더링 작업 실패 시 가능한 경우 탭 단위로 오류 격리

### 12.3 검색 오류

- Search API 인증 실패
- API 호출 제한
- 검색 결과 없음
- 페이지 본문 추출 실패
- 크롤링 차단
- 부분 검색 결과만 수집된 상황

페이지 수집이 일부 실패하더라도 확보된 검색 결과는 유지한다.

### 12.4 AI 오류

- API Key 오류
- Rate Limit
- API 서버 오류
- 응답 지연
- 입력 길이 초과
- 출력 파싱 실패
- 사용자 취소

AI 실패는 일반 브라우징 및 일반 검색에 영향을 주지 않아야 한다.

### 12.5 오류 기록

문제 해결에는 예외 로그, 테스트 케이스, 공식 문서 및 Stack Overflow를 활용한다.

오류 로그에는 API Key, Cookie, 인증 토큰 등의 민감정보를 남기지 않는다.

---

## 13. 비기능 요구사항

### 13.1 성능

- UI 스레드 차단 방지
- 네트워크 요청 비동기화
- AI 응답 스트리밍
- 불필요한 재렌더링 최소화
- 이미지 및 리소스 캐싱
- 검색 결과 수집 병렬화
- 작업 취소 지원

### 13.2 안정성

- 탭별 상태 격리
- 오류 전파 최소화
- 세션 복원
- 비정상 종료 복구
- 명확한 리소스 해제
- 백그라운드 작업 종료 처리

### 13.3 확장성

- Search Provider 교체 가능
- AI Provider 교체 가능
- 렌더링 파이프라인 단계별 개선
- MCP Tool 추가 가능
- Storage 구현 교체 가능
- UI와 엔진 독립성 유지

### 13.4 플랫폼

초기에는 JavaFX 개발 및 실행이 안정적인 단일 데스크톱 운영체제를 대상으로 한다.

Windows, macOS 및 Linux 지원은 플랫폼별 입력·글꼴·그래픽·배포 테스트를 진행한 후 확대한다.

---

## 14. 디렉터리 구조

```text
Serox/
├── pom.xml
├── README.md
├── docs/
│   ├── architecture.md
│   ├── specifications.md
│   ├── browser-engine.md
│   ├── search-engine.md
│   ├── ai-engine.md
│   └── mcp.md
│
├── serox-app/
│   └── src/main/java/
│
├── serox-ui/
│   └── src/main/
│       ├── java/
│       └── resources/
│
├── serox-core/
│   └── src/main/java/
│       ├── browser/
│       ├── tabs/
│       ├── navigation/
│       ├── session/
│       └── events/
│
├── serox-network/
│   └── src/main/java/
│       ├── http/
│       ├── cookies/
│       ├── cache/
│       └── download/
│
├── serox-engine/
│   └── src/main/java/
│       ├── html/
│       ├── dom/
│       ├── css/
│       ├── layout/
│       ├── rendering/
│       ├── javascript/
│       └── events/
│
├── serox-search/
│   └── src/main/java/
│       ├── provider/
│       ├── fetch/
│       ├── extract/
│       ├── ranking/
│       └── evidence/
│
├── serox-ai/
│   └── src/main/java/
│       ├── provider/
│       ├── context/
│       ├── analysis/
│       ├── conversation/
│       └── citation/
│
├── serox-mcp/
│   └── src/main/java/
│       ├── server/
│       ├── security/
│       └── tools/
│
├── serox-storage/
│   └── src/main/java/
│       ├── database/
│       ├── history/
│       ├── bookmarks/
│       ├── session/
│       └── settings/
│
└── serox-common/
    └── src/main/java/
        ├── model/
        ├── exception/
        └── event/
```

Maven 멀티 모듈 구성으로 관리한다.

공통 인터페이스와 데이터 모델의 순환 의존성이 발생하지 않도록 모듈 의존 방향을 제한한다.

---

## 15. 주요 동작 시나리오

### 15.1 일반 웹사이트 방문

1. 사용자가 URL을 입력한다.
2. NavigationManager가 URL을 해석한다.
3. Network Engine이 서버에 요청한다.
4. HTML 문서를 수신한다.
5. HTML Parser가 DOM을 생성한다.
6. CSS Engine이 스타일을 계산한다.
7. Layout Engine이 요소 위치를 결정한다.
8. Rendering Engine이 페이지를 그린다.
9. 지원되는 JavaScript를 실행한다.
10. 사용자 입력 및 페이지 이벤트를 처리한다.

### 15.2 일반 검색

1. 사용자가 주소창에 검색어를 입력한다.
2. NavigationManager가 검색어로 분류한다.
3. SearchProvider가 Search API를 호출한다.
4. 검색 결과를 수신한다.
5. 중복 결과를 정리한다.
6. UI에 제목, URL, 요약문을 표시한다.
7. 사용자가 결과를 선택하면 해당 URL로 탐색한다.

### 15.3 AI 검색

1. 사용자가 검색어를 입력한다.
2. Search API로 상위 N개 결과를 가져온다.
3. PageFetcher가 각 URL의 본문을 수집한다.
4. ContentExtractor가 본문을 정제한다.
5. ContextBuilder가 AI 입력을 구성한다.
6. Claude API에 요청한다.
7. AI 응답을 스트리밍으로 수신한다.
8. CitationManager가 출처 ID를 검증한다.
9. 답변과 출처 링크를 표시한다.
10. 출처 클릭 시 원문을 연다.

### 15.4 현재 페이지 AI 분석

1. 사용자가 현재 페이지 분석을 요청한다.
2. 사용자의 허용 범위를 확인한다.
3. DOM에서 본문을 추출한다.
4. 민감정보 및 불필요한 내용을 제거한다.
5. AI Engine에 전달한다.
6. Claude API에서 답변을 받는다.
7. 기존 UI의 AI 표시 영역에 결과를 출력한다.

### 15.5 MCP 검색 요청

1. 외부 AI 에이전트가 Serox MCP 서버에 연결한다.
2. `search_and_summarize` 도구를 호출한다.
3. ToolDispatcher가 요청을 검증한다.
4. Search Engine이 검색과 본문 수집을 진행한다.
5. AI Engine이 요약을 생성한다.
6. 출처를 포함한 구조화된 응답을 반환한다.

---

## 16. 개발 계획

### 16.1 학교 수행평가 일정 — 3주

기존 프로젝트 계획서의 3주 구성을 유지하되, 독자적인 브라우저 엔진 구현에 맞추어 최소 동작 범위를 제한한다.

#### 1주차

- Maven 및 JavaFX 개발 환경 구성
- 기존 UI 프로젝트 연결
- Network Engine 기본 구현
- HTML 다운로드
- 기본 HTML Tokenizer 및 DOM 구축
- 텍스트와 링크 표시
- 주소창과 탐색 기능
- Search API 연동
- 상위 검색 결과 목록 표시

#### 2주차

- 기본 CSS 처리
- Block 및 Inline 레이아웃
- 이미지 표시
- Jsoup 기반 검색 대상 본문 추출
- Claude API 연동
- Prompt 설계
- AI 검색 결과 표시
- 출처 링크 연결
- 페이지 분석 기능

#### 3주차

- MCP Server 구현
- `search_and_summarize` 도구 등록
- 외부 AI 에이전트 호출 테스트
- 검색 기록
- 오류 처리
- 기본 캐싱
- 성능 및 기능 테스트
- 최종 점검
- 발표 자료 준비

이 일정에서 구현하는 브라우저는 제한된 HTML/CSS를 지원하는 초기 프로토타입이며, 최신 상용 웹사이트 전체와의 호환성을 목표로 하지
않는다.

### 16.2 장기 개발 로드맵

#### Phase 1 — Browser Foundation

- 네트워크 기능
- HTML Parser
- DOM Engine
- 기본 렌더링
- 탭 및 탐색

#### Phase 2 — Styling & Layout

- CSS Parser
- Cascade
- Box Model
- Text Layout
- Positioning
- Flexbox 및 Grid의 단계적 구현

#### Phase 3 — AI Search

- Search Provider
- PageFetcher
- ContentExtractor
- Claude Provider
- Citation System
- AI Conversation

#### Phase 4 — JavaScript Support

- JavaScript Runtime
- DOM Bindings
- Event Loop
- Browser API
- 동적 페이지 대응

#### Phase 5 — MCP & Automation

- MCP Server
- Search Tools
- Page Tools
- 브라우저 제어 도구
- 사용자 승인 및 권한 관리

#### Phase 6 — Browser Compatibility

- 웹 표준 호환성 확대
- 브라우저 프로필
- 시크릿 모드
- Cookie 및 Storage 정책
- 보안 강화
- 성능 최적화
- 플랫폼별 패키징
- 업데이트 시스템

---

## 17. 테스트 및 검증

### 17.1 단위 테스트

- URL 파싱
- HTTP 응답 처리
- HTML Tokenizer
- DOM Tree 생성
- CSS Selector
- Style Resolver
- Layout 계산
- Search Response 파싱
- AI 응답 파싱
- Citation 검증

### 17.2 통합 테스트

- URL 입력부터 렌더링까지
- 검색어 입력부터 결과 표시까지
- AI 검색 전체 파이프라인
- 페이지 분석
- MCP 도구 호출
- 탭 복원
- 설정 저장

### 17.3 시각적 테스트

정해진 HTML/CSS 테스트 문서를 렌더링하고 예상 출력과 비교한다.

#### 검사 항목

- 글꼴 및 줄바꿈
- 요소 크기
- Margin/Padding
- 배경색
- 테두리
- 이미지 배치
- 스크롤
- 요소 겹침
- Viewport 크기 변경

### 17.4 보안 테스트

- 잘못된 인증서
- 악성 URL
- Redirect Loop
- 과도하게 큰 응답
- 비정상 HTML/CSS
- JavaScript 무한 반복
- Prompt Injection
- MCP 권한 우회
- 비인가 로컬 파일 접근

### 17.5 완료 기준

#### 수행평가 프로토타입

- JavaFX 애플리케이션이 실행된다.
- URL 입력으로 지원 범위 내 HTML 페이지를 열 수 있다.
- 기본 텍스트·링크·이미지를 표시할 수 있다.
- Search API 검색이 작동한다.
- Claude API로 검색 결과를 요약할 수 있다.
- 출처 링크를 제공한다.
- MCP 검색 도구를 호출할 수 있다.
- 주요 오류가 애플리케이션 전체 종료로 이어지지 않는다.

#### 범용 브라우저 정식 버전

- 주요 웹 표준의 정의된 지원 범위를 충족한다.
- 동적 웹페이지가 작동한다.
- 안전한 사이트 격리와 웹 보안 정책을 제공한다.
- 브라우저 프로필과 세션을 안정적으로 관리한다.
- 장기간 사용해도 메모리 및 리소스 누수가 관리된다.
- 플랫폼별 배포와 업데이트가 가능하다.

정식 버전은 별도의 호환성 및 보안 검증을 통과해야 한다.

---

## 18. 프로젝트 위험 요소

| 위험 | 영향 | 대응 |
| --- | --- | --- |
| HTML 표준 복잡성 | DOM 구조 오류 | 단계적 표준 구현 및 테스트 |
| CSS 레이아웃 복잡성 | 화면 표시 오류 | 기능별 레이아웃 엔진 분리 |
| JavaScript 호환성 | 동적 사이트 미작동 | 런타임과 Web API 분리 |
| 네트워크 오류 | 탐색 실패 | Timeout 및 재시도 정책 |
| 수집 차단 | AI 검색 결과 부족 | 부분 결과 제공 |
| 긴 문서 | AI 입력 제한 초과 | 본문 분할 및 정제 |
| AI 비용 | 운영 비용 증가 | 캐싱 및 사용량 제한 |
| Prompt Injection | AI 오작동 | 외부 데이터 신뢰 경계 |
| MCP 권한 오용 | 브라우저 정보 노출 | 권한 검증 |
| 브라우저 보안 취약점 | 사용자 데이터 위험 | 격리, 테스트, 공개 범위 제한 |
| 3주 개발 일정 | 전체 기능 미완성 | MVP 범위 명확화 |

---

## 19. 미확정 세부 사항

아래 항목은 현재 프로젝트에서 아직 구체적인 구현 선택이 확정되지 않은 사항이다.

1. 구체적인 Search API 제공자
2. JavaScript 런타임의 최종 채택 방식
3. 개발 우선 대상 운영체제
4. 실제 UI 코드와 각 컨트롤의 연결 방식
5. 독립 엔진의 첫 번째 웹 표준 지원 범위
6. 브라우저 프로세스 격리 및 샌드박스 구조
7. 검색·AI 사용량 제한과 운영 예산
8. 정식 배포 방식 및 자동 업데이트 정책

이 항목들은 구현 시작 시 기술 검증과 테스트를 통해 확정한다.

---

## 20. 최종 설계 결론

Serox는 **독자적인 웹 렌더링 엔진을 기반으로 한 Java 범용 웹 브라우저**이며, 외부 Search API와 Claude API를
결합하여 일반 검색과 AI 검색을 동시에 제공한다.

브라우저의 기본 기능은 AI 서비스에 종속되지 않는다.

HTML, CSS, DOM, Layout, Rendering, Navigation 및 Browser State를 독립적인 모듈로 구성하며,
Search Engine과 AI Engine은 이 구조 위에서 확장 기능을 제공한다.

MCP 서버는 검색·요약·콘텐츠 조회 기능을 외부 AI 에이전트에 연결하는 독립 인터페이스로 작동한다.

초기 3주 개발에서는 HTML/CSS의 제한된 부분집합과 AI 검색, MCP 검색 도구를 갖춘 실행 가능한 프로토타입을 목표로 한다.

장기적으로는 JavaScript, Web API, 보안 격리 및 웹 표준 호환성을 확장하여 실질적인 범용 웹 브라우저로 발전시킨다.

**최종 아키텍처 방향:**

JavaFX Application + Independent Browser Engine + External Search API + Claude
AI + MCP Server + SQLite Storage

**최종 목표:**

기존 브라우저 엔진에 의존하지 않으면서 일반 웹 탐색과 근거 기반 AI 검색을 하나의 통합 환경에서 제공하는 독립형 AI-Native Web
Browser, Serox를 개발한다.
