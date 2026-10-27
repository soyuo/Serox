package kr.soyuo.serox.browser.layout;

import kr.soyuo.serox.browser.css.ComputedStyle;
import kr.soyuo.serox.browser.dom.Element;

/** Positioned box-model geometry for one rendered element. */
public record LayoutBox(
    Element element,
    double x,
    double y,
    double width,
    double height,
    double contentX,
    double contentY,
    double contentWidth,
    double contentHeight,
    ComputedStyle style) {}
