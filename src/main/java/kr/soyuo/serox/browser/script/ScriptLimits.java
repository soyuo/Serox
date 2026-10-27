package kr.soyuo.serox.browser.script;

/** Upper bounds that keep one page's scripts from taking over the browser. */
public record ScriptLimits(
    long timeMillis,
    int maxExternalScripts,
    int maxScriptChars,
    int maxTimerTasks,
    int maxMessages) {
  public ScriptLimits {
    if (timeMillis < 1
        || maxExternalScripts < 0
        || maxScriptChars < 1
        || maxTimerTasks < 0
        || maxMessages < 1) {
      throw new IllegalArgumentException("Invalid script limits");
    }
  }

  /** Three seconds in all, ten outside scripts, one million characters, a hundred timer runs. */
  public static ScriptLimits defaults() {
    return new ScriptLimits(3000, 10, 1_000_000, 100, 200);
  }
}
