package com.rbi.cms.mailintake.spi;

/**
 * The extension point. This module's job ends at producing a {@link NormalisedInboundMail} and
 * calling this interface — CMS complaint-creation logic (drafts, DEO round-robin, duplicate
 * detection, conversion to a formal complaint) does not live in cms-mail-intake.
 *
 * The intended real implementation calls the existing
 * {@code com.rbi.cms.ingestion.email.service.EmailSyndicationService#ingestEmail} — either
 * in-process, if cms-mail-intake is packaged as a library inside cms-ingestion-service, or via
 * its existing {@code POST /api/v1/email-syndication/ingest} endpoint if cms-mail-intake is
 * deployed standalone (see Stage 1 findings). That adapter is a Stage 4/5 concern — kept out of
 * this module so cms-mail-intake has no compile-time dependency on cms-ingestion-service either
 * way.
 */
@FunctionalInterface
public interface InboundMailHandler {
    HandlerResult handle(NormalisedInboundMail mail);
}
