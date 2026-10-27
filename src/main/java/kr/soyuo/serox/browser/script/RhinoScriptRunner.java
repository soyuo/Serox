package kr.soyuo.serox.browser.script;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

/**
 * Runs a page's classic scripts in Rhino, a JavaScript engine written in Java. The engine runs
 * without Java access, in interpreted mode with a stack limit, and every page gets a fresh
 * global scope and a time budget. Scripts see only the objects {@link DomHost} makes.
 */
public final class RhinoScriptRunner implements ScriptRunner {
  private static final List<String> SCRIPT_TYPES =
      List.of("", "text/javascript", "application/javascript", "text/ecmascript", "application/ecmascript");
  private static final int OBSERVE_EVERY_INSTRUCTIONS = 5_000;
  private static final int MAX_STACK_DEPTH = 200;

  private final ScriptFetcher fetcher;
  private final Predicate<URI> enabled;
  private final ScriptLimits limits;

  /** Runs scripts when {@code enabled} accepts the page address; {@code fetcher} loads src files. */
  public RhinoScriptRunner(ScriptFetcher fetcher, Predicate<URI> enabled, ScriptLimits limits) {
    this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    this.enabled = Objects.requireNonNull(enabled, "enabled");
    this.limits = Objects.requireNonNull(limits, "limits");
  }

  /** The context factory that sets the sandbox rules on every context it makes. */
  private static final class SandboxFactory extends ContextFactory {
    private final ScriptBudget budget;

    SandboxFactory(ScriptBudget budget) {
      this.budget = budget;
    }

    @Override
    protected Context makeContext() {
      Context cx = super.makeContext();
      cx.setLanguageVersion(Context.VERSION_ES6);
      cx.setOptimizationLevel(-1);
      cx.setMaximumInterpreterStackDepth(MAX_STACK_DEPTH);
      cx.setInstructionObserverThreshold(OBSERVE_EVERY_INSTRUCTIONS);
      cx.setClassShutter(name -> false);
      return cx;
    }

    @Override
    protected void observeInstructionCount(Context cx, int instructionCount) {
      budget.check();
    }
  }

  @Override
  public ScriptReport run(Document document) {
    Objects.requireNonNull(document, "document");
    List<Element> scripts = document.getElementsByTagName("script");
    if (scripts.isEmpty() || !enabled.test(document.documentUrl())) {
      return new ScriptReport(0, List.of());
    }
    ScriptBudget budget = new ScriptBudget(limits.timeMillis());
    SandboxFactory factory = new SandboxFactory(budget);
    return factory.call(cx -> runAll(cx, document, scripts, budget));
  }

  private ScriptReport runAll(Context cx, Document document, List<Element> scripts, ScriptBudget budget) {
    ScriptableObject scope = cx.initSafeStandardObjects();
    DomHost host = new DomHost(document, scope, budget, limits);
    int ran = 0;
    try {
      host.install(cx);
      int external = 0;
      int chars = 0;
      for (Element script : scripts) {
        String type = script.attribute("type").orElse("").strip().toLowerCase(Locale.ROOT);
        if (!SCRIPT_TYPES.contains(type)) {
          continue;
        }
        String name = "inline";
        String source = script.textContent();
        Optional<String> src = script.attribute("src").map(String::strip).filter(value -> !value.isEmpty());
        if (src.isPresent()) {
          Optional<URI> uri = resolve(document.documentUrl(), src.get());
          if (uri.isEmpty() || external >= limits.maxExternalScripts()) {
            host.log("skipped script: " + src.get());
            continue;
          }
          external++;
          Optional<String> loaded = fetcher.fetch(uri.get());
          if (loaded.isEmpty()) {
            host.log("could not load script: " + uri.get());
            continue;
          }
          source = loaded.get();
          name = uri.get().toString();
        }
        chars += source.length();
        if (chars > limits.maxScriptChars()) {
          host.log("script size limit reached");
          break;
        }
        if (host.evaluate(cx, source, name)) {
          ran++;
        }
      }
      host.fireLifecycle();
      host.runTimers();
    } catch (ScriptBudget.Exceeded timeUp) {
      host.log("script time limit reached");
    } catch (StackOverflowError | OutOfMemoryError tooBig) {
      host.log("script stopped: it used too much memory or stack");
    }
    return new ScriptReport(ran, host.messages());
  }

  private static Optional<URI> resolve(URI base, String src) {
    try {
      URI resolved = base.resolve(src);
      String scheme = resolved.getScheme();
      boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
      return web && resolved.getHost() != null ? Optional.of(resolved) : Optional.empty();
    } catch (IllegalArgumentException malformed) {
      return Optional.empty();
    }
  }
}
