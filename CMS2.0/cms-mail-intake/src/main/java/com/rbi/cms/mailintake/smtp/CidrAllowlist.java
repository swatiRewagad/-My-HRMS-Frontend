package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * This IS the authentication mechanism (brief PART 0 #4 — SPF/DKIM fail by design on
 * redirected/forwarded mail, so we don't gate on them). Every entry in
 * cms.mail.intake.allowlist.cidrs is parsed once at construction; a malformed entry fails
 * startup loudly rather than being silently skipped, since a silently-skipped allowlist entry
 * is a silent widening of who's trusted, not a narrowing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CidrAllowlist {

    private final MailIntakeProperties properties;
    private List<CidrBlock> blocks;

    private List<CidrBlock> blocks() {
        // Lazily parsed (not at construction) so a MailIntakeProperties validation failure
        // surfaces as the clearer @Validated error rather than a CidrBlock parse error racing it.
        if (blocks == null) {
            List<CidrBlock> parsed = new ArrayList<>();
            for (String cidr : properties.getAllowlist().getCidrs()) {
                parsed.add(CidrBlock.parse(cidr));
            }
            blocks = parsed;
        }
        return blocks;
    }

    public boolean isAllowed(InetAddress remote) {
        for (CidrBlock block : blocks()) {
            if (block.contains(remote)) {
                return true;
            }
        }
        log.warn("Rejected connection from off-allowlist IP: {}", remote.getHostAddress());
        return false;
    }

    private record CidrBlock(byte[] network, int prefixLength) {

        static CidrBlock parse(String cidr) {
            String[] parts = cidr.trim().split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "cms.mail.intake.allowlist.cidrs entry is not in CIDR form (address/prefix): " + cidr);
            }
            try {
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int prefix = Integer.parseInt(parts[1]);
                int maxPrefix = address.length * 8;
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException(
                            "Invalid prefix length " + prefix + " for " + cidr);
                }
                return new CidrBlock(address, prefix);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Unparseable CIDR entry: " + cidr, e);
            }
        }

        boolean contains(InetAddress candidate) {
            byte[] candidateBytes = candidate.getAddress();
            if (candidateBytes.length != network.length) {
                return false; // IPv4 candidate against an IPv6 block or vice versa
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (candidateBytes[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF00 >> remainingBits & 0xFF;
            return (candidateBytes[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
