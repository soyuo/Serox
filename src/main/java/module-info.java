module kr.soyuo.serox {
  requires javafx.controls;
  requires javafx.swing;
  requires com.github.weisj.jsvg;
  requires java.desktop;
  requires java.net.http;
  requires java.logging;
  requires java.sql;
  requires com.google.gson;
  requires org.xerial.sqlitejdbc;

  exports kr.soyuo.serox.app;
}
