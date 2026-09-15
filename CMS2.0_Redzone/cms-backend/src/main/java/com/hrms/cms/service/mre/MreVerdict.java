package com.hrms.cms.service.mre;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class MreVerdict implements Serializable {

    public enum OverallSignal {
        OBJECTIVELY_CLEAR,
        NEEDS_HUMAN_REVIEW,
        OBJECTIVELY_NON_MAINTAINABLE
    }

    private final int ruleVersion;
    private final OverallSignal overallSignal;
    private final List<GroundVerdict> groundVerdicts;
    private final Map<String, Object> timeline;
    private final String summary;

    public boolean hasAnyFail() {
        return groundVerdicts.stream().anyMatch(g -> g.getStatus() == GroundVerdict.Status.FAIL);
    }

    public boolean hasAnyNeedsReview() {
        return groundVerdicts.stream().anyMatch(g -> g.getStatus() == GroundVerdict.Status.NEEDS_REVIEW);
    }

    public List<GroundVerdict> getFailedGrounds() {
        return groundVerdicts.stream().filter(g -> g.getStatus() == GroundVerdict.Status.FAIL).toList();
    }

    public String getTriageSignal() {
        return switch (overallSignal) {
            case OBJECTIVELY_CLEAR -> "GREEN";
            case NEEDS_HUMAN_REVIEW -> "AMBER";
            case OBJECTIVELY_NON_MAINTAINABLE -> "RED";
        };
    }
}
