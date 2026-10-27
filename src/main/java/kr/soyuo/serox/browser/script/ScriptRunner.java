package kr.soyuo.serox.browser.script;

import kr.soyuo.serox.browser.dom.Document;

/** Runs the scripts of a freshly parsed document, changing the document in place. */
public interface ScriptRunner {
  /** A runner that does nothing, for engines and tests that do not run scripts. */
  ScriptRunner NONE = document -> new ScriptReport(0, java.util.List.of());

  /** Runs the page's scripts and reports what happened; never throws for a script's own error. */
  ScriptReport run(Document document);
}
