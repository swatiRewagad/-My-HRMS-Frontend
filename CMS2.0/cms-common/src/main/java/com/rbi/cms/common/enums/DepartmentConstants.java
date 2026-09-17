package com.rbi.cms.common.enums;

import java.util.List;

public final class DepartmentConstants {

    private DepartmentConstants() {}

    public static final String DEPT_RBIO = "RBIO";

    public static final String DEPT_CEPC = "CEPC";

    public static final String DEPT_CRPC = "CRPC";

    public static final String DEPT_APPELLATE = "APPELLATE";

    public static final List<String> ALL_DEPARTMENTS =
            List.of(DEPT_RBIO, DEPT_CEPC, DEPT_CRPC, DEPT_APPELLATE);

    public static boolean isValid(String department) {
        return canonicalize(department) != null;
    }

    /**
     * Returns the canonical constant matching the given value ignoring case and surrounding
     * whitespace, or null if it is not a known department. Callers enforcing department scoping must
     * use the canonical form: the index stores these values exactly, so a {@code term} filter on a
     * differently-cased claim would match nothing and read as "no complaints".
     */
    public static String canonicalize(String department) {
        if (department == null || department.isBlank()) {
            return null;
        }
        String trimmed = department.trim();
        for (String known : ALL_DEPARTMENTS) {
            if (known.equalsIgnoreCase(trimmed)) {
                return known;
            }
        }
        return null;
    }
}
