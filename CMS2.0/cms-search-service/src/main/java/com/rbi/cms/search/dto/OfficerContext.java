package com.rbi.cms.search.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficerContext {

    private String userId;
    private String displayName;
    private String roleGroup;
    private String regionalOffice;
}
