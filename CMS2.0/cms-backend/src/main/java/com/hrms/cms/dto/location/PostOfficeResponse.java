package com.hrms.cms.dto.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One post office serving a pincode, used to auto-fill state and district on the address forms.
 *
 * <p>This used to be emitted with PascalCase keys ({@code Name}, {@code BranchType}, …) inside a
 * one-element list carrying {@code Status}/{@code Message}, mimicking the public India Post API that
 * the lookup once proxied. It reads the local PINCODE table now, so the mimicry has been dropped: the
 * outcome lives in the {@code ApiResponse} envelope and the post offices are its {@code data}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostOfficeResponse {

    private String name;
    private String district;
    private String state;
    private String region;
    private String division;
    /** Post-office class, e.g. {@code "Sub Office"}. Shown to officers as the branch category. */
    private String branchType;
    private String pincode;
}
