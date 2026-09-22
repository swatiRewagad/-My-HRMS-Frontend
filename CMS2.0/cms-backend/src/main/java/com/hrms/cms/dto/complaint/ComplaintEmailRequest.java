package com.hrms.cms.dto.complaint;

import com.rbi.cms.common.enums.DeliveryStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A mail composed against a complaint from the officer's Email Communication tab, used both to create a
 * draft and to send. {@link #status} is the only thing that distinguishes the two, so it is constrained
 * here rather than trusted from the client.
 *
 * <p>The Size limits mirror the SIMULATED_EMAILS column widths: without them an oversized subject or
 * recipient list fails on the insert as a data-truncation error instead of as a readable 400.</p>
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ComplaintEmailRequest {

    /** One address, or several separated by comma/semicolon - the compose form uses a single free-text field. */
    public static final String EMAIL_LIST =
            "^\\s*[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}\\s*([,;]\\s*[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}\\s*)*$";

    /** As EMAIL_LIST, but an omitted CC/BCC arrives as an empty string rather than as null. */
    public static final String OPTIONAL_EMAIL_LIST = "(^\\s*$)|" + EMAIL_LIST;

    public static final String DRAFT = "DRAFT";
    public static final String SENT = "SENT";

    @NotBlank(message = "From address is required")
    @Email(message = "From must be a valid email address")
    @Size(max = 200, message = "From address must be at most 200 characters")
    private String from;

    @NotBlank(message = "At least one recipient is required")
    @Pattern(regexp = EMAIL_LIST, message = "To must be a valid email address, or several separated by commas")
    @Size(max = 200, message = "Recipient list must be at most 200 characters")
    private String to;

    @Pattern(regexp = OPTIONAL_EMAIL_LIST, message = "CC must be a valid email address, or several separated by commas")
    @Size(max = 500, message = "CC list must be at most 500 characters")
    private String cc;

    @Pattern(regexp = OPTIONAL_EMAIL_LIST, message = "BCC must be a valid email address, or several separated by commas")
    @Size(max = 500, message = "BCC list must be at most 500 characters")
    private String bcc;

    @NotBlank(message = "Subject is required")
    @Size(max = 500, message = "Subject must be at most 500 characters")
    private String subject;

    @NotBlank(message = "Email body is required")
    @Size(max = 20000, message = "Email body must be at most 20000 characters")
    private String body;

    /** Absent means SENT: composing and pressing Send is the common case. */
    @Pattern(regexp = DRAFT + "|" + SENT, message = "Status must be either DRAFT or SENT")
    private String status;

    /** Set when this mail is a reply, so the new message joins the thread it answers. */
    private Long inReplyToId;

    public boolean isDraft() {
        return DRAFT.equalsIgnoreCase(status);
    }

    /**
     * Maps the officer's intent onto the stored lifecycle, in the one place that mapping happens.
     *
     * <p>Pressing Send yields PENDING, not SENT: cms-notification-service decides whether it becomes SENT
     * or FAILED. That asymmetry is why {@link #status} keeps its DRAFT|SENT vocabulary - the client states
     * what it wants, never what the server has established.</p>
     */
    public DeliveryStatus resolvedStatus() {
        return isDraft() ? DeliveryStatus.DRAFT : DeliveryStatus.PENDING;
    }
}
