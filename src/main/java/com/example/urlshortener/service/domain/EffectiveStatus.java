package com.example.urlshortener.service.domain;

/** Status derived at read time from the persisted status and expiry (URL-FR-4.3). */
public enum EffectiveStatus {
  ACTIVE,
  INACTIVE,
  EXPIRED
}
