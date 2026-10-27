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

현재 골격은 브라우저 탭/도구 모음, 홈 검색 UI, 최근 검색 및 연결 상태 카드,
하단 상태 표시줄을 제공한다. 실제 탐색, 검색 API, AI, MCP와 저장소 기능은
아직 연결하지 않았다.

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

제품 UI의 기준은 [Serox Figma 디자인](https://www.figma.com/design/IZiradnUF4Q4Ok0XGeTtPy/Serox?node-id=30-3&t=3KCOPM8O9iPz75EA-1)이다. 화면별 기능과 구현 순서는 [STEP.md](STEP.md)에 연결되어 있다.

각 `### STEP n`마다 전용 이슈·브랜치·PR을 만들고, STEP 안의 각 번호 작업 단위는 하나의 커밋으로 남긴다. 커밋 메시지는 `type: lowercase summary` 형식을 사용한다. 각 STEP PR은 GitHub에서 **Create a merge commit**으로 합친다.
