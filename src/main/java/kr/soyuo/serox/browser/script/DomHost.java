package kr.soyuo.serox.browser.script;

import java.net.URI;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import kr.soyuo.serox.browser.dom.CommentNode;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.Node;
import kr.soyuo.serox.browser.dom.TextNode;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * The browser objects a page's script can see: {@code window}, {@code document}, elements,
 * timers, events and a few harmless stand-ins. Only objects made here reach the script; no Java
 * class is visible to it, and nothing here reaches the network or the disk.
 */
final class DomHost {
  private static final Object NOT_FOUND = Scriptable.NOT_FOUND;
  private static final long MAX_TIMER_DELAY_MILLIS = 5000;
  private static final int MAX_INTERVAL_RUNS = 10;
  private static final int MAX_HTML_CHARS = 1_000_000;
  private static final String PRELUDE =
      """
      (function (g) {
        var denied = function () {
          var e = new Error('Permission denied');
          e.name = 'NotAllowedError';
          return e;
        };
        var positionError = { code: 1, message: 'User denied Geolocation' };
        g.navigator = {
          userAgent: 'Mozilla/5.0 (compatible; Serox)',
          language: 'ko-KR',
          languages: ['ko-KR', 'en'],
          platform: '',
          cookieEnabled: false,
          onLine: true,
          geolocation: {
            getCurrentPosition: function (ok, fail) { if (fail) { fail(positionError); } },
            watchPosition: function (ok, fail) { if (fail) { fail(positionError); } return 0; },
            clearWatch: function () {}
          },
          mediaDevices: {
            getUserMedia: function () { return Promise.reject(denied()); },
            enumerateDevices: function () { return Promise.resolve([]); }
          }
        };
        g.Notification = function () { throw denied(); };
        g.Notification.permission = 'denied';
        g.Notification.requestPermission = function (done) {
          if (done) { done('denied'); }
          return Promise.resolve('denied');
        };
        var makeStorage = function () {
          var data = {};
          return {
            getItem: function (k) { return Object.prototype.hasOwnProperty.call(data, k) ? data[k] : null; },
            setItem: function (k, v) { data[String(k)] = String(v); },
            removeItem: function (k) { delete data[k]; },
            clear: function () { data = {}; },
            key: function (i) { return Object.keys(data)[i] === undefined ? null : Object.keys(data)[i]; },
            get length() { return Object.keys(data).length; }
          };
        };
        g.localStorage = makeStorage();
        g.sessionStorage = makeStorage();
        g.alert = function () {};
        g.confirm = function () { return false; };
        g.prompt = function () { return null; };
        g.open = function () { return null; };
        g.close = function () {};
        g.focus = function () {};
        g.blur = function () {};
        g.scrollTo = function () {};
        g.getComputedStyle = function () { return {}; };
        g.matchMedia = function () {
          return { matches: false, addListener: function () {}, removeListener: function () {} };
        };
        g.requestAnimationFrame = function (f) { return g.setTimeout(f, 16); };
        g.cancelAnimationFrame = function (id) { g.clearTimeout(id); };
        g.innerWidth = 1100;
        g.innerHeight = 720;
        g.devicePixelRatio = 1;
      })(this);
      """;

  private record Timer(int id, Callable function, long delay, boolean repeat, Object[] args) {}

  private static final class Pending {
    final Timer timer;
    long due;
    int runs;

    Pending(Timer timer, long due) {
      this.timer = timer;
      this.due = due;
    }
  }

  private final Document document;
  private final ScriptableObject scope;
  private final ScriptBudget budget;
  private final ScriptLimits limits;
  private final List<String> messages = new ArrayList<>();
  private final Map<Node, NodeWrapper> wrappers = new IdentityHashMap<>();
  private final Map<Object, Map<String, List<Callable>>> listeners = new IdentityHashMap<>();
  private final List<Pending> timers = new ArrayList<>();
  private final HtmlDocumentParser parser = new HtmlDocumentParser();
  private int nextTimerId = 1;

  DomHost(Document document, ScriptableObject scope, ScriptBudget budget, ScriptLimits limits) {
    this.document = document;
    this.scope = scope;
    this.budget = budget;
    this.limits = limits;
  }

  List<String> messages() {
    return List.copyOf(messages);
  }

  void log(String line) {
    if (messages.size() < limits.maxMessages()) {
      messages.add(line.length() > 500 ? line.substring(0, 500) : line);
    }
  }

  /** Puts the browser objects into the script's global scope. */
  void install(Context cx) {
    ScriptableObject.putProperty(scope, "window", scope);
    ScriptableObject.putProperty(scope, "self", scope);
    ScriptableObject.putProperty(scope, "top", scope);
    ScriptableObject.putProperty(scope, "parent", scope);
    ScriptableObject.putProperty(scope, "document", wrap(document));
    ScriptableObject.putProperty(scope, "location", location(cx));
    ScriptableObject.putProperty(scope, "console", console(cx));
    define("setTimeout", args -> addTimer(args, false));
    define("setInterval", args -> addTimer(args, true));
    define("clearTimeout", this::clearTimer);
    define("clearInterval", this::clearTimer);
    define("addEventListener", args -> addListener(scope, args));
    define("removeEventListener", args -> removeListener(scope, args));
    cx.evaluateString(scope, PRELUDE, "serox-prelude", 1, null);
  }

  private void define(String name, Function<Object[], Object> body) {
    ScriptableObject.putProperty(scope, name, function(body));
  }

  private Scriptable location(Context cx) {
    URI uri = document.documentUrl();
    NativeObject location = (NativeObject) cx.newObject(scope);
    String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    String origin = uri.getScheme() + "://" + uri.getRawAuthority();
    ScriptableObject.putProperty(location, "href", uri.toString());
    ScriptableObject.putProperty(location, "origin", origin);
    ScriptableObject.putProperty(location, "protocol", uri.getScheme() + ":");
    ScriptableObject.putProperty(location, "host", uri.getRawAuthority());
    ScriptableObject.putProperty(location, "hostname", uri.getHost());
    ScriptableObject.putProperty(location, "port", uri.getPort() < 0 ? "" : "" + uri.getPort());
    ScriptableObject.putProperty(location, "pathname", path);
    ScriptableObject.putProperty(location, "search", uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    ScriptableObject.putProperty(location, "hash", uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    ScriptableObject.putProperty(location, "assign", function(args -> Undefined.instance));
    ScriptableObject.putProperty(location, "replace", function(args -> Undefined.instance));
    ScriptableObject.putProperty(location, "reload", function(args -> Undefined.instance));
    ScriptableObject.putProperty(location, "toString", function(args -> uri.toString()));
    return location;
  }

  private Scriptable console(Context cx) {
    NativeObject console = (NativeObject) cx.newObject(scope);
    for (String level : List.of("log", "info", "warn", "error", "debug")) {
      ScriptableObject.putProperty(
          console,
          level,
          function(
              args -> {
                StringBuilder line = new StringBuilder(level).append(": ");
                for (Object arg : args) {
                  line.append(Context.toString(arg)).append(' ');
                }
                log(line.toString().strip());
                return Undefined.instance;
              }));
    }
    return console;
  }

  // ----- functions, wrappers and small helpers -----

  /** A script-callable function backed by Java code. */
  Scriptable function(Function<Object[], Object> body) {
    return new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
      private static final long serialVersionUID = 1L;

      @Override
      public Object call(Context cx, Scriptable callScope, Scriptable thisObj, Object[] args) {
        Object result = body.apply(args);
        return result == null ? null : result;
      }
    };
  }

  /** The script-side object for a DOM node; the same node always gives the same object. */
  Object wrap(Node node) {
    if (node == null) {
      return null;
    }
    return wrappers.computeIfAbsent(node, key -> new NodeWrapper(this, key));
  }

  Document document() {
    return document;
  }

  ScriptableObject scope() {
    return scope;
  }

  HtmlDocumentParser parser() {
    return parser;
  }

  Object array(List<?> items) {
    return Context.getCurrentContext().newArray(scope, items.toArray());
  }

  static String text(Object[] args, int index) {
    return args.length > index ? Context.toString(args[index]) : "";
  }

  Node nodeArg(Object[] args, int index) {
    if (args.length > index && args[index] instanceof NodeWrapper wrapper) {
      return wrapper.node();
    }
    throw org.mozilla.javascript.ScriptRuntime.typeError("The argument is not a node");
  }

  /** Parses markup into nodes that are not yet in any document. */
  List<Node> parseFragment(String html) {
    String bounded = html.length() > MAX_HTML_CHARS ? html.substring(0, MAX_HTML_CHARS) : html;
    Document parsed = parser.parse(document.documentUrl(), "<html><body>" + bounded + "</body></html>");
    return parsed.body().map(Node::children).orElse(List.of());
  }

  // ----- events -----

  Object addListener(Object target, Object[] args) {
    if (args.length > 1 && args[1] instanceof Callable callable) {
      listeners
          .computeIfAbsent(target, key -> new LinkedHashMap<>())
          .computeIfAbsent(text(args, 0), key -> new ArrayList<>())
          .add(callable);
    }
    return Undefined.instance;
  }

  Object removeListener(Object target, Object[] args) {
    Map<String, List<Callable>> byType = listeners.get(target);
    if (byType != null && args.length > 1) {
      List<Callable> list = byType.get(text(args, 0));
      if (list != null) {
        list.remove(args[1]);
      }
    }
    return Undefined.instance;
  }

  /** Runs the listeners for an event, then those of the ancestors when it bubbles. */
  boolean dispatch(Node target, String type, boolean bubbles) {
    Context cx = Context.getCurrentContext();
    NativeObject event = (NativeObject) cx.newObject(scope);
    boolean[] prevented = {false};
    ScriptableObject.putProperty(event, "type", type);
    ScriptableObject.putProperty(event, "bubbles", bubbles);
    ScriptableObject.putProperty(event, "target", wrap(target));
    ScriptableObject.putProperty(event, "preventDefault", function(args -> {
      prevented[0] = true;
      return Undefined.instance;
    }));
    ScriptableObject.putProperty(event, "stopPropagation", function(args -> Undefined.instance));
    List<Object> path = new ArrayList<>();
    Node current = target;
    while (current != null) {
      path.add(current);
      if (!bubbles) {
        break;
      }
      current = current.parent().orElse(null);
    }
    if (bubbles) {
      path.add(scope);
    }
    for (Object stop : path) {
      List<Callable> found = listeners.getOrDefault(stop, Map.of()).get(type);
      if (found != null) {
        for (Callable listener : List.copyOf(found)) {
          ScriptableObject.putProperty(
              event, "currentTarget", stop instanceof Node node ? wrap(node) : scope);
          invoke(listener, stop instanceof Node node ? (Scriptable) wrap(node) : scope, event);
        }
      }
    }
    return !prevented[0];
  }

  /** Runs the "DOM ready" and "load" listeners once all scripts have run. */
  void fireLifecycle() {
    dispatchTo(document, "DOMContentLoaded");
    dispatchTo(scope, "load");
    Object onload = ScriptableObject.getProperty(scope, "onload");
    if (onload instanceof Callable callable) {
      invoke(callable, scope);
    }
  }

  private void dispatchTo(Object target, String type) {
    Context cx = Context.getCurrentContext();
    NativeObject event = (NativeObject) cx.newObject(scope);
    ScriptableObject.putProperty(event, "type", type);
    for (Callable listener : List.copyOf(listeners.getOrDefault(target, Map.of()).getOrDefault(type, List.of()))) {
      invoke(listener, scope, event);
    }
  }

  // ----- timers -----

  private Object addTimer(Object[] args, boolean repeat) {
    if (args.length == 0 || !(args[0] instanceof Callable function)) {
      return 0;
    }
    long delay = args.length > 1 ? (long) Math.max(0, Context.toNumber(args[1])) : 0;
    Object[] extra = args.length > 2 ? java.util.Arrays.copyOfRange(args, 2, args.length) : new Object[0];
    if (timers.size() >= limits.maxTimerTasks()) {
      return 0;
    }
    int id = nextTimerId++;
    timers.add(new Pending(new Timer(id, function, delay, repeat, extra), delay));
    return id;
  }

  private Object clearTimer(Object[] args) {
    if (args.length > 0) {
      int id = (int) Context.toNumber(args[0]);
      timers.removeIf(pending -> pending.timer.id() == id);
    }
    return Undefined.instance;
  }

  /** Runs the queued timers in order of their time, without really waiting. */
  void runTimers() {
    int tasks = 0;
    while (!timers.isEmpty() && tasks < limits.maxTimerTasks()) {
      budget.check();
      Pending next = timers.get(0);
      for (Pending pending : timers) {
        if (pending.due < next.due) {
          next = pending;
        }
      }
      if (next.due > MAX_TIMER_DELAY_MILLIS) {
        break;
      }
      tasks++;
      next.runs++;
      if (next.timer.repeat() && next.runs < MAX_INTERVAL_RUNS) {
        next.due += Math.max(1, next.timer.delay());
      } else {
        timers.remove(next);
      }
      invoke(next.timer.function(), scope, next.timer.args());
    }
  }

  // ----- running script code -----

  void invoke(Callable function, Scriptable thisObject, Object... args) {
    Context cx = Context.getCurrentContext();
    try {
      function.call(cx, scope, thisObject, args);
      cx.processMicrotasks();
    } catch (RhinoException failure) {
      log("error: " + failure.details());
    }
  }

  /** Runs one script's source; an error in it is recorded and the next script still runs. */
  boolean evaluate(Context cx, String source, String name) {
    try {
      cx.evaluateString(scope, source, name, 1, null);
      cx.processMicrotasks();
      return true;
    } catch (RhinoException failure) {
      log("error: " + failure.details() + " (" + name + ":" + failure.lineNumber() + ")");
      return false;
    }
  }

  static String tagName(Node node) {
    if (node instanceof Element element) {
      return element.tagName().toUpperCase(Locale.ROOT);
    }
    if (node instanceof TextNode) {
      return "#text";
    }
    if (node instanceof CommentNode) {
      return "#comment";
    }
    return "#document";
  }

  Object undefinedIfNull(Object value) {
    return value == null ? Undefined.instance : value;
  }
}
