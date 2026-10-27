# Serox

Serox : AI Searching Browser

## JavaFX 시작하기

필요 환경: JDK 25 이상과 Maven 3.9 이상.

프로젝트 루트에서 아래 명령으로 JavaFX 앱을 실행한다.

```powershell
mvn clean javafx:run
```

`pom.xml`이 JavaFX 의존성과 현재 플랫폼의 네이티브 라이브러리를 함께 내려받는다.
별도의 JavaFX SDK 설치는 필요하지 않다.

초기 프로젝트는 단일 Maven 모듈로 시작한다. 모듈 분리 기준은
[ADR 0001](docs/decisions/0001-maven-module-strategy.md)에 기록한다.

## Windows 배포 산출물

최종 배포는 자체 실행 런타임을 포함한 MSI 설치 파일을 기본으로 한다.
설치 후 시작 메뉴와 바탕 화면에서 `Serox.exe`를 실행할 수 있다.

필요 환경은 Windows, JDK 25 이상, Maven 3.9 이상이다. `jpackage`는 JDK에 포함되며,
JavaFX 런타임은 앱 패키지에 포함된다. MSI 또는 EXE 설치 파일을 만들기 위한 WiX
Toolset 3.14.1은 아래 스크립트로 사용자 로컬 도구 경로에 준비한다. 관리자 권한이나
시스템 PATH 변경 없이 실행할 수 있다. 스크립트는 공식 [WiX 3.14.1 바이너리 배포물]
을 내려받아 SHA-256을 확인한다.

```powershell
.\scripts\prepare-wix.ps1
```

[WiX 3.14.1 바이너리 배포물]: https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm

```powershell
# Serox.exe와 런타임이 들어 있는 실행 폴더
.\scripts\package-windows.ps1 -Type app-image

# 기본 설치 파일
.\scripts\package-windows.ps1 -Type msi

# 선택 사항: EXE 설치 파일
.\scripts\package-windows.ps1 -Type exe
```

산출물은 `dist` 아래에 생성된다. `app-image`는 `Serox.exe`를 포함하는 실행 폴더이고,
`msi`는 Windows Installer 파일이다. 패키지 버전은 `pom.xml`의
`app.release.version` 값을 사용한다. 각 패키징은 Windows에서 실행해야 한다.

설치·실행·데이터 위치는 [플랫폼 지원](docs/release/platform-support.md), 번들 런타임
확인 결과는 [런타임 번들 점검](docs/release/runtime-bundle.md)을 참고한다.

## 현재 상태 (릴리스 후보 0.1.0)

Serox는 독자 HTML/CSS 렌더러로 지원 범위의 페이지를 열고, 탭 이동, 일반 검색,
AI 요약과 출처, 설정·북마크·방문 기록·세션 복구, MCP 검색 도구를 제공한다.
각 단계의 완료 기준 대조 결과는 [완료 체크리스트](docs/release/completion-checklist.md)에 있다.

## API 설정

검색과 AI 기능은 사용자가 직접 발급한 키가 있을 때만 켜진다. 키는 환경 변수로만 읽으며
어디에도 저장하지 않는다. 키가 없어도 주소 입력, 탭, 북마크 등 일반 탐색은 동작한다.

```powershell
$env:SEROX_BRAVE_SEARCH_API_KEY = "<Brave Search API key>"
$env:SEROX_ANTHROPIC_API_KEY = "<Anthropic API key>"
.\dist\Serox\Serox.exe
```

- 데이터 폴더: `%LOCALAPPDATA%\Serox` (`SEROX_DATA_DIR`로 변경 가능)
- 설정 화면의 연결 상태 배지가 키와 저장소의 실제 상태를 보여 준다.

## 보안 안내

- AI·MCP가 읽는 검색 결과 페이지는 공개 웹만 허용한다. localhost, 사설망, 링크 로컬 주소와
  리다이렉트 우회를 차단한다. 자세한 내용은 [네트워크 정책](docs/security/network-policy.md).
- 검색 결과와 페이지 본문은 신뢰하지 않는 데이터로 다룬다. 태그와 제어·방향 문자를 제거하고,
  이메일·토큰·카드번호 등은 AI 제공자로 보내기 전에 가린다.
- MCP 도구는 사용자가 환경 변수로 승인한 것만 실행되며, 호출 횟수가 제한된다.
- 로그에는 키, 쿠키, 응답 본문, 예외 상세를 남기지 않는다. [보안 안내](docs/SECURITY.md).
- 설치 파일은 아직 서명되지 않았다. 첫 실행 때 Windows SmartScreen 경고가 나올 수 있다.

## 알려진 제한

- JavaScript, flexbox, grid, 위치 지정, 미디어 쿼리는 지원하지 않는다.
  이미지는 대체 텍스트가 있는 자리 표시자로 그려진다.
  [렌더링 지원 범위](docs/rendering-support.md).
- 검색은 Brave Search, AI는 Claude 하나만 지원한다. 두 연동은 로컬 가짜 서버로만 검증했고
  실제 키로 호출해 본 적은 없다.
- 지원 운영체제는 Windows뿐이다. 사용자 단위 설치판의 설치·실행·제거는 확인했지만, 배포용 MSI(관리자 권한 필요)는 설치해 보지 못했다.
- 페이지 읽기에서 DNS 재바인딩은 완전히 차단하지 못한다(응답 크기·형식 제한과 민감정보
  가림으로 완화).
- Figma 대조는 홈, 검색 결과, 디자인 설정 화면만 했다. 오른쪽 원문 미리보기(저장된 발췌만 보여 줌)와 설정의 나머지 페이지 조작 항목은 디자인과 다르다. [Figma 대조](docs/release/figma-comparison.md).

## 로드맵

장기 항목은 MVP 완료 기준과 분리해 별도 이슈로 관리한다.

1. 깨끗한 Windows에서 MSI 설치·업그레이드·제거 확인과 코드 서명
2. 실제 API 키를 사용한 검색·AI 통합 점검과 Figma 대조
3. CSS 확장(flexbox, 위치 지정)과 이미지 로딩
4. JavaScript 런타임과 DOM 바인딩
5. 추가 검색·AI 제공자, macOS·Linux 패키지

### 소스 구조

- `kr.soyuo.serox.app`: JavaFX 애플리케이션 진입점
- `kr.soyuo.serox.ui`: 브라우저 껍데기와 홈 화면
- `src/main/resources/kr/soyuo/serox/ui`: JavaFX CSS

UI는 이후 `STEP.md`의 모듈 구조에 맞춰 애플리케이션 코어와 브라우저 엔진으로
분리해 나간다.

API 키와 로그 처리 규칙은 [보안 안내](docs/SECURITY.md)를 따른다. 실제 키를
저장소, 명령행 인수, 이슈, PR 또는 로그에 넣지 않는다.

도구 참고: [OpenJFX Maven 안내](https://openjfx.io/openjfx-docs/#maven),
[JDK 25 jpackage 안내](https://docs.oracle.com/en/java/javase/25/jpackage/index.html).

## 코드 품질 명령

JDK 25와 Maven 3.9 이상을 설치한 뒤 프로젝트 루트에서 실행한다.

```powershell
# Google Java Format 적용
mvn spotless:apply

# 포맷 검사 및 Checkstyle 정적 분석
mvn spotless:check
mvn checkstyle:check

# 테스트와 전체 검증
mvn test
mvn verify
```

`mvn verify`는 Checkstyle을 실행하고 컴파일 및 테스트까지 검증한다. 아직 테스트 코드가
없는 단계에서는 테스트 수가 0일 수 있으며, 기능을 추가할 때 단위 테스트를 함께 추가한다.

## 디자인 및 작업 흐름

제품 UI의 기준은 [Serox Figma 디자인](https://www.figma.com/design/IZiradnUF4Q4Ok0XGeTtPy/Serox?node-id=30-3&t=3KCOPM8O9iPz75EA-1)이다.
화면별 기능과 구현 순서는 [STEP.md](STEP.md)에 연결되어 있다.

각 `### STEP n`마다 전용 이슈·브랜치·PR을 만들고, 번호가 붙은 각 작업 단위는
하나의 커밋으로 남긴다. 커밋 메시지는 `type: lowercase summary` 형식을 사용한다.
각 STEP PR은 GitHub에서 **Create a merge commit**으로 합친다.

## MCP 서버 (외부 AI 에이전트 연동)

Serox는 stdio 기반 MCP 서버를 제공한다. 에이전트가 Serox를 직접 실행하며, 도구는
`search_and_summarize` 하나만 노출한다. 설계 근거는
[0004-mcp-transport.md](docs/decisions/0004-mcp-transport.md)를 참고한다.

- 창 없이 실행: `Serox --mcp-headless`
- 창과 함께 실행(하단 상태 표시줄에 MCP 연결 상태 표시): `Serox --mcp`
- 사용자 승인: 에이전트 설정의 환경 변수에 허용할 도구를 적어야 한다.
  `SEROX_MCP_APPROVED_TOOLS=search_and_summarize` — 값이 없으면 세션이 거부된다.
- 검색·요약에는 `SEROX_BRAVE_SEARCH_API_KEY`와 `SEROX_ANTHROPIC_API_KEY`가 필요하다.
- 호출은 동시 2건, 분당 20건으로 제한되고, 브라우저 제어·로컬 파일·방문 기록에 접근하는
  도구는 존재하지 않는다.
