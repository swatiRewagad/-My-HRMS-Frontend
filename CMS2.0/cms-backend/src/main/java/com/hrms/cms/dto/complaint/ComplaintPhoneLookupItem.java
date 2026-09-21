package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A row of the public "track my complaints by phone number" list. {@code comments} is the description
 * truncated to 50 characters, which is all the list shows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintPhoneLookupItem {

    private String complaintId;
    private String entityName;
    private String complaintDate;
    private String status;
    private String comments;
}
