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

import java.util.*;
import java.awt.Color;

public class Bufflist extends Widget {
    public static final int margin = UI.scale(2);
    public static final int num = 12;

	public final static Resource buffswim = Resource.local().loadwait("customclient/buffs/swim");
	public final static Resource bufftrack = Resource.local().loadwait("customclient/buffs/tracking");
	public final static Resource buffcrime = Resource.local().loadwait("customclient/buffs/crime");
	public final static Resource partyperm = Resource.local().loadwait("customclient/buffs/partyperm");
	public final static Resource itemstacking = Resource.local().loadwait("customclient/buffs/itemcomb");

	private UI.Grab dragging;
	private Coord dc;

    public interface Managed {
	public void move(Coord c, double off);
    }

    public Bufflist() {
        super(Buff.cframe.sz());
    }

	@Override
	public boolean mousedown(MouseDownEvent ev) {
		if (!GameUI.showUI)
			return(false);
		if (ev.b == 2 && ui.modmeta) {
			if((dragging != null)) { // ND: I need to do this extra check and remove it in case you do another click before the mouseup. Idk why it has to be done like this, but it solves the issue.
				dragging.remove();
				dragging = null;
			}
			dragging = ui.grabmouse(this);
			dc = ev.c;
			return true;
		}
		return(super.mousedown(ev));
	}

	@Override
	public boolean mouseup(MouseUpEvent ev) {
//		checkIfOutsideOfUI(); // ND: Prevent the widget from being dragged outside the current window size
		if((dragging != null)) {
			dragging.remove();
			dragging = null;
			Utils.setprefc("wndc-buffsBarWgt", this.c);
			return true;
		}
		return super.mouseup(ev);
	}

	@Override
	public void mousemove(MouseMoveEvent ev) {
		if (dragging != null) {
			this.c = this.c.add(ev.c.x, ev.c.y).sub(dc);
			return;
		}
		super.mousemove(ev);
	}

//	public void checkIfOutsideOfUI() {
//		if (this.c.x < 0)
//			this.c.x = 0;
//		if (this.c.y < 0)
//			this.c.y = 0;
//		if (this.c.x > (GameUI.this.sz.x - this.sz.x))
//			this.c.x = GameUI.this.sz.x - this.sz.x;
//		if (this.c.y > (GameUI.this.sz.y - this.sz.y))
//			this.c.y = this.sz.y - this.sz.y;
//	}

    private void arrange(Widget imm) {
	int i = 0, rn = 0, x = 0, y = 0, maxh = 0;
	Coord br = new Coord();
	Collection<Pair<Managed, Coord>> mv = new ArrayList<>();
	for(Widget wdg = child; wdg != null; wdg = wdg.next) {
	    if(!(wdg instanceof Managed))
		continue;
	    Managed ch = (Managed)wdg;
	    Coord c = new Coord(x, y);
	    if(ch == imm)
		wdg.c = c;
	    else
		mv.add(new Pair<>(ch, c));
	    i++;
	    x += wdg.sz.x + margin;
	    maxh = Math.max(maxh, wdg.sz.y);
	    if(++rn >= num) {
		x = 0;
		y += maxh + margin;
		maxh = 0;
		rn = 0;
	    }
	    if(c.x + wdg.sz.x > br.x) br.x = c.x + wdg.sz.x;
	    if(c.y + wdg.sz.y > br.y) br.y = c.y + wdg.sz.y;
	}
	resize(br);
	double off = 1.0 / mv.size(), coff = 0.0;
	for(Pair<Managed, Coord> p : mv) {
	    p.a.move(p.b, coff);
	    coff += off;
	}
    }

    public void addchild(Widget child, Object... args) {
	add(child);
	arrange(child);
    }

    public void cdestroy(Widget ch) {
	arrange(null);
    }

    public void draw(GOut g) {
	for(Widget wdg = child, next; wdg != null; wdg = next) {
	    next = wdg.next;
	    if(!wdg.visible || !(wdg instanceof Managed))
		continue;
	    wdg.draw(g.reclipl(xlate(wdg.c, true), wdg.sz));
	}
    }

	public Buff gettoggle(String name) {
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg instanceof Buff) {
				Buff buff = (Buff) wdg;
				try {
					Resource res = buff.res.get();
					if (res.basename().equals(name))
						return buff;
				} catch (Loading e) {
				}
			}
		}
		return null;
	}
}
