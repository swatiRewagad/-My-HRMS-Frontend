package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.entity.SenderResolverType;
import lombok.Builder;
import org.apache.james.mime4j.dom.Message;

import java.time.Instant;
import java.util.List;

/** What a {@link SenderResolver} produces when it's confident it found the original sender.
 *  {@code canonicalMessage} is the mime4j Message the REST of the pipeline (body extraction,
 *  attachments, complaint-ref matching) should read from — for the redirect/custom-header/inline
 *  cases that's the same top-level message that arrived; for nested-message and TNEF it's the
 *  decoded inner message, since that's what actually carries the citizen's content. */
@Builder
public record ResolvedSender(
        SenderResolverType resolverType,
        String originalFrom,
        List<String> originalTo,
        String originalSubject,
        Instant originalSentAt,
        String replyTo,
        Message canonicalMessage
) {}
