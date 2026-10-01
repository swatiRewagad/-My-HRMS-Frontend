package com.hrms.cms.service.mre;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class GroundVerdict implements Serializable {

    public enum Status {
        PASS,
        FAIL,
        NEEDS_REVIEW,
        NOT_APPLICABLE
    }

    private final MreGround ground;
    private final Status status;
    private final String clause;
    private final String reason;

    public static GroundVerdict pass(MreGround ground, String reason) {
        return GroundVerdict.builder()
                .ground(ground).status(Status.PASS).clause(ground.getClause()).reason(reason).build();
    }

    public static GroundVerdict fail(MreGround ground, String reason) {
        return GroundVerdict.builder()
                .ground(ground).status(Status.FAIL).clause(ground.getClause()).reason(reason).build();
    }

    public static GroundVerdict needsReview(MreGround ground, String reason) {
        return GroundVerdict.builder()
                .ground(ground).status(Status.NEEDS_REVIEW).clause(ground.getClause()).reason(reason).build();
    }

    public static GroundVerdict notApplicable(MreGround ground) {
        return GroundVerdict.builder()
                .ground(ground).status(Status.NOT_APPLICABLE).clause(ground.getClause()).reason("Not applicable").build();
    }
}
