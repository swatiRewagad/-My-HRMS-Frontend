package com.rbi.cms.mailintake.parser;

/** A binary part pulled out of the MIME tree, before any of the size/count/scan/zip-bomb checks
 *  AttachmentProcessor applies. filename is exactly what the message declared — untrusted, never
 *  used to build a path (see AttachmentProcessor#sanitizeFilename and RawMessageStore). */
record RawAttachment(String filename, String declaredContentType, byte[] content) {}
