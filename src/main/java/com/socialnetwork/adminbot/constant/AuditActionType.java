package com.socialnetwork.adminbot.constant;

/**
 * Типы действий администраторов для audit log
 */
public final class AuditActionType {

    // User actions
    public static final String BLOCK_USER = "BLOCK_USER";
    public static final String UNBLOCK_USER = "UNBLOCK_USER";
    public static final String VIEW_USER = "VIEW_USER";
    public static final String DELETE_USER = "DELETE_USER";

    // Statistics actions
    public static final String VIEW_STATISTICS = "VIEW_STATISTICS";

    // Admin actions (для v2.0)
    public static final String CREATE_ADMIN = "CREATE_ADMIN";
    public static final String UPDATE_ADMIN = "UPDATE_ADMIN";
    public static final String DELETE_ADMIN = "DELETE_ADMIN";

    private AuditActionType() {
        throw new UnsupportedOperationException("Utility class");
    }
}
