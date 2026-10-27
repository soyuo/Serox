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

필요 환경은 Windows, JDK 25 이상, Maven 3.9 이상이다. MSI 또는 EXE 설치 파일을
만들려면 WiX Toolset 3.x의 `candle.exe`와 `light.exe`가 PATH에 있어야 한다.
`jpackage`는 JDK에 포함되며, JavaFX 런타임은 앱 패키지에 포함된다.

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

도구 참고: [OpenJFX Maven 안내](https://openjfx.io/openjfx-docs/#maven),
[JDK 25 jpackage 안내](https://docs.oracle.com/en/java/javase/25/jpackage/index.html).
