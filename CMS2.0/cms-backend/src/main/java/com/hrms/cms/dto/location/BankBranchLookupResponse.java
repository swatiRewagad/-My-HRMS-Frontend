package com.hrms.cms.dto.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Branches of one regulated entity within a pincode, sourced from the IFSC registry.
 *
 * <p>{@code matchedBank} is the part callers actually branch on: an unrecognised entity name and a
 * recognised bank with no branch in that pincode both yield an empty list, but only the second means
 * "this bank has no branch here". It lived at the top level of the old {@code Map} alongside a key
 * literally named {@code data}; both now sit inside the envelope's payload, so {@code res.data} can no
 * longer resolve to the branch list by accident.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankBranchLookupResponse {

    /** False when no bank matched the entity name, in which case {@code bankName} is null. */
    private boolean matchedBank;

    /** The registry's name for the matched bank, which may differ from the name searched for. */
    private String bankName;

    @Builder.Default
    private List<Branch> branches = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Branch {
        private String ifsc;
        private String branchName;
        private String address;
        private String city;
        private String district;
        private String state;
        private String pincode;
    }
}
