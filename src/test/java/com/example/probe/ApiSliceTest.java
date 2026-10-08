package com.example.probe;

import com.example.urlshortener.api.error.ErrorEnvelopeWriter;
import com.example.urlshortener.config.AppProperties;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

/**
 * {@code @WebMvcTest} slice with the real filters, {@code GlobalExceptionHandler}, the real {@code
 * application.yml} Jackson settings, and the {@link ProbeController}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({ProbeController.class, ErrorEnvelopeWriter.class})
@EnableConfigurationProperties(AppProperties.class)
public @interface ApiSliceTest {}
