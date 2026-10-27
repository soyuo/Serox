package kr.soyuo.serox.ui;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.ai.application.AiConnectionService;
import kr.soyuo.serox.ai.auth.ChatGptAccounts;
import kr.soyuo.serox.core.model.AiProviderId;

/** Figma provider cards and connection methods backed by real credentials and browser callbacks. */
final class AiProviderPage extends ScrollPane {
  private final AiConnectionService service;
  private final Consumer<URI> openBrowser;
  private final VBox content = SettingsControls.content("AI 제공자", "요약에 사용할 AI와 연결 방법을 골라요.");
  private final VBox connection = SettingsControls.card();
  private final Label message = SettingsControls.label("", "search-state-message");
  private AiProviderId selected;
  private CompletableFuture<?> operation;
  private boolean busy;
  private boolean browserLogin;

  AiProviderPage(AiConnectionService service, Consumer<URI> openBrowser) {
    this.service = service;
    this.openBrowser = openBrowser;
    selected = service.snapshot().provider();
    setAccessibleText("AI 제공자 설정");
    SettingsControls.configure(this, content);
    render();
    sceneProperty()
        .addListener(
            (observable, oldScene, scene) -> {
              if (oldScene != null && scene == null && browserLogin) {
                service.cancelSignIn();
              }
            });
  }

  private void render() {
    FlowPane choices = new FlowPane(UiTokens.SPACE_MD, UiTokens.SPACE_MD);
    ToggleGroup group = new ToggleGroup();
    for (AiProviderId id : AiProviderId.values()) {
      String vendor =
          switch (id) {
            case CLAUDE -> "Anthropic";
            case GEMINI -> "Google";
            case CODEX -> "OpenAI";
            case GROK -> "xAI";
          };
      ToggleButton choice = new ToggleButton();
      javafx.scene.layout.Region radio = new javafx.scene.layout.Region();
      radio.getStyleClass().add("provider-radio");
      VBox name =
          new VBox(
              4,
              SettingsControls.label(id.label(), "settings-row-title"),
              SettingsControls.label(vendor, "provider-vendor"));
      javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      Label state = SettingsControls.label(connected(id) ? "연결됨" : "연결 안 됨", "provider-status");
      HBox graphic = new HBox(10, radio, name, spacer, state);
      graphic.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
      graphic.prefWidthProperty().bind(choice.widthProperty().subtract(34));
      choice.setGraphic(graphic);
      choice.getStyleClass().add("provider-card");
      choice.setToggleGroup(group);
      choice.setSelected(id == selected);
      choice.setAccessibleText(id.label() + " 제공자 선택");
      choice.setDisable(busy);
      choice.prefWidthProperty().bind(cardWidth(choices));
      choice.setOnAction(
          event -> {
            selected = id;
            render();
          });
      choices.getChildren().add(choice);
    }
    Button custom = new Button("다른 제공자 추가\nOpenAI 호환 주소 입력");
    custom.setGraphic(SvgIconView.icon(UiTokens.ICON_ADD_PROVIDER, 18));
    custom.getStyleClass().add("provider-card");
    custom.setDisable(true);
    custom.setAccessibleHelp("사용자 지정 제공자는 아직 지원하지 않아요.");
    custom.prefWidthProperty().bind(cardWidth(choices));
    choices.getChildren().add(custom);
    message.setText(service.snapshot().message());
    content
        .getChildren()
        .setAll(
            SettingsControls.label("AI 제공자", "settings-page-title"),
            SettingsControls.label("요약에 사용할 AI와 연결 방법을 골라요.", "search-state-message"),
            choices,
            connection,
            message);
    connection.getChildren().clear();
    VBox fields = new VBox(20);
    fields.getStyleClass().add("connection-content");
    fields.setDisable(busy);
    fields
        .getChildren()
        .add(SettingsControls.label(selected.label() + " 연결", "search-section-heading"));
    fields.getChildren().add(browserMethod());
    fields.getChildren().add(SettingsControls.label("또는", "search-state-message"));
    PasswordField key = new PasswordField();
    key.setPromptText("API 키를 입력하세요");
    key.setAccessibleText(selected.label() + " API 키");
    TextField model =
        new TextField(
            service.snapshot().provider() == selected && !service.snapshot().browser()
                ? service.snapshot().model()
                : selected.defaultModel());
    model.setAccessibleText(selected.label() + " 모델 ID");
    model.setPromptText("모델 ID");
    Button test = new Button("연결 테스트");
    test.getStyleClass().add("connection-secondary");
    test.setOnAction(
        event -> {
          String secret = key.getText();
          key.clear();
          run(service.connectKey(selected, model.getText().strip(), secret));
        });
    HBox keyRow = new HBox(12, key, test);
    HBox.setHgrow(key, Priority.ALWAYS);
    fields
        .getChildren()
        .addAll(
            SettingsControls.label("방법 2 · API 키", "settings-row-title"),
            keyRow,
            SettingsControls.row("모델", "제공자의 모델 ID를 입력하세요.", model));
    Button useKey = new Button("저장된 API 키 사용");
    useKey.getStyleClass().add("secondary-button");
    useKey.setDisable(!service.snapshot().configured().contains(selected));
    useKey.setOnAction(event -> run(service.selectKey(selected, model.getText().strip())));
    fields
        .getChildren()
        .addAll(
            useKey,
            SettingsControls.label(
                "API 키와 로그인 토큰은 이 컴퓨터의 Windows 사용자 계정으로 암호화해 저장해요.", "search-state-message"));
    connection.getChildren().add(fields);
    if (busy && browserLogin) {
      Button cancel = new Button("로그인 취소");
      cancel.getStyleClass().add("secondary-button");
      cancel.setOnAction(event -> service.cancelSignIn());
      connection.getChildren().add(cancel);
    }
  }

  private static javafx.beans.binding.DoubleBinding cardWidth(FlowPane pane) {
    return javafx.beans.binding.Bindings.createDoubleBinding(
        () -> {
          double width = pane.getWidth();
          return Math.floor(
              width < 650 ? Math.max(0, width) : width < 950 ? (width - 12) / 2 : (width - 24) / 3);
        },
        pane.widthProperty());
  }

  private VBox browserMethod() {
    VBox browser = new VBox(12);
    browser.getChildren().add(SettingsControls.label("방법 1 · 브라우저 로그인", "settings-row-title"));
    if (selected != AiProviderId.CODEX) {
      String explanation =
          switch (selected) {
            case CLAUDE -> "Claude는 타사 앱의 Claude.ai 계정 로그인을 제공하지 않아요. API 키로 연결하세요.";
            case GEMINI -> "Google 계정 로그인은 Serox용 OAuth 클라이언트 등록이 필요해요. 현재는 Gemini API 키로 연결하세요.";
            case GROK -> "Grok API는 API 키로 연결해요. Grok 웹사이트 로그인은 API 연결을 승인하지 않아요.";
            default -> "";
          };
      browser.getChildren().add(SettingsControls.label(explanation, "search-state-message"));
      return browser;
    }
    Button login = new Button("Continue with ChatGPT");
    login.getStyleClass().add("connection-primary");
    login.setGraphic(SvgIconView.icon(UiTokens.ICON_EXTERNAL_LOGIN, 16));
    login.setOnAction(event -> login(""));
    browser
        .getChildren()
        .addAll(
            SettingsControls.label(
                "기본 브라우저가 열리고, 로그인과 플랜 사용 승인을 마치면 자동으로 연결돼요.", "search-state-message"),
            login);
    for (ChatGptAccounts.Account account : service.snapshot().accounts()) {
      Button use = new Button(account.planEnabled() ? "사용" : "다시 로그인");
      use.getStyleClass().add("secondary-button");
      use.setOnAction(
          event -> {
            if (account.planEnabled()) {
              run(service.useAccount(account.id()));
            } else {
              login(account.id());
            }
          });
      Button reconnect = new Button("다시 로그인");
      reconnect.getStyleClass().add("secondary-button");
      reconnect.setOnAction(event -> login(account.id()));
      Button logout = new Button("로그아웃");
      logout.getStyleClass().add("secondary-button");
      logout.setOnAction(event -> run(service.signOut(account.id())));
      browser
          .getChildren()
          .add(
              new VBox(
                  8,
                  SettingsControls.label(
                      account.label()
                          + (account.id().equals(service.snapshot().accountId())
                                  && service.snapshot().browser()
                              ? " · 선택됨"
                              : ""),
                      "settings-row-title"),
                  new FlowPane(8, 8, use, reconnect, logout)));
    }
    if (service.snapshot().browser()) {
      javafx.scene.control.Hyperlink usage = new javafx.scene.control.Hyperlink("ChatGPT 사용량 관리");
      usage.setOnAction(
          event -> openBrowser.accept(URI.create("https://chatgpt.com/settings/usage")));
      browser
          .getChildren()
          .addAll(
              SettingsControls.label(
                  "ChatGPT 플랜 사용 중 · 요약은 ChatGPT 플랜 또는 승인한 크레딧을 사용해요.", "settings-row-title"),
              usage);
      ComboBox<ChatGptAccounts.Model> models = new ComboBox<>();
      models.getItems().setAll(service.snapshot().models());
      models.setAccessibleText("ChatGPT 계정 모델");
      models.setPromptText(service.snapshot().model());
      models.getItems().stream()
          .filter(model -> model.id().equals(service.snapshot().model()))
          .findFirst()
          .ifPresent(models::setValue);
      models.setOnAction(
          event -> {
            if (models.getValue() != null
                && !models.getValue().id().equals(service.snapshot().model())) {
              run(service.changeBrowserModel(models.getValue().id()));
            }
          });
      Button refresh = new Button("모델 새로고침");
      refresh.getStyleClass().add("secondary-button");
      refresh.setOnAction(event -> run(service.useAccount(service.snapshot().accountId())));
      browser.getChildren().add(new FlowPane(12, 8, models, refresh));
    }
    return browser;
  }

  private boolean connected(AiProviderId id) {
    return service.snapshot().configured().contains(id)
        || id == AiProviderId.CODEX
            && service.snapshot().accounts().stream()
                .anyMatch(ChatGptAccounts.Account::planEnabled);
  }

  private void openAuthorization(URI uri) {
    Platform.runLater(
        () -> {
          try {
            openBrowser.accept(uri);
          } catch (RuntimeException failure) {
            service.cancelSignIn();
          }
        });
  }

  private void login(String id) {
    browserLogin = true;
    run(service.signIn(id, this::openAuthorization));
  }

  private void run(CompletableFuture<AiConnectionService.Snapshot> future) {
    operation = future;
    busy = true;
    render();
    message.setText("연결을 확인하고 있어요…");
    future.whenComplete(
        (snapshot, failure) ->
            Platform.runLater(
                () -> {
                  if (operation != future) {
                    return;
                  }
                  busy = false;
                  browserLogin = false;
                  if (snapshot != null) {
                    selected = snapshot.provider();
                  }
                  render();
                  if (failure == null && service.needsWelcome()) {
                    javafx.scene.control.Alert welcome =
                        new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION);
                    welcome.setTitle("ChatGPT 연결");
                    welcome.setHeaderText("ChatGPT 플랜을 사용하고 있어요");
                    welcome.setContentText(
                        "Serox의 AI 요약은 승인한 ChatGPT 플랜이나 크레딧을 사용해요. AI 제공자 설정의 ‘ChatGPT 사용량 관리’에서 한도와 접근 권한을 관리할 수 있어요.");
                    welcome.showAndWait();
                    service.acknowledgeWelcome();
                  }
                  if (failure != null) {
                    message.setText("연결을 완료하지 못했어요. 승인, API 키, 모델과 네트워크를 확인하고 다시 시도하세요.");
                  }
                }));
  }
}
