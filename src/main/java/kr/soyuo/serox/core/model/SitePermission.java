package kr.soyuo.serox.core.model;

/**
 * The permissions a site settings page lists. Only the ones Serox can really give or refuse can
 * be changed; the rest are always blocked, because Serox has no such feature to switch on.
 */
public enum SitePermission {
  LOCATION("위치", false, PermissionChoice.BLOCK, "", "이 사이트가 내 위치를 알 수 없어요."),
  CAMERA("카메라", false, PermissionChoice.BLOCK, "", "이 사이트가 카메라를 쓸 수 없어요."),
  MICROPHONE("마이크", false, PermissionChoice.BLOCK, "", "이 사이트가 마이크를 쓸 수 없어요."),
  NOTIFICATIONS("알림", false, PermissionChoice.BLOCK, "", "이 사이트가 알림을 보낼 수 없어요."),
  JAVASCRIPT(
      "JavaScript",
      true,
      PermissionChoice.ALLOW,
      "페이지가 동작하는 데 필요해요. 대부분의 사이트가 사용해요.",
      "기능이 동작하지 않거나 화면이 깨질 수 있어요."),
  POPUPS("팝업과 리디렉션", false, PermissionChoice.BLOCK, "", "사이트가 새 창을 열거나 다른 곳으로 보낼 수 없어요."),
  DOWNLOADS("자동 다운로드", false, PermissionChoice.BLOCK, "", "사이트가 마음대로 파일을 내려받을 수 없어요.");

  private final String label;
  private final boolean changeable;
  private final PermissionChoice defaultChoice;
  private final String allowHint;
  private final String blockHint;

  SitePermission(
      String label,
      boolean changeable,
      PermissionChoice defaultChoice,
      String allowHint,
      String blockHint) {
    this.label = label;
    this.changeable = changeable;
    this.defaultChoice = defaultChoice;
    this.allowHint = allowHint;
    this.blockHint = blockHint;
  }

  public String label() {
    return label;
  }

  /** Whether the user can switch this one; the others are always blocked. */
  public boolean changeable() {
    return changeable;
  }

  public PermissionChoice defaultChoice() {
    return defaultChoice;
  }

  public String allowHint() {
    return allowHint;
  }

  public String blockHint() {
    return blockHint;
  }
}
