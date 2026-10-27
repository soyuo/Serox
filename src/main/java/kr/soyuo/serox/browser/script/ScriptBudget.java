package kr.soyuo.serox.browser.script;

/** The time left for one page's scripts; the engine checks it while the code runs. */
final class ScriptBudget {
  /** Thrown inside the engine when the time is used up; ends all scripts of the page. */
  static final class Exceeded extends Error {
    private static final long serialVersionUID = 1L;

    Exceeded() {
      super("Script time limit reached", null, false, false);
    }
  }

  private final long deadlineNanos;

  ScriptBudget(long timeMillis) {
    this.deadlineNanos = System.nanoTime() + timeMillis * 1_000_000L;
  }

  void check() {
    if (System.nanoTime() - deadlineNanos > 0) {
      throw new Exceeded();
    }
  }
}
