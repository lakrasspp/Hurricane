package hurricane.nords;

import java.util.List;

public interface InventoryListener {
    void dirty();
    void initListeners(final List<InventoryListener> listeners);
    List<InventoryListener> listeners();
}