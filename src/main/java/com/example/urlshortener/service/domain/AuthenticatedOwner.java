package com.example.urlshortener.service.domain;

/**
 * The caller identified by a valid API key (URL-NFR-4.1).
 *
 * @param ownerId owner id
 * @param apiKeyId id of the API key used
 */
public record AuthenticatedOwner(long ownerId, long apiKeyId) {}
