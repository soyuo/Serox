package kr.soyuo.serox.browser.script;

import java.util.List;

/** What running a page's scripts did: how many ran, and the errors and console lines they made. */
public record ScriptReport(int scriptsRun, List<String> messages) {
  public ScriptReport {
    messages = List.copyOf(messages);
  }
}
