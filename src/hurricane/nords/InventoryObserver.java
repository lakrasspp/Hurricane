package hurricane.nords;

import java.util.List;

public interface InventoryObserver {
    List<InventoryListener> observers();
    void addListeners(final List<InventoryListener> listeners);
    void removeListeners(final List<InventoryListener> listeners);
}