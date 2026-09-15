package com.rbi.cms.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

public final class ComplaintIdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "CMP";
    private static final AtomicLong SEQUENCE = new AtomicLong(1);

    private ComplaintIdGenerator() {
    }

    public static String generate() {
        String datePart = LocalDate.now().format(FORMATTER);
        long seq = SEQUENCE.getAndIncrement();
        return String.format("%s-%s-%06d", PREFIX, datePart, seq);
    }
}
