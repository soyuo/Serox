package kr.soyuo.serox.browser.api;

import java.util.concurrent.CompletionStage;

/** Public asynchronous entry point for loading a page through the browser engine. */
public interface BrowserEngine {
  CompletionStage<PageSnapshot> load(PageLoadRequest request);
}
