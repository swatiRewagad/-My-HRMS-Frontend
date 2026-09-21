package com.hrms.cms.dto.routing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Answer to "has this regulated entity's registration been cancelled?", used to auto-flag a complaint
 * at intake.
 *
 * <p>{@code entity} and {@code message} describe the cancellation, so they are only meaningful when
 * {@code cancelled} is true; {@code NON_NULL} keeps them off the wire otherwise, which is what the
 * two {@code Map.of(...)} bodies this replaced did.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CancelledEntityResponse {

    /** Primitive so it is always present, including on the negative answer. */
    private boolean cancelled;

    /** The name as the master holds it, which may differ in case from the one that was looked up. */
    private String entity;

    /** Ready to display; null when nothing is cancelled. */
    private String message;

    public static CancelledEntityResponse notCancelled() {
        return CancelledEntityResponse.builder().cancelled(false).build();
    }
}
