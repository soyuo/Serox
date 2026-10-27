package kr.soyuo.serox.browser.network;

import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.browser.api.PageLoadRequest;

/** Injectable asynchronous boundary for sending a page request. */
public interface HttpTransport {
  CompletionStage<HttpResponseData> dispatch(PageLoadRequest request);
}
