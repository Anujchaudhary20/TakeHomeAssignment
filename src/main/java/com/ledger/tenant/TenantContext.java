package com.ledger.tenant;

import java.util.function.Supplier;

/**
 * ThreadLocal storage holding the active tenant identifier for the current execution context.
 */
public final class TenantContext {

    public static final String DEFAULT_TENANT = "SYSTEM_UNASSIGNED";
    private static final ThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(tenantId.trim());
        }
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static String getRequiredTenantId() {
        String tenant = CURRENT_TENANT.get();
        if (tenant == null || tenant.trim().isEmpty() || DEFAULT_TENANT.equals(tenant)) {
            throw new IllegalStateException("Tenant context is required but not set for this operation.");
        }
        return tenant;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static <T> T runWithTenant(String tenantId, Supplier<T> operation) {
        String previous = getTenantId();
        try {
            setTenantId(tenantId);
            return operation.get();
        } finally {
            if (previous != null) {
                setTenantId(previous);
            } else {
                clear();
            }
        }
    }

    public static void runWithTenant(String tenantId, Runnable operation) {
        runWithTenant(tenantId, () -> {
            operation.run();
            return null;
        });
    }
}
