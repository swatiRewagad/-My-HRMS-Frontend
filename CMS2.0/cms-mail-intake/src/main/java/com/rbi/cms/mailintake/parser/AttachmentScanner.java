package com.rbi.cms.mailintake.parser;

/** ClamAV (or any AV engine) hook. {@link NoOpAttachmentScanner} is the default when no real
 *  scanner is wired — see the brief: "ClamAV scan hook via an AttachmentScanner interface with a
 *  no-op default." */
public interface AttachmentScanner {

    ScanResult scan(byte[] content);

    enum Verdict { CLEAN, INFECTED, SCAN_UNAVAILABLE }

    record ScanResult(Verdict verdict, String detail) {
        public static ScanResult clean() {
            return new ScanResult(Verdict.CLEAN, null);
        }
    }
}
