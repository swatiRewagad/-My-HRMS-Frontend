package com.rbi.cms.mailintake.parser;

/** Registered via {@code AttachmentScannerConfig}'s {@code @Bean @ConditionalOnMissingBean}
 *  method, not {@code @Component} directly — see LoggingInboundMailHandler's Javadoc for why. */
class NoOpAttachmentScanner implements AttachmentScanner {

    @Override
    public ScanResult scan(byte[] content) {
        return ScanResult.clean();
    }
}
