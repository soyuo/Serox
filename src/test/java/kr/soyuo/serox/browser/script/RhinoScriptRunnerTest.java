package kr.soyuo.serox.browser.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class RhinoScriptRunnerTest {
  private static final URI PAGE = URI.create("https://example.test/dir/page.html");

  private static Document parse(String body) {
    return new HtmlDocumentParser()
        .parse(PAGE, "<html><head><title>T</title></head><body>" + body + "</body></html>");
  }

  private static RhinoScriptRunner runner() {
    return new RhinoScriptRunner(ScriptFetcher.NONE, uri -> true, ScriptLimits.defaults());
  }

  private static String text(Document document, String id) {
    return document.getElementById(id).orElseThrow().textContent();
  }

  @Test
  void aScriptChangesTheTextOfAnElement() {
    var document = parse("<p id='a'>old</p><script>document.getElementById('a').textContent = 'new';</script>");

    var report = runner().run(document);

    assertEquals("new", text(document, "a"));
    assertEquals(1, report.scriptsRun());
  }

  @Test
  void scriptsCanBuildElementsAndQueryThem() {
    var document =
        parse(
            "<div id='box'></div><script>"
                + "var li = document.createElement('li');"
                + "li.className = 'item hot';"
                + "li.textContent = 'one';"
                + "var ul = document.createElement('ul');"
                + "ul.appendChild(li);"
                + "document.getElementById('box').appendChild(ul);"
                + "document.title = 'Done ' + document.querySelectorAll('ul > li.hot').length;"
                + "</script>");

    runner().run(document);

    assertEquals("one", document.getElementsByTagName("li").get(0).textContent());
    assertEquals("item hot", document.getElementsByTagName("li").get(0).attribute("class").orElse(""));
    assertEquals("Done 1", document.getElementsByTagName("title").get(0).textContent());
  }

  @Test
  void innerHtmlStyleAndClassListWork() {
    var document =
        parse(
            "<div id='d'></div><script>"
                + "var d = document.getElementById('d');"
                + "d.innerHTML = '<b>bold</b> text';"
                + "d.style.backgroundColor = 'red';"
                + "d.classList.add('x'); d.classList.add('y'); d.classList.remove('x');"
                + "d.setAttribute('data-n', String(d.children.length));"
                + "</script>");

    runner().run(document);

    var d = document.getElementById("d").orElseThrow();
    assertEquals("bold text", d.textContent());
    assertEquals("background-color: red;", d.attribute("style").orElse(""));
    assertEquals("y", d.attribute("class").orElse(""));
    assertEquals("1", d.attribute("data-n").orElse(""));
  }

  @Test
  void timersAndReadyListenersRunAfterTheScripts() {
    var document =
        parse(
            "<p id='log'></p><script>"
                + "var log = [];"
                + "setTimeout(function () { log.push('timer'); }, 10);"
                + "document.addEventListener('DOMContentLoaded', function () { log.push('ready'); });"
                + "log.push('script');"
                + "window.onload = function () {"
                + "  document.getElementById('log').textContent = log.join(',');"
                + "};"
                + "setTimeout(function () { document.getElementById('log').textContent = log.join(','); }, 20);"
                + "</script>");

    runner().run(document);

    assertEquals("script,ready,timer", text(document, "log"));
  }

  @Test
  void clickListenersRunWhenAScriptClicks() {
    var document =
        parse(
            "<button id='b'>go</button><p id='out'></p><script>"
                + "document.getElementById('b').addEventListener('click', function (e) {"
                + "  document.getElementById('out').textContent = e.type + ':' + e.target.id;"
                + "});"
                + "document.getElementById('b').click();"
                + "</script>");

    runner().run(document);

    assertEquals("click:b", text(document, "out"));
  }

  @Test
  void anEndlessLoopIsStoppedAndTheNextPageWorkStillHappens() {
    var document = parse("<p id='a'>x</p><script>while (true) {}</script>");
    var limits = new ScriptLimits(300, 10, 1_000_000, 100, 200);
    long start = System.nanoTime();

    var report = new RhinoScriptRunner(ScriptFetcher.NONE, uri -> true, limits).run(document);

    assertTrue((System.nanoTime() - start) / 1_000_000 < 5000);
    assertTrue(report.messages().stream().anyMatch(line -> line.contains("time limit")));
    assertEquals("x", text(document, "a"));
  }

  @Test
  void scriptsCannotReachJava() {
    var document =
        parse(
            "<p id='a'>safe</p><script>"
                + "try { document.getElementById('a').textContent = String(java.lang.System.getenv('PATH')); } catch (e) {}"
                + "try { document.getElementById('a').textContent = String(Packages.java.lang.System); } catch (e) {}"
                + "try { document.getElementById('a').textContent = String(Java.type('java.lang.System')); } catch (e) {}"
                + "try { document.getElementById('a').textContent = typeof importPackage; } catch (e) {}"
                + "</script>");

    runner().run(document);

    assertEquals("undefined", text(document, "a"));
  }

  @Test
  void anErrorInOneScriptDoesNotStopTheNext() {
    var document =
        parse(
            "<p id='a'>x</p><script>nothing.here();</script>"
                + "<script>document.getElementById('a').textContent = 'second';</script>");

    var report = runner().run(document);

    assertEquals("second", text(document, "a"));
    assertEquals(1, report.scriptsRun());
    assertTrue(report.messages().stream().anyMatch(line -> line.startsWith("error:")));
  }

  @Test
  void externalScriptsAreLoadedFromTheResolvedAddress() {
    var asked = new ArrayList<URI>();
    ScriptFetcher fetcher =
        uri -> {
          asked.add(uri);
          return Optional.of("document.getElementById('a').textContent = 'from file';");
        };
    var document = parse("<p id='a'>x</p><script src='app.js'></script>");

    new RhinoScriptRunner(fetcher, uri -> true, ScriptLimits.defaults()).run(document);

    assertEquals(List.of(URI.create("https://example.test/dir/app.js")), asked);
    assertEquals("from file", text(document, "a"));
  }

  @Test
  void onlyWebScriptAddressesAreFetchedAndOnlyUpToTheLimit() {
    var asked = new ArrayList<URI>();
    ScriptFetcher fetcher =
        uri -> {
          asked.add(uri);
          return Optional.empty();
        };
    var document =
        parse(
            "<script src='file:///etc/passwd'></script><script src='a.js'></script>"
                + "<script src='b.js'></script><script src='c.js'></script>");
    var limits = new ScriptLimits(1000, 2, 1_000_000, 100, 200);

    new RhinoScriptRunner(fetcher, uri -> true, limits).run(document);

    assertEquals(2, asked.size());
  }

  @Test
  void modulesAndOtherTypesAreNotRun() {
    var document =
        parse(
            "<p id='a'>x</p><script type='module'>document.getElementById('a').textContent='m';</script>"
                + "<script type='application/json'>{\"a\":1}</script>");

    var report = runner().run(document);

    assertEquals("x", text(document, "a"));
    assertEquals(0, report.scriptsRun());
  }

  @Test
  void whenScriptsAreOffNothingRuns() {
    var document = parse("<p id='a'>x</p><script>document.getElementById('a').textContent='y';</script>");

    var report = new RhinoScriptRunner(ScriptFetcher.NONE, uri -> false, ScriptLimits.defaults()).run(document);

    assertEquals("x", text(document, "a"));
    assertEquals(0, report.scriptsRun());
  }

  @Test
  void deviceFeaturesAnswerThatPermissionIsDenied() {
    var document =
        parse(
            "<p id='a'></p><script>"
                + "var out = [];"
                + "navigator.geolocation.getCurrentPosition(function () { out.push('ok'); },"
                + "  function (e) { out.push('geo' + e.code); });"
                + "out.push(Notification.permission);"
                + "navigator.mediaDevices.getUserMedia({video: true}).catch(function (e) { out.push(e.name); });"
                + "document.getElementById('a').textContent = out.join(',');"
                + "setTimeout(function () { document.getElementById('a').textContent = out.join(','); }, 1);"
                + "</script>");

    runner().run(document);

    assertEquals("geo1,denied,NotAllowedError", text(document, "a"));
  }

  @Test
  void aBadSelectorIsAnErrorNotACrash() {
    var document = parse("<p id='a'>x</p><script>document.querySelector('p:hover');</script>");

    var report = runner().run(document);

    assertFalse(report.messages().isEmpty());
  }

  @Test
  void deepRecursionIsStopped() {
    var document = parse("<p id='a'>x</p><script>function f() { f(); } f();</script>");

    var report = runner().run(document);

    assertEquals("x", text(document, "a"));
    assertFalse(report.messages().isEmpty());
  }
}
