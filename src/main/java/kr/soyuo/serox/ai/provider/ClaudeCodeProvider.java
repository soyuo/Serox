package kr.soyuo.serox.ai.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiRequest;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.context.PromptBuilder;

/**
 * Answers through the user's own installed Claude Code CLI ({@code claude -p}). Claude Code keeps
 * its own sign-in, so Serox never sees a Claude token. Tools, MCP servers, settings and session
 * history are turned off so the CLI only writes the summary.
 */
public final class ClaudeCodeProvider implements AiProvider {
  private static final long TIMEOUT_SECONDS = 180;
  private final String model;

  public ClaudeCodeProvider(String model) {
    if (!model.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new IllegalArgumentException("Invalid AI model identifier");
    }
    this.model = model;
  }

  /** The CLI's version line, or empty when Claude Code is not installed or not on PATH. */
  public static Optional<String> installedVersion() {
    try {
      Process process = new ProcessBuilder(launcher(List.of("--version"))).start();
      process.getOutputStream().close();
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(20, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }
      return process.exitValue() == 0 && out.contains("Claude Code")
          ? Optional.of(out.strip())
          : Optional.empty();
    } catch (IOException exception) {
      return Optional.empty();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /** The command line, run through cmd.exe on Windows so the npm {@code claude.cmd} shim works. */
  static List<String> launcher(List<String> arguments) {
    List<String> command = new ArrayList<>();
    if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
      command.addAll(List.of("cmd.exe", "/d", "/c", "claude"));
    } else {
      command.add("claude");
    }
    command.addAll(arguments);
    return command;
  }

  static List<String> arguments(String model, Path systemPrompt) {
    return List.of(
        "-p",
        "--output-format",
        "stream-json",
        "--verbose",
        "--include-partial-messages",
        "--no-session-persistence",
        "--strict-mcp-config",
        "--disable-slash-commands",
        "--setting-sources",
        "",
        "--tools",
        "",
        "--model",
        model,
        "--system-prompt-file",
        systemPrompt.toString());
  }

  /** Turns one stream-json line into the events it carries. */
  static List<AiEvent> events(String line) {
    JsonObject event;
    try {
      event = JsonParser.parseString(line).getAsJsonObject();
    } catch (RuntimeException notJson) {
      return List.of();
    }
    String type = string(event, "type");
    if ("stream_event".equals(type) && event.get("event") instanceof JsonObject inner) {
      if ("content_block_delta".equals(string(inner, "type"))
          && inner.get("delta") instanceof JsonObject delta
          && "text_delta".equals(string(delta, "type"))) {
        String text = string(delta, "text");
        return text.isEmpty() ? List.of() : List.of(new AiTextDelta(text));
      }
      return List.of();
    }
    if ("result".equals(type)) {
      boolean failed =
          event.has("is_error") && event.get("is_error").getAsBoolean()
              || !"success".equals(string(event, "subtype"));
      return failed
          ? List.of(failure(string(event, "result")))
          : List.of(new AiCompleted(List.of()));
    }
    return List.of();
  }

  private static AiFailed failure(String detail) {
    String lower = detail.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("login") || lower.contains("auth") || lower.contains("not logged")) {
      return new AiFailed(
          "unauthorized", "Claude Code 로그인이 필요해요. 터미널에서 claude 를 실행해 로그인하세요.");
    }
    if (lower.contains("limit")) {
      return new AiFailed("rate_limited", "Claude 구독 사용 한도에 도달했어요.");
    }
    return new AiFailed("provider_error", "Claude Code가 요약을 마치지 못했어요.");
  }

  private static String string(JsonObject object, String key) {
    return object.has(key) && object.get(key).isJsonPrimitive()
        ? object.get(key).getAsString()
        : "";
  }

  @Override
  public Flow.Publisher<AiEvent> analyze(AiRequest request) {
    Objects.requireNonNull(request, "request");
    return subscriber -> {
      AtomicBoolean started = new AtomicBoolean();
      AtomicBoolean cancelled = new AtomicBoolean();
      AtomicReference<Process> running = new AtomicReference<>();
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
              Thread.ofVirtual()
                  .name("claude-code")
                  .start(() -> run(request, subscriber, cancelled, running));
            }

            @Override
            public void cancel() {
              cancelled.set(true);
              Process process = running.get();
              if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
              }
            }
          });
    };
  }

  private void run(
      AiRequest request,
      Flow.Subscriber<? super AiEvent> subscriber,
      AtomicBoolean cancelled,
      AtomicReference<Process> running) {
    Path directory = null;
    boolean finished = false;
    try {
      // An empty working folder keeps project files and CLAUDE.md out of the prompt.
      directory = Files.createTempDirectory("serox-claude-");
      Path system = directory.resolve("system.txt");
      Files.writeString(
          system, PromptBuilder.systemPrompt(request.answerLanguage()), StandardCharsets.UTF_8);
      Process process =
          new ProcessBuilder(launcher(arguments(model, system)))
              .directory(directory.toFile())
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      running.set(process);
      if (cancelled.get()) {
        process.destroyForcibly();
        return;
      }
      try (OutputStream input = process.getOutputStream()) {
        input.write(PromptBuilder.userMessage(request).getBytes(StandardCharsets.UTF_8));
      }
      Thread watchdog =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        process.descendants().forEach(ProcessHandle::destroyForcibly);
                        process.destroyForcibly();
                      }
                    } catch (InterruptedException ignored) {
                      // The answer finished first.
                    }
                  });
      try (BufferedReader output =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while (!finished && (line = output.readLine()) != null) {
          for (AiEvent event : events(line)) {
            if (cancelled.get()) {
              return;
            }
            subscriber.onNext(event);
            if (event instanceof AiCompleted || event instanceof AiFailed) {
              finished = true;
            }
          }
        }
      } finally {
        watchdog.interrupt();
      }
      if (!finished && !cancelled.get()) {
        // The stream ended without a result: the CLI failed or the watchdog stopped it.
        subscriber.onNext(failure(""));
      }
      if (!cancelled.get()) {
        subscriber.onComplete();
      }
    } catch (IOException exception) {
      if (!cancelled.get()) {
        subscriber.onNext(
            new AiFailed("network", "Claude Code를 실행하지 못했어요. 설치되어 있고 PATH에 있는지 확인하세요."));
        subscriber.onComplete();
      }
    } finally {
      Process process = running.get();
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
      if (directory != null) {
        try (var files = Files.list(directory)) {
          for (Path file : files.toList()) {
            Files.deleteIfExists(file);
          }
          Files.deleteIfExists(directory);
        } catch (IOException ignored) {
          // A leftover empty temp folder is harmless.
        }
      }
    }
  }
}
