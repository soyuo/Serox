package kr.soyuo.serox.core.model;

/** The actions of the shortcuts page, in the order the design lists them. */
public enum ShortcutAction {
  FOCUS_SEARCH("검색창으로 이동", "Ctrl+L"),
  TOGGLE_AI("AI 요약 켜기 / 끄기", "Ctrl+Shift+A"),
  NEXT_SOURCE("다음 출처 열기", "Alt+Down"),
  PREVIOUS_SOURCE("이전 출처 열기", "Alt+Up"),
  NEW_TAB("새 탭", "Ctrl+T"),
  CLOSE_TAB("탭 닫기", "Ctrl+W"),
  OPEN_HISTORY("검색 기록 열기", "Ctrl+H"),
  OPEN_SETTINGS("설정 열기", "Ctrl+,");

  private final String label;
  private final KeyChord defaultChord;

  ShortcutAction(String label, String defaultChord) {
    this.label = label;
    this.defaultChord = KeyChord.parse(defaultChord).orElseThrow();
  }

  /** The name shown on the shortcuts page. */
  public String label() {
    return label;
  }

  /** The key combination the action has until the user changes it. */
  public KeyChord defaultChord() {
    return defaultChord;
  }
}
