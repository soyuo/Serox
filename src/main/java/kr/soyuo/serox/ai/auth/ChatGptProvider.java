package kr.soyuo.serox.ai.auth;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiRequest;
import kr.soyuo.serox.ai.provider.AiProviders;
import kr.soyuo.serox.core.model.AiProviderId;
import kr.soyuo.serox.core.model.SecretValue;

/**
 * Refreshes the selected ChatGPT session off the UI thread, then uses public Responses streaming.
 */
public final class ChatGptProvider implements AiProvider {
  private final ChatGptAccounts accounts;
  private final String accountId;
  private final String model;

  public ChatGptProvider(ChatGptAccounts accounts, String accountId, String model) {
    this.accounts = accounts;
    this.accountId = accountId;
    this.model = model;
  }

  @Override
  public Flow.Publisher<AiEvent> analyze(AiRequest request) {
    return subscriber -> {
      AtomicBoolean started = new AtomicBoolean();
      AtomicBoolean cancelled = new AtomicBoolean();
      AtomicReference<Flow.Subscription> upstream = new AtomicReference<>();
      AtomicReference<Thread> worker = new AtomicReference<>();
      subscriber.onSubscribe(
          new Flow.Subscription() {
            @Override
            public void request(long n) {
              if (cancelled.get() || !started.compareAndSet(false, true)) {
                return;
              }
              if (n <= 0) {
                cancelled.set(true);
                subscriber.onError(new IllegalArgumentException("Demand must be positive"));
                return;
              }
              Thread thread =
                  Thread.ofVirtual()
                      .unstarted(
                          () -> {
                            try {
                              String token = accounts.accessToken(accountId);
                              if (cancelled.get()) {
                                return;
                              }
                              AiProviders.create(AiProviderId.CODEX, model, new SecretValue(token))
                                  .analyze(request)
                                  .subscribe(
                                      new Flow.Subscriber<>() {
                                        @Override
                                        public void onSubscribe(Flow.Subscription subscription) {
                                          upstream.set(subscription);
                                          if (cancelled.get()) {
                                            subscription.cancel();
                                          } else {
                                            subscription.request(Long.MAX_VALUE);
                                          }
                                        }

                                        @Override
                                        public void onNext(AiEvent event) {
                                          if (!cancelled.get()) {
                                            subscriber.onNext(event);
                                          }
                                        }

                                        @Override
                                        public void onError(Throwable error) {
                                          if (!cancelled.get()) {
                                            subscriber.onError(
                                                new IllegalStateException(
                                                    "ChatGPT request failed"));
                                          }
                                        }

                                        @Override
                                        public void onComplete() {
                                          if (!cancelled.get()) {
                                            subscriber.onComplete();
                                          }
                                        }
                                      });
                            } catch (Exception exception) {
                              if (!cancelled.get()) {
                                subscriber.onNext(
                                    new AiFailed(
                                        "authentication_required",
                                        "ChatGPT 연결이 만료됐어요. AI 제공자 설정에서 다시 로그인하세요."));
                                subscriber.onComplete();
                              }
                            }
                          });
              worker.set(thread);
              thread.start();
            }

            @Override
            public void cancel() {
              cancelled.set(true);
              Flow.Subscription subscription = upstream.get();
              if (subscription != null) {
                subscription.cancel();
              }
              Thread thread = worker.get();
              if (thread != null) {
                thread.interrupt();
              }
            }
          });
    };
  }
}
