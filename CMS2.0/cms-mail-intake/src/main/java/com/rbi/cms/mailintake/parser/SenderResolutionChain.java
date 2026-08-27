package com.rbi.cms.mailintake.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * First confident match wins. Spring injects {@code resolvers} pre-sorted by each resolver's
 * {@code @Order} value — 1 (redirect headers) through 5 (inline forward block) — matching the
 * priority the brief specifies. If none match, resolution is UNRESOLVED and the caller
 * (ParserPipeline) quarantines with UNRESOLVED_ORIGINAL_SENDER rather than guessing — brief
 * step 6: "Fail. ... Do not guess."
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SenderResolutionChain {

    private final List<SenderResolver> resolvers; // order guaranteed by @Order on each bean

    Optional<ResolvedSender> resolve(Message message) {
        for (SenderResolver resolver : resolvers) {
            Optional<ResolvedSender> result = tryResolver(resolver, message);
            if (result.isPresent()) {
                log.debug("Sender resolved by {}", result.get().resolverType());
                return result;
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedSender> tryResolver(SenderResolver resolver, Message message) {
        try {
            return resolver.resolve(message, 0);
        } catch (RuntimeException e) {
            // One resolver misbehaving on a pathological message must not sink the whole chain —
            // rule 4 in spirit: try the next resolver rather than failing the parse outright.
            log.warn("Resolver {} threw, trying the next one: {}", resolver.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
