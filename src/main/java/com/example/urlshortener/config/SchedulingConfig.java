package com.example.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} for the click flush worker (design §3.6). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
