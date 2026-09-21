package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintRegistrationAck {

    /** The complaint *number*, not the numeric id — this is the reference the complainant tracks with. */
    private String complaintId;
    private String status;
    private String registeredAt;
    private String slaDueDate;
    private String acknowledgementMessage;
}
