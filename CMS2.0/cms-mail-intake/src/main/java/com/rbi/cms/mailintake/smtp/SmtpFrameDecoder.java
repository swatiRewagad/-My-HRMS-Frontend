package com.rbi.cms.mailintake.smtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Two modes, toggled by {@link SmtpCommandHandler} via {@link #enterDataMode()}/
 * {@link #exitDataMode()}:
 *
 * <ul>
 *   <li>Command mode: emits one {@code String} per CRLF-terminated line, decoded as US-ASCII
 *   (SMTP commands are always 7-bit per RFC 5321 — this is safe and deliberate, unlike the
 *   message body).</li>
 *   <li>DATA mode: emits one {@code byte[]} — the complete message body, exactly as received,
 *   with SMTP dot-stuffing (RFC 5321 §4.5.2) undone. Never goes through a String/charset
 *   conversion, so non-UTF-8 body content survives untouched — decoding declared charsets is
 *   Stage 4's job, not this decoder's.</li>
 * </ul>
 *
 * DATA mode relies on Netty's {@link ByteToMessageDecoder} cumulation: if the terminator isn't
 * found yet, nothing is consumed from {@code in}, so the same (plus newly-arrived) bytes are
 * re-presented next call. {@code searchFrom} avoids re-scanning already-confirmed-clean bytes on
 * every partial read. The terminator search itself is a manual byte-by-byte scan via
 * {@code ByteBuf.getByte} — deliberately not {@code ByteBufUtil.indexOf(ByteBuf, ByteBuf)}, which
 * would require allocating a throwaway needle buffer on every call with nothing to release it.
 */
class SmtpFrameDecoder extends ByteToMessageDecoder {

    private static final byte[] TERMINATOR = "\r\n.\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EMPTY_BODY_TERMINATOR = ".\r\n".getBytes(StandardCharsets.US_ASCII);

    private final int maxLineLength;
    private final long maxDataBytes;

    private boolean dataMode = false;
    private int searchFrom = 0;

    SmtpFrameDecoder(int maxLineLength, long maxDataBytes) {
        this.maxLineLength = maxLineLength;
        this.maxDataBytes = maxDataBytes;
    }

    void enterDataMode() {
        dataMode = true;
        searchFrom = 0;
    }

    void exitDataMode() {
        dataMode = false;
        searchFrom = 0;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (dataMode) {
            decodeData(in, out);
        } else {
            decodeCommandLine(in, out);
        }
    }

    private void decodeCommandLine(ByteBuf in, List<Object> out) {
        int lf = ByteBufUtil.indexOf(in, in.readerIndex(), in.writerIndex(), (byte) '\n');
        if (lf < 0) {
            if (in.readableBytes() > maxLineLength) {
                throw new TooLongFrameException("SMTP command line exceeded " + maxLineLength + " bytes");
            }
            return; // wait for more bytes
        }
        int length = lf - in.readerIndex();
        if (length > 0 && in.getByte(lf - 1) == '\r') {
            length--; // strip trailing \r
        }
        String line = in.toString(in.readerIndex(), length, StandardCharsets.US_ASCII);
        in.readerIndex(lf + 1);
        out.add(line);
    }

    private void decodeData(ByteBuf in, List<Object> out) {
        int available = in.readableBytes();
        if (available > maxDataBytes) {
            throw new TooLongFrameException("Message body exceeded configured max-message-size-bytes");
        }

        // Empty-message case: DATA content is immediately just ".\r\n" with nothing before it.
        if (matchesAt(in, in.readerIndex(), EMPTY_BODY_TERMINATOR)) {
            in.readerIndex(in.readerIndex() + EMPTY_BODY_TERMINATOR.length);
            out.add(new byte[0]);
            return;
        }

        int scanFrom = Math.max(in.readerIndex(), in.readerIndex() + searchFrom - TERMINATOR.length + 1);
        int idx = indexOf(in, scanFrom, in.writerIndex(), TERMINATOR);
        if (idx < 0) {
            searchFrom = in.readableBytes();
            return; // terminator not found yet — wait for more bytes, nothing consumed
        }

        int bodyLength = idx - in.readerIndex() + 2; // include the \r\n that precedes the "."
        byte[] rawWithStuffing = new byte[bodyLength];
        in.getBytes(in.readerIndex(), rawWithStuffing);
        in.readerIndex(idx + TERMINATOR.length);
        searchFrom = 0;

        out.add(undoDotStuffing(rawWithStuffing));
    }

    private static boolean matchesAt(ByteBuf buf, int at, byte[] needle) {
        if (buf.writerIndex() - at < needle.length) return false;
        for (int i = 0; i < needle.length; i++) {
            if (buf.getByte(at + i) != needle[i]) return false;
        }
        return true;
    }

    private static int indexOf(ByteBuf buf, int fromIndex, int toExclusive, byte[] needle) {
        int lastPossibleStart = toExclusive - needle.length;
        for (int i = fromIndex; i <= lastPossibleStart; i++) {
            if (matchesAt(buf, i, needle)) {
                return i;
            }
        }
        return -1;
    }

    /** RFC 5321 §4.5.2: a sender that has to transmit a genuine line starting with "." prefixes it
     *  with an extra "." so it's never confused with the terminator; we undo exactly that here. */
    private static byte[] undoDotStuffing(byte[] body) {
        if (body.length == 0) return body;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(body.length);
        int i = 0;
        while (i < body.length) {
            if (body[i] == '.' && (i == 0 || body[i - 1] == '\n')) {
                i++; // drop exactly one leading dot on this line
                continue;
            }
            int lineEnd = indexOfNewlineOrEnd(body, i);
            out.write(body, i, lineEnd - i);
            i = lineEnd;
        }
        return out.toByteArray();
    }

    private static int indexOfNewlineOrEnd(byte[] body, int from) {
        for (int i = from; i < body.length; i++) {
            if (body[i] == '\n') return i + 1;
        }
        return body.length;
    }
}
