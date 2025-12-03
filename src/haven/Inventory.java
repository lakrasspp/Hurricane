/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import haven.res.ui.tt.q.qbuff.QBuff;
import hurricane.nords.DTarget2;
import hurricane.nords.InventoryListener;
import hurricane.nords.InventoryObserver;
import haven.res.ui.stackinv.ItemStack;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.awt.image.WritableRaster;
import java.util.stream.Collectors;

public class Inventory extends Widget implements DTarget, InventoryListener, InventoryObserver {
    public static final Coord sqsz = UI.scale(new Coord(32, 32)).add(1, 1);
    public static final Tex invsq = Resource.loadtex("gfx/hud/invsq");
    public boolean dropul = true;
	public final AltInventory ainv;
    public Coord isz;
    public boolean[] sqmask = null;
    public Map<GItem, WItem> wmap = new HashMap<GItem, WItem>();
	public final Map<String, Tex> cached = new HashMap<>();
	public static Set<String> PLAYER_INVENTORY_NAMES = new HashSet<>(Arrays.asList("Inventory", "Belt", "Equipment", "Character Sheet", "Study"));

	public static final Comparator<WItem> ITEM_COMPARATOR_ASC = new Comparator<WItem>() {
		@Override
		public int compare(WItem o1, WItem o2) {

			double q1 = o1.item.getQBuff() != null ? o1.item.getQBuff().q : 0;
			double q2 = o2.item.getQBuff() != null ? o2.item.getQBuff().q : 0;

			return Double.compare(q1, q2);
		}
	};
	public static final Comparator<WItem> ITEM_COMPARATOR_DESC = new Comparator<WItem>() {
		@Override
		public int compare(WItem o1, WItem o2) {
			return ITEM_COMPARATOR_ASC.compare(o2, o1);
		}
	};

	// ND: WHY is this happening when there's literally a texture resource for this?
	// ND: This affects the menugrid slots color, I'm basically replacing it with the inventory square texture
//    static {
//	Coord sz = sqsz.add(1, 1);
//	WritableRaster buf = PUtils.imgraster(sz);
//	for(int i = 1, y = sz.y - 1; i < sz.x - 1; i++) {
//	    buf.setSample(i, 0, 0, 20); buf.setSample(i, 0, 1, 28); buf.setSample(i, 0, 2, 21); buf.setSample(i, 0, 3, 167);
//	    buf.setSample(i, y, 0, 20); buf.setSample(i, y, 1, 28); buf.setSample(i, y, 2, 21); buf.setSample(i, y, 3, 167);
//	}
//	for(int i = 1, x = sz.x - 1; i < sz.y - 1; i++) {
//	    buf.setSample(0, i, 0, 20); buf.setSample(0, i, 1, 28); buf.setSample(0, i, 2, 21); buf.setSample(0, i, 3, 167);
//	    buf.setSample(x, i, 0, 20); buf.setSample(x, i, 1, 28); buf.setSample(x, i, 2, 21); buf.setSample(x, i, 3, 167);
//	}
//	for(int y = 1; y < sz.y - 1; y++) {
//	    for(int x = 1; x < sz.x - 1; x++) {
//		buf.setSample(x, y, 0, 36); buf.setSample(x, y, 1, 52); buf.setSample(x, y, 2, 38); buf.setSample(x, y, 3, 125);
//	    }
//	}
//	invsq = new TexI(PUtils.rasterimg(buf));
//    }

    @RName("inv")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Inventory((Coord)args[0]));
	}
    }

    public void draw(GOut g) {
	Coord c = new Coord();
	int mo = 0;
	for(c.y = 0; c.y < isz.y; c.y++) {
	    for(c.x = 0; c.x < isz.x; c.x++) {
		if((sqmask != null) && sqmask[mo++]) {
		    g.chcolor(64, 64, 64, 255);
		    g.image(invsq, c.mul(sqsz));
		    g.chcolor();
		} else {
		    g.image(invsq, c.mul(sqsz));
		}
			if(OptWnd.showInventoryNumbers.a) {
				g.aimage((Tex) this.cached.computeIfAbsent("" + (c.y * this.isz.x + c.x + 1), (s) -> {
					return Text.render(s, new Color(255, 255, 255, 100)).tex();
				}), c.mul(sqsz).add(invsq.sz().div(2)), 0.5, 0.5);
			}
	    }
	}
	super.draw(g);
    }
	
    public Inventory(Coord sz) {
	super(sqsz.mul(sz).add(1, 1));
	isz = sz;
	ainv = new AltInventory(this);
	add(ainv, Coord.of(this.sz.x, 0));
	ainv.hide();
    }
    
    public boolean mousewheel(MouseWheelEvent ev) {
	if(ui.modshift) {
	    Inventory minv = getparent(GameUI.class).maininv;
	    if(minv != this) {
		if(ev.a < 0)
		    wdgmsg("invxf", minv.wdgid(), 1);
		else if(ev.a > 0)
		    minv.wdgmsg("invxf", this.wdgid(), 1);
	    }
	}
	return(true);
    }
    
    public void addchild(Widget child, Object... args) {
	add(child);
	Coord c = (Coord)args[0];
	if(child instanceof GItem) {
	    GItem i = (GItem)child;
	    wmap.put(i, add(new WItem(i), c.mul(sqsz).add(1, 1)));
		i.addListeners(listeners());
		observers().forEach(InventoryListener::dirty);
	}
    }
    
    public void cdestroy(Widget w) {
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
	    ui.destroy(wmap.remove(i));
		i.removeListeners(listeners());
		observers().forEach(InventoryListener::dirty);
	}
    }

	@Override
	protected void added() {
		super.added();
		if (ainv.visible()) {
			Coord max = sqsz.mul(isz).add(1, 1);
			max.x = Math.max(max.x, ainv.c.x + ainv.sz.x);
			max.y = Math.max(max.y, ainv.c.y + ainv.sz.y);
			resize(max);
		}
		listeners.add(this);
	}

	@Override
	public void reqdestroy() {
		super.reqdestroy();
		listeners.remove(this);
	}
    public boolean drop(Coord cc, Coord ul) {
	Coord dc;
	if(dropul)
	    dc = ul.add(sqsz.div(2)).div(sqsz);
	else
	    dc = cc.div(sqsz);
	wdgmsg("drop", dc);
	return(true);
    }
	
    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }
	
    public void uimsg(String msg, Object... args) {
	if(msg == "sz") {
	    isz = (Coord)args[0];
	    resize(invsq.sz().add(UI.scale(new Coord(-1, -1))).mul(isz).add(UI.scale(new Coord(1, 1))));
	    sqmask = null;
	} else if(msg == "mask") {
	    boolean[] nmask;
	    if(args[0] == null) {
		nmask = null;
	    } else {
		nmask = new boolean[isz.x * isz.y];
		byte[] raw = (byte[])args[0];
		for(int i = 0; i < isz.x * isz.y; i++)
		    nmask[i] = (raw[i >> 3] & (1 << (i & 7))) != 0;
	    }
	    this.sqmask = nmask;
	} else if(msg == "mode") {
	    dropul = !Utils.bv(args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }

	public List<WItem> getAllItems() {
		List<WItem> items = new ArrayList<WItem>();
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				items.add((WItem) wdg);
			}
		}
		return items;
	}

	public List<WItem> getItemsExact(String... names) {
		List<WItem> items = new ArrayList<WItem>();
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				String wdgname = ((WItem)wdg).item.getname();
				for (String name : names) {
					if (wdgname.equals(name)) {
						items.add((WItem) wdg);
						break;
					}
				}
			}
		}
		return items;
	}

	public WItem getItemPrecise(String name) {
		if (name == null)
			return null;
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				String wdgname = ((WItem)wdg).item.getname();
				if (wdgname.equals(name))
					return (WItem) wdg;
			}
		}
		return null;
	}

	public WItem getItemPartial(String name) {
		if (name == null)
			return null;
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				String wdgname = ((WItem)wdg).item.getname();
				if (wdgname.contains(name))
					return (WItem) wdg;
			}
		}
		return null;
	}

	@Override
	public void wdgmsg(Widget sender, String msg, Object... args) {
		if(msg.equals("transfer-ordered")){
			processTransfer(getSame((GItem) args[0], (Boolean) args[1]));
		} else {
			super.wdgmsg(sender, msg, args);
		}
	}

	private static boolean isInPlayerInventory(WItem item) {
		Window window = item.getparent(Window.class);
		return window != null && Objects.equals("Inventory", window.cap);
	}

	private static List<Integer> getExternalInventoryIds(UI ui) {
		List<Inventory> inventories = ui.gui.getAllWindows()
				.stream()
				.flatMap(w -> w.children().stream())
				.filter(child -> child instanceof Inventory)
				.map(i -> (Inventory) i)
				.collect(Collectors.toList());

		List<Integer> externalInventoryIds = inventories
				.stream()
				.filter(i -> {
					Window window = i.getparent(Window.class);
					return window != null && !PLAYER_INVENTORY_NAMES.contains(window.cap);
				}).map(i -> i.wdgid())
				.collect(Collectors.toList());

		List<Integer> stockpileIds = ui.gui.getAllWindows()
				.stream()
				.map(i -> i.getchild(ISBox.class))
				.filter(Objects::nonNull)
				.map(Widget::wdgid)
				.collect(Collectors.toList());

		externalInventoryIds.addAll(stockpileIds);
		return externalInventoryIds;
	}

	private static void attemptTransferSplittingStack(List<Integer> externalInventoryIds, ItemStack stack) {
		for (Integer externalInventoryId : externalInventoryIds) {
			Object[] invxf2Args = new Object[3];
			invxf2Args[0] = 0;
			invxf2Args[1] = stack.order.size();
			invxf2Args[2] = externalInventoryId;

			stack.order.get(0).wdgmsg("invxf2", invxf2Args);
		}
	}

	private void processTransfer(List<WItem> items) {
		List<Integer> externalInventoryIds = getExternalInventoryIds(ui);
		for (WItem item : items){
			item.item.wdgmsg("transfer", Coord.z);

			Widget contents = item.item.contents;
			if (contents instanceof ItemStack && isInPlayerInventory(item)) {
				attemptTransferSplittingStack(externalInventoryIds, (ItemStack) contents);
			}
		}
	}

	private List<WItem> getSame(GItem item, Boolean ascending) {
		List<WItem> items = new ArrayList<>();
		try {
			String name = item.res.get().name;
			GSprite spr = item.spr();
			for(Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
				if(wdg.visible && wdg instanceof WItem) {
					WItem wItem = (WItem) wdg;
					GItem child = wItem.item;
					try {
						if(child.res.get().name.equals(name) && ((spr == child.spr()) || (spr != null && spr.same(child.spr())))) {
							items.add(wItem);
						}
					} catch (Loading e) {}
				}
			}
			Collections.sort(items, ascending ? ITEM_COMPARATOR_ASC : ITEM_COMPARATOR_DESC);
		} catch (Loading e) { }
		return items;
	}

	public Coord isRoom(int x, int y) {
		//check if there is a space for an x times y item, return coordinate where.
		Coord freespot = null;
		boolean[][] occumap = new boolean[isz.x][isz.y];
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				for (int i = 0; i < wdg.sz.x; i++) {
					for (int j = 0; j < wdg.sz.y; j++) {
						occumap[(wdg.c.x/sqsz.x+i/sqsz.x)][(wdg.c.y/sqsz.y+j/sqsz.y)] = true;
					}
				}
			}
		}
		//(NICE LOOPS)
		//Iterate through all spots in inventory
		superloop:
		for (int i = 0; i < isz.x; i++) {
			for (int j = 0; j < isz.y; j++) {
				boolean itsclear = true;
				//Check if there is X times Y free slots
				try {
					for (int k = 0; k < x; k++) {
						for (int l = 0; l < y; l++) {
							if (occumap[i+k][j+l] == true) {
								itsclear = false;
							}
						}
					}
				} catch (IndexOutOfBoundsException e) {
					itsclear = false;
				}

				if (itsclear) {
					freespot = new Coord(i,j);
					break superloop;
				}
			}
		}

		return freespot;
	}

	public int getFreeSpace() {
		int feespace = isz.x * isz.y;
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem)
				feespace -= (wdg.sz.x * wdg.sz.y) / (sqsz.x * sqsz.y);
		}
		return feespace;
	}

	public void openStacks() {
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				WItem w = (WItem) wdg;
				if (w.item.contents != null) w.item.showcontwnd(true);
			}
		}
	}

	public void closeStacks() {
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				WItem w = (WItem) wdg;
				if (w.item.contents != null) w.item.showcontwnd(false);
			}
		}
	}

	public Coord getFreeSlot() {
		int[][] invTable = new int[isz.x][isz.y];
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				WItem item = (WItem) wdg;
				for (int i = 0; i < item.sz.div(sqsz).y; i++)
					for (int j = 0; j < item.sz.div(sqsz).x; j++)
						invTable[item.c.div(sqsz).x + j][item.c.div(sqsz).y + i] = 1;
			}
		}
		int mo = 0;
		for (int i = 0; i < isz.y; i++) {
			for (int j = 0; j < isz.x; j++) {
				if ((sqmask != null) && sqmask[mo++]) continue;
				if (invTable[j][i] == 0)
					return (new Coord(j, i));
			}
		}
		return (null);
	}

	public List<WItem> getItemsPartial(String... names) {
		List<WItem> items = new ArrayList<WItem>();
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof WItem) {
				String wdgname = ((WItem)wdg).item.getname();
				for (String name : names) {
					if (name == null)
						continue;
					if (wdgname.contains(name)) {
						items.add((WItem) wdg);
						break;
					}
				}
			}
		}
		return items;
	}

	public static class AltInventory extends Widget implements DTarget2 {
		private static final Color even = new Color(255, 255, 255, 16);
		private static final Color odd = new Color(255, 255, 255, 32);

		private final Inventory inv;
		public final ItemGroupList list;
		public OldDropBox<Grouping> dropGroup = new OldDropBox<Grouping>(UI.scale(60), 16, UI.scale(16)) {
			@Override
			protected Grouping listitem(final int i) {
				return (Grouping.values()[i]);
			}

			@Override
			protected int listitems() {
				return (Grouping.values().length);
			}

			@Override
			protected void drawitem(final GOut g, final Grouping item, final int i) {
				Tex tex = Text.render(item.name).tex();
				g.image(tex, Coord.of(0, (itemh - tex.sz().y) / 2));
			}

			@Override
			public void change(Grouping item) {
				super.change(item);
				inv.dirty();
			}
		};
		public OldDropBox<Sorting> dropSort = new OldDropBox<Sorting>(UI.scale(60), 16, UI.scale(16)) {
			@Override
			protected Sorting listitem(final int i) {
				return (Sorting.values()[i]);
			}

			@Override
			protected int listitems() {
				return (Sorting.values().length);
			}

			@Override
			protected void drawitem(final GOut g, final Sorting item, final int i) {
				Tex tex = Text.render(item.name).tex();
				g.image(tex, Coord.of(0, (itemh - tex.sz().y) / 2));
			}

			@Override
			public void change(final int index) {
				super.change(index);
				inv.dirty();
			}
		};

		public AltInventory(final Inventory inv) {
			this.inv = inv;
			add(dropSort, Coord.of(0, 0)).settip("List Sorting");
			add(dropGroup, dropSort.pos("ur").adds(5, 0)).settip("Item Picking On Quality");
			list = add(new ItemGroupList(inv, this, UI.scale(150), 16, UI.scale(16)), dropSort.pos("bl"));
			list.resizeh(inv.sz.y);
			dropGroup.change(0);
			dropSort.change(0);
			super.pack();
		}

		@Override
		public void draw(final GOut g) {
			super.draw(g);
			g.chcolor(even);
			g.rect(Coord.z, g.sz());
			g.chcolor();
		}

		@Override
		public void tick(final double dt) {
			if (!visible()) return;
			super.tick(dt);
		}

		@Override
		public boolean drop(final WItem target, final Coord cc, final Coord ul) {
			for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
				if (wdg.visible()) {
					if (wdg instanceof DTarget) {
						Coord ccc = cc.sub(wdg.c);
						Coord ulc = ul.sub(wdg.c);
						if (ccc.isect(Coord.z, wdg.sz)) {
							if (((DTarget) wdg).drop(ccc, ulc))
								return (true);
						}
					} else if (wdg instanceof DTarget2) {
						Coord ccc = cc.sub(wdg.c);
						Coord ulc = ul.sub(wdg.c);
						if (ccc.isect(Coord.z, wdg.sz)) {
							if (((DTarget2) wdg).drop(target, ccc, ulc))
								return (true);
						}
					}
				}
			}
			return (false);
		}

		@Override
		public boolean iteminteract(final WItem target, final Coord cc, final Coord ul) {
			for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
				if (wdg.visible()) {
					if (wdg instanceof DTarget) {
						Coord ccc = cc.sub(wdg.c);
						Coord ulc = ul.sub(wdg.c);
						if (ccc.isect(Coord.z, wdg.sz)) {
							if (((DTarget) wdg).iteminteract(ccc, ulc))
								return (true); ;
						}
					} else if (wdg instanceof DTarget2) {
						Coord ccc = cc.sub(wdg.c);
						Coord ulc = ul.sub(wdg.c);
						if (ccc.isect(Coord.z, wdg.sz)) {
							if (((DTarget2) wdg).iteminteract(target, ccc, ulc))
								return (true);
						}
					}
				}
			}
			return (false);
		}

		public enum Grouping {
			NORMAL("Ascending", Comparator.comparingDouble(o -> {
				QBuff qb = ItemInfo.find(QBuff.class, o.item.info());
				return (qb == null ? 0.0 : qb.q);
			})),
			REVERSED("Descending", NORMAL.cmp.reversed());

			private final String name;
			private final Comparator<WItem> cmp;

			Grouping(String name, Comparator<WItem> cmp) {
				this.name = name;
				this.cmp = cmp;
			}
		}

		public enum Sorting {
			NONE("None", Comparator.comparingInt(g -> 0)),
			COUNT("Count", Comparator.<Group>comparingInt(g -> g.count).reversed()),
			NAME("Name", Comparator.comparing(g -> g.name)),
			RESNAME("ResName", Comparator.comparing(g -> g.resname)),
			Q("Quality", Comparator.<Group>comparingDouble(g -> g.q).reversed());

			private final String name;
			private final Comparator<Group> cmp;

			Sorting(String name, Comparator<Group> cmp) {
				this.name = name;
				this.cmp = cmp;
			}
		}

		public static class Group extends Widget {
			private final String name;
			private final String resname;
			private int count;
			private double q;
			private boolean nq;
			private GSprite spr;
			private TexI texture;
			private final List<WItem> items = Collections.synchronizedList(new ArrayList<>());
			private static final Tex qimg = Resource.remote().loadwait("ui/tt/q/quality").layer(Resource.imgc, 0).tex();

			public Group(final String name, final String resname) {
				this.name = name;
				this.resname = resname;
			}

			public void addItem(final WItem item) {
				items.add(item);
				count++;
				if (spr == null){
					spr = item.item.spr();
					if(spr instanceof GSprite.ImageSprite)
						texture = new TexI(((GSprite.ImageSprite) spr).image());
				}

			}

			public void calcQuality() {
				List<Double> stream = items.stream().mapToDouble(i -> {
					QBuff qb = ItemInfo.find(QBuff.class, i.item.info());
					return (qb == null ? 0 : qb.q);
				}).boxed().collect(Collectors.toList());
				double sum = stream.stream().mapToDouble(d -> d).sum();
				q = sum / items.size();
				nq = stream.stream().allMatch(d -> d != q);
			}

			private WItem takeFirst() {
				return (items.get(0));
			}

			@Override
			public boolean mousedown(MouseDownEvent ev) {/// ///
				return (takeFirst().mousedown(ev));
			}

			public boolean mousedown(Coord c, int button){
				MouseDownEvent ev = new MouseDownEvent(c, button);
				return (takeFirst().mousedown(ev));
			}

			@Override
			public boolean mouseup(MouseUpEvent ev) {
				return (takeFirst().mouseup(ev));
			}

			@Override
			public void mousemove(MouseMoveEvent ev) {
				takeFirst().mousemove(ev);
			}

			@Override
			public boolean mousewheel(MouseWheelEvent ev) {
				return (takeFirst().mousewheel(ev));
			}

			public boolean mousewheel(final Coord c, final int amount) {
				MouseWheelEvent ev = new MouseWheelEvent(c, amount);
				return (takeFirst().mousewheel(ev));
			}

			private static final Map<String, Tex> TEX = Collections.synchronizedMap(new WeakHashMap<>());
			private static Tex create(String text) {
				return (TEX.computeIfAbsent(text, s -> Text.render(s).tex()));
			}

			private final Text.UTex<String> ucount = new Text.UTex<>(() -> "x" + count, s -> Text.render(s).tex());
			private final Text.UTex<String> uq = new Text.UTex<>(() -> (nq ? "~" : "") + Utils.odformat2(q, 2), s -> Text.render(s).tex());

			public void draw(final GOut g, final Coord sz) {
				int x = 0;
				TexI texture = this.texture;
				if (texture != null) {
					Coord ssz = texture.sz();
					double sy = 1.0 * ssz.y / sz.y;
					ssz = ssz.div(sy);
					g.image(texture, Coord.z, ssz);
					x += ssz.x;
				}
				int count = this.count;
				if (count > 0) {
					Tex tex = ucount.get();
					g.image(tex, Coord.of(x, (sz.y - tex.sz().y) / 2));
					x += tex.sz().x;
				}
				int right = 0;
				int w = sz.x;
				double q = this.q;
				if (q > 0) {
					Tex tex = uq.get();
					g.image(tex, Coord.of(w - tex.sz().x, (sz.y - tex.sz().y) / 2));
					g.aimage(qimg, Coord.of(w - tex.sz().x, (sz.y - tex.sz().y) / 2), 1, 0.05);
					right = tex.sz().x + qimg.sz().x;
				}
				String name = this.name;
				if (!name.isEmpty()) {
					x += 5;
					int max = w - x - right - 5;
					for (int j = 0; j < name.length(); j++) {
						if (j != 0) {
							name = name.substring(0, name.length() - 1 - j).concat("...");
						}
						if (Text.std.strsize(name).x <= max)
							break;
					}
					Tex tex = create(name);
					g.image(tex, Coord.of(x, (sz.y - tex.sz().y) / 2));
					x += tex.sz().x;
				}
			}
		}

		public static class ItemGroupList extends OldListBox<Group> implements DTarget2 {
			private final Inventory inv;
			private final AltInventory ainv;
			private List<Group> wlist = Collections.emptyList();

			public ItemGroupList(Inventory inv, AltInventory ainv, int w, int h, int itemh) {
				super(w, h, itemh);
				this.inv = inv;
				this.ainv = ainv;
			}

			@Override
			protected Group listitem(int i) {
				return (wlist.get(i));
			}

			@Override
			protected int listitems() {
				return (wlist.size());
			}

			@Override
			protected void drawitem(GOut g, Group item, int i) {
				g.chcolor(((i % 2) == 0) ? even : odd);
				g.frect(Coord.z, g.sz());
				g.chcolor();
				item.draw(g, Coord.of(sz.x - (sb.vis() ? sb.sz.x : 0), itemh));
			}

			@Override
			public void dispose() {
				super.dispose();
			}

			@Override
			public void tick(double dt) {
				super.tick(dt);
				if (inv.dirty) {
					inv.dirty = false;
					try {
						List<WItem> wlist = new ArrayList<>();
						for (WItem wItem : inv.wmap.values()) {
							Widget cont = wItem.item.contents;
							if (cont != null) {
								wlist.addAll(cont.getchilds(WItem.class));
							} else {
								wlist.add(wItem);
							}
						}
						Map<String, Group> wmap = new HashMap<>();
						for (WItem wItem : wlist) {
							ItemInfo.Name ninfo = ItemInfo.find(ItemInfo.Name.class, wItem.item.info());
							if (ninfo == null) continue;
							String name = ninfo.str.text;
							String resname = wItem.item.resource().name;
							Group gr = wmap.computeIfAbsent(name + resname, n -> new Group(name, resname));
							gr.addItem(wItem);
						}
						for (Group g : wmap.values()) {
							g.calcQuality();

							g.items.sort(ainv.dropGroup.sel.cmp);
						}
						List<Group> list = new ArrayList<>(wmap.values());
						list.sort(ainv.dropSort.sel.cmp);
						this.wlist = list;
					} catch (Loading l) {
						inv.dirty = true;
					}
				}
			}

			@Override
			protected void itemclick(final Group item, final int button) {
				item.mousedown(Coord.z, button);
			}

			@Override
			protected void drawbg(GOut g) {}
//
//			@Override
//			public boolean iteminteract(Interact ev) {
//				Group item = itemat(ev.c);
//				if(item == null) {return false;}
//				if(item.items.isEmpty()) {return false;}
//				item.items.get(0).iteminteract(ev);
//				return false;
//			}
			@Override
			public boolean iteminteract(final WItem target, final Coord cc, final Coord ul) {
				int idx = idxat(cc);
				WItem item = null;
				if (idx >= 0 && idx < listitems())
					item = listitem(idx).takeFirst();
				if (item != null) {
					item.iteminteract(Coord.z, Coord.z);
				}
				return (true);
			}

			@Override
			public boolean drop(final WItem target, final Coord cc, final Coord ul) {
				Coord slot = inv.getFreeSlot();
				if (slot != null)
					inv.wdgmsg("drop", slot);
				return (true);
			}



			public boolean mousewheel(final Coord c, final int amount) {
				MouseWheelEvent ev = new MouseWheelEvent(c, amount);
				return (super.mousewheel(ev));
			}

			@Override
			public Object tooltip(Coord c, Widget prev) {
				int idx = idxat(c);
				WItem item = null;
				if (idx >= 0 && idx < listitems())
					item = listitem(idx).takeFirst();
				if (item != null) {
					return item.tooltip(Coord.z, prev);
				}
				return super.tooltip(c, prev);
			}
		}
	}

	@Override
	public void pack() {
		Coord invsz = sqsz.mul(isz).add(1, 1);
		Coord max = invsz;
		if (ainv.visible()) {
			ainv.list.resizeh(invsz.y);
			ainv.move(Coord.of(invsz.x, 0));
			max.x = Math.max(max.x, ainv.c.x + ainv.sz.x);
			max.y = Math.max(max.y, ainv.c.y + ainv.sz.y);
		}
		resize(max);
		parent.pack();
	}

	public void toggleAltInventory() {
		ainv.show(!ainv.visible());
		pack();
	}

	private boolean dirty = true;
	@Override
	public void dirty() {
		dirty = true;
	}

	private final List<InventoryListener> listeners = Collections.synchronizedList(new ArrayList<>());

	@Override
	public void initListeners(final List<InventoryListener> listeners) {
		this.listeners.addAll(listeners);
	}

	@Override
	public List<InventoryListener> listeners() {
		return (listeners);
	}

	private final List<InventoryListener> listeners2 = Collections.synchronizedList(new ArrayList<>());

	@Override
	public List<InventoryListener> observers() {
		return (listeners2);
	}

	@Override
	public void addListeners(final List<InventoryListener> listeners) {
		this.listeners2.addAll(listeners);
	}

	@Override
	public void removeListeners(final List<InventoryListener> listeners) {
		this.listeners2.removeAll(listeners);
	}
}
