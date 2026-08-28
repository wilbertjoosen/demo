package com.example.inventory.ports;

import com.example.inventory.model.InventoryItem;

/**
 * Outbound port for "this warehouse line's quantity just changed" — a raw fact, published on every
 * mutation. Low-stock detection isn't done here: it's derived downstream, from this event stream,
 * by InventoryStreamsTopology.
 */
public interface StockEventPort {

    void publishStockLevelChanged(InventoryItem item);
}
