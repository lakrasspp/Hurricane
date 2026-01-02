package haven.automated;

import haven.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class InventorySorter implements Runnable {
	private final GameUI gui;
	private final Inventory inv;

	public InventorySorter(GameUI gui, Inventory inv) {
		this.gui = gui;
		this.inv = inv;
	}

	@Override
	public void run() {
		try {
			sortByName();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sortByName() {
		List<InvItem> items = new ArrayList<>();
		List<Integer> ignoreSlots = new ArrayList<>();

		// Collect all items
		for (Widget wdg = inv.child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				InvItem item = new InvItem((WItem) wdg);
				Coord sz = item.getSize();

				// If item is larger than 1x1, mark those slots as ignored
				if (sz.x > 1 || sz.y > 1) {
					Coord slot = item.getSlot();
					for (int y = 0; y < sz.y; y++) {
						for (int x = 0; x < sz.x; x++) {
							Integer slotIndex = coordToSloti(slot.add(x, y));
							if (slotIndex != null) {
								ignoreSlots.add(slotIndex);
							}
						}
					}
					continue; // Skip large items from sorting
				}
				items.add(item);
			}
		}

		if (items.isEmpty()) {
			return;
		}

		// Sort by slot position first, then by name
		items.sort(Comparator.comparing(InvItem::getSloti));
		items.sort(Comparator.comparing(InvItem::getName));

		// Perform the sorting by swapping items
		for (int i = 0; i < items.size(); i++) {
			InvItem invItem = items.get(i);
			InvItem currentItem = getItem(invItem.getSlot());

			// Calculate target slot index, skipping ignored slots
			int targetSloti = i;
			for (Integer ignoredSlot : ignoreSlots) {
				if (ignoredSlot <= targetSloti) {
					targetSloti++;
				}
			}

			Coord targetSlot = slotiToCoord(targetSloti);
			if (targetSlot == null) continue;

			// Check if item is already in the correct position
			if (invItem.equals(currentItem) && invItem.getSloti() != targetSloti) {
				// Pick up the item
				if (!invItem.take()) {
					break;
				}

				// Get whatever item is currently at target position
				InvItem displacedItem = getItem(targetSlot);

				// Drop at target position
				if (!drop(targetSlot)) {
					break;
				}

				// Handle the displaced item (chain of swaps)
				while (displacedItem != null && gui.vhand != null) {
					Integer newTargetIndex = getTargetIndex(items, displacedItem, ignoreSlots);
					if (newTargetIndex == null) {
						break;
					}

					Coord newTargetSlot = slotiToCoord(newTargetIndex);
					if (newTargetSlot == null) {
						break;
					}

					displacedItem = getItem(newTargetSlot);
					if (!drop(newTargetSlot)) {
						break;
					}
				}
			}
		}
	}

	private Integer getTargetIndex(List<InvItem> items, InvItem item, List<Integer> ignoreSlots) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).equals(item)) {
				int targetSloti = i;
				for (Integer ignoredSlot : ignoreSlots) {
					if (ignoredSlot <= targetSloti) {
						targetSloti++;
					}
				}
				return targetSloti;
			}
		}
		return null;
	}

	private InvItem getItem(Coord slot) {
		for (Widget wdg = inv.child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				WItem w = (WItem) wdg;
				Coord itemSlot = w.c.sub(1, 1).div(Inventory.sqsz);
				if (itemSlot.equals(slot)) {
					return new InvItem(w);
				}
			}
		}
		return null;
	}

	private boolean drop(Coord slot) {
		InvItem itemBefore = getItem(slot);
		inv.wdgmsg("drop", slot);

		// Wait for the drop to complete (item at slot changes)
		for (int sleep = 0; sleep < 1000; sleep += 10) {
			InvItem itemAfter = getItem(slot);
			if (itemAfter == null || !itemAfter.equals(itemBefore)) {
				return true;
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				return false;
			}
		}
		return true;
	}

	private Coord slotiToCoord(int slot) {
		Coord c = new Coord();
		int index = 0;
		for (c.y = 0; c.y < inv.isz.y; c.y++) {
			for (c.x = 0; c.x < inv.isz.x; c.x++) {
				if (inv.sqmask == null || !inv.sqmask[c.y * inv.isz.x + c.x]) {
					if (slot == index) {
						return new Coord(c);
					}
					index++;
				}
			}
		}
		return null;
	}

	private Integer coordToSloti(Coord slot) {
		Coord c = new Coord();
		int index = 0;
		for (c.y = 0; c.y < inv.isz.y; c.y++) {
			for (c.x = 0; c.x < inv.isz.x; c.x++) {
				if (inv.sqmask == null || !inv.sqmask[c.y * inv.isz.x + c.x]) {
					if (slot.x == c.x && slot.y == c.y) {
						return index;
					}
					index++;
				}
			}
		}
		return null;
	}

	private class InvItem {
		private final WItem wItem;
		private String name;
		private Coord slot;
		private Integer sloti;
		private Coord size;

		public InvItem(WItem wItem) {
			this.wItem = wItem;
		}

		public WItem getWItem() {
			return wItem;
		}

		public String getName() {
			if (name == null) {
				try {
					name = wItem.item.getname();
				} catch (Loading e) {
					name = "???";
				}
			}
			return name;
		}

		public Coord getSlot() {
			if (slot == null) {
				slot = wItem.c.sub(1, 1).div(Inventory.sqsz);
			}
			return slot;
		}

		public Integer getSloti() {
			if (sloti == null) {
				sloti = coordToSloti(getSlot());
			}
			return sloti != null ? sloti : 0;
		}

		public Coord getSize() {
			if (size == null) {
				size = wItem.sz.div(Inventory.sqsz);
			}
			return size;
		}

		public boolean take() {
			for (int attempts = 0; attempts < 5; attempts++) {
				wItem.item.wdgmsg("take", Coord.z);

				// Wait for hand to be occupied
				for (int sleep = 0; sleep < 1000; sleep += 10) {
					if (gui.vhand != null) {
						return true;
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						return false;
					}
				}
			}
			return false;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof InvItem)) return false;
			InvItem invItem = (InvItem) o;
			return getWItem().equals(invItem.getWItem());
		}

		@Override
		public int hashCode() {
			return Objects.hash(wItem);
		}
	}
}
