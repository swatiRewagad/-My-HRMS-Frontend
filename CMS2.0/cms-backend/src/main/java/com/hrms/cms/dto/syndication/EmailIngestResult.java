package com.hrms.cms.dto.syndication;

/**
 * What ingesting an inbound mail produced. Ingestion either parks a draft for a DEO to assess
 * ({@link EmailDraftResponse}) or decides the mail is not a complaint at all and closes it on the spot
 * ({@link EmailAutoCloseResponse}) — two genuinely different payloads from one endpoint, named here so
 * callers see a union rather than an untyped body.
 */
public interface EmailIngestResult {
}
