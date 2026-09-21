package com.hrms.cms.dto.syndication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Acknowledgement for the create/upsert-draft call, which returns only enough for the caller to keep
 * editing the draft it just created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftCreatedResponse {

    private String draftId;
    private String status;
}
