package kr.soyuo.serox.core.application;

import kr.soyuo.serox.core.event.BrowserEventBus;

/** Application facade that coordinates tabs, navigation, and application events. */
public interface BrowserController extends BrowserCommandService {
  /** Returns the tab lifecycle boundary. */
  TabManager tabs();

  /** Returns the navigation lifecycle boundary. */
  NavigationManager navigation();

  /** Returns the event stream exposed to presentation adapters. */
  BrowserEventBus eventBus();
}
