package com.example.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the URL shortener service (design §7.1). */
@SpringBootApplication
public class UrlShortenerApplication {

  /**
   * Starts the application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(UrlShortenerApplication.class, args);
  }
}
