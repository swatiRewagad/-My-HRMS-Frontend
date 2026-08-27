package com.rbi.cms.mailintake.smtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Deliberately a raw socket client, not a mail library — GreenMail simulates a mail *server*
 *  for testing code that sends mail; here we ARE the server, so testing it needs an actual SMTP
 *  client with full protocol control (including sending deliberately adversarial sequences for
 *  the security tests). See the Stage 3 summary for why GreenMail doesn't fit this module. */
public class TestSmtpClient implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader in;
    private final OutputStream out;

    public TestSmtpClient(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        out = socket.getOutputStream();
    }

    public String readLine() throws IOException {
        return in.readLine();
    }

    /** Reads a possibly-multiline response ("250-...\r\n...250 OK"), returning the final line. */
    public String readResponse() throws IOException {
        String line;
        do {
            line = in.readLine();
        } while (line != null && line.length() > 3 && line.charAt(3) == '-');
        return line;
    }

    public void send(String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    /** Full happy-path transaction through EHLO/MAIL/RCPT so tests that only care about the DATA
     *  outcome don't each re-type the preamble. */
    public String deliver(String mailFrom, String rcptTo, String body) throws IOException {
        readResponse(); // banner
        send("EHLO relay.rbi.org.in");
        readResponse();
        send("MAIL FROM:<" + mailFrom + ">");
        readResponse();
        send("RCPT TO:<" + rcptTo + ">");
        readResponse();
        send("DATA");
        readResponse();
        send(body + "\r\n.");
        return readResponse();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
