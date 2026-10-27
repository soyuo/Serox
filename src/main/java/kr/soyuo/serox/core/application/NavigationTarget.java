package kr.soyuo.serox.core.application;

/** Classified destination produced from the browser address field. */
public sealed interface NavigationTarget permits DirectAddress, SearchQuery {}
