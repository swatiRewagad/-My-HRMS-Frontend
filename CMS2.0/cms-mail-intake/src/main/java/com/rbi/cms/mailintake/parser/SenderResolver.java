package com.rbi.cms.mailintake.parser;

import org.apache.james.mime4j.dom.Message;

import java.util.Optional;

/**
 * One link in the ordered chain (redirect headers → custom header → nested message → TNEF →
 * inline forward block). Each resolver looks only at what it's specifically good at recognising
 * and returns empty rather than guessing — {@link SenderResolutionChain} tries them in order and
 * takes the first confident match. {@code depth} is the nested-message recursion depth (0 at the
 * top level), passed through so a resolver that itself recurses (NestedMessageSenderResolver) can
 * enforce cms.mail.intake.resolver.nested-message-depth-cap.
 */
public interface SenderResolver {

    Optional<ResolvedSender> resolve(Message message, int depth);
}
