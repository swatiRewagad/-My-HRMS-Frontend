package com.rbi.cms.common.enums;

public class RoleConstants {

    private RoleConstants() {}

    public static final String RBIO_ADMIN = "RBIO_ADMIN";

    public static final String RBIO_DO = "RBIO_DO";

    public static final String RBIO_REVIEWER = "RBIO_REVIEWER";

    public static final String RBIO_DEPUTY_OMBUDSMAN = "RBIO_DEPUTY_OMBUDSMAN";

    public static final String RBIO_OMBUDSMAN = "RBIO_OMBUDSMAN";

    public static final String[] ALL_RBIO_ROLES = {
            RBIO_ADMIN, RBIO_DO, RBIO_REVIEWER, RBIO_DEPUTY_OMBUDSMAN, RBIO_OMBUDSMAN
    };
}
