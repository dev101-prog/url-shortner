package com.example.urlshortener.service.port;

import com.example.urlshortener.service.domain.ClickEvent;

/** Destination for click events off the hot path (design §3.6; URL-FR-7.2). */
public interface ClickSink {

  /**
   * Enqueues a click. Must never block.
   *
   * @param event the click
   * @return false if the event was dropped (buffer full)
   */
  boolean offer(ClickEvent event);
}
