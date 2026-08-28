package com.example.inventory.policy;

/**
 * Decides whether a warehouse line's quantity counts as "needs restocking". A separate strategy
 * (rather than a hardcoded comparison in the service layer) so alternative policies — percentage of
 * max capacity, sales-velocity based, per-category thresholds — can be swapped in without touching
 * {@code InventoryServiceImpl}.
 */
public interface ReorderPolicy {

    boolean isLowStock(int quantity);

    /** The threshold this policy is currently applying — surfaced so callers can attach it to the alert payload. */
    int threshold();
}
