package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of a regulated-entity bulk import. Rows are skipped rather than rejected when they are
 * nameless, carry a department other than CEPC or RBIO, or duplicate an entity already on file, so
 * {@link #skipped} being non-zero is normal and not an error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResponse {

    private int imported;
    private int total;
    private int skipped;
}
