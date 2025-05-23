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

import haven.automated.AutoRepeatFlowerMenuScript;

import java.awt.Color;
import java.awt.Font;
import java.sql.*;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static java.lang.Math.PI;

public class FlowerMenu extends Widget {
    public static final Color pink = new Color(255, 0, 128);
    public static final Color ptc = Color.WHITE;
    public static final Text.Foundry ptf = new Text.Foundry(Text.dfont, 12);
    public static final IBox pbox = Window.wbox;
    public static final Tex pbg = Window.bg;
    public static final int ph = UI.scale(30), ppl = 8;
    public Petal[] opts;
    private UI.Grab mg, kg;
	public static final Color ptcRed = new Color(255, 50, 50);
	public static final Color ptcGreen = new Color(0, 200, 50);
	public static final Color ptcYellow = new Color(252, 186, 3);
	public static final Color ptcStroke = Color.BLACK;
	private static String nextAutoSel;
	public final String[] options;
	private static final String DATABASE = "jdbc:sqlite:static_data.db";
	public static Map<String, Boolean> autoSelectMap = new TreeMap<>();

	private Consumer<Integer> callback = null;

    @RName("sm")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    String[] opts = new String[args.length];
	    for(int i = 0; i < args.length; i++)
		opts[i] = (String)args[i];
	    return(new FlowerMenu(opts));
	}
    }

    public class Petal extends Widget {
	public String name;
	public double ta, tr;
	public int num;
	private Text text;
	private double a = 1;

	public Petal(String name, int i) {
	    super(Coord.z);
		i = i+1;
	    this.name = name;
		if (name.equals("Steal")) {
			text = ptf.renderstroked(i + ". " + ">> STEAL <<", ptcRed, ptcStroke);
		} else if (name.equals("Invite")) {
			text = ptf.renderstroked(i +". " + "Invite to Party", ptcGreen, ptcStroke);
		} else if (name.equals("Memorize")) {
			text = ptf.renderstroked(i +". " + name, ptcYellow, ptcStroke);
		} else {
			text = ptf.renderstroked(i + ". " + name, ptc, ptcStroke);
		}
	    resize(text.sz().x + UI.scale(25), ph);
	}

	public void move(Coord c) {
	    this.c = c.sub(sz.div(2));
	}

	public void move(double a, double r) {
	    move(Coord.sc(a, r));
	}

	public void draw(GOut g) {
	    g.chcolor(new Color(255, 255, 255, (int)(255 * a)));
	    g.image(pbg, new Coord(3, 3), new Coord(3, 3), sz.add(new Coord(-6, -6)), UI.scale(pbg.sz()));
	    pbox.draw(g, Coord.z, sz);
	    g.image(text.tex(), sz.div(2).sub(text.sz().div(2)));
	}

	public boolean mousedown(MouseDownEvent ev) {
	    choose(this);
	    return(true);
	}

	public Area ta(Coord tc) {
	    return(Area.sized(tc.sub(sz.div(2)), sz));
	}

	public Area ta(double a, double r) {
	    return(ta(Coord.sc(a, r)));
	}
    }

    private static double nxf(double a) {
	return(-1.8633 * a * a + 2.8633 * a);
    }

    public class Opening extends NormAnim {
	Opening() {super(0.0);}
	
	public void ntick(double s) {
	    double ival = 0.8;
	    double off = (opts.length == 1) ? 0.0 : ((1.0 - ival) / (opts.length - 1));
	    for(int i = 0; i < opts.length; i++) {
		Petal p = opts[i];
		double a = Utils.clip((s - (off * i)) * (1.0 / ival), 0, 1);
		double b = nxf(a);
		p.move(p.ta + ((1 - b) * PI), p.tr * b);
		p.a = a;
	    }
	}
    }

    public class Chosen extends NormAnim {
	Petal chosen;
		
	Chosen(Petal c) {
	    super(0.0);
	    chosen = c;
	}
		
	public void ntick(double s) {
	    double ival = 0.8;
	    double off = ((1.0 - ival) / (opts.length - 1));
	    for(int i = 0; i < opts.length; i++) {
		Petal p = opts[i];
		if(p == chosen) {
		    if(s > 0.6) {
			p.a = 1 - ((s - 0.6) / 0.4);
		    } else if(s < 0.3) {
			double a = nxf(s / 0.3);
			p.move(p.ta, p.tr * (1 - a));
		    }
		} else {
		    if(s > 0.3) {
			p.a = 0;
		    } else {
			double a = s / 0.3;
			a = Utils.clip((a - (off * i)) * (1.0 / ival), 0, 1);
			p.a = 1 - a;
		    }
		}
	    }
	    if(s == 1.0)
		ui.destroy(FlowerMenu.this);
	}
    }

    public class Cancel extends NormAnim {
	Cancel() {super(0.0);}

	public void ntick(double s) {
	    double ival = 0.8;
	    double off = (opts.length == 1) ? 0.0 : ((1.0 - ival) / (opts.length - 1));
	    for(int i = 0; i < opts.length; i++) {
		Petal p = opts[i];
		double a = Utils.clip((s - (off * i)) * (1.0 / ival), 0, 1);
		double b = 1.0 - nxf(1.0 - a);
		p.move(p.ta + (b * PI), p.tr * (1 - b));
		p.a = 1 - a;
	    }
	    if(s == 1.0)
		ui.destroy(FlowerMenu.this);
	}
    }

    private void organize(Petal[] opts) {
	Area bounds = parent.area().xl(c.inv());
	int l = 1, p = 0, i = 0, mp = 0, ml = 1, t = 0, tt = -1;
	boolean muri = false;
	while(i < opts.length) {
	    place: {
		double ta = (PI / 2) - (p * (2 * PI / (l * ppl)));
		double tr = UI.scale(75) + (UI.scale(50) * (l - 1));
		if(!muri && !bounds.contains(opts[i].ta(ta, tr))) {
		    if(tt < 0) {
			tt = ppl * l;
			t = 1;
			mp = p;
			ml = l;
		    } else if(++t >= tt) {
			muri = true;
			p = mp;
			l = ml;
			continue;
		    }
		    break place;
		}
		tt = -1;
		opts[i].ta = ta;
		opts[i].tr = tr;
		i++;
	    }
	    if(++p >= (ppl * l)) {
		l++;
		p = 0;
	    }
	}
    }

	private void organizeVertical(Petal[] options) {
		int maxWidth = 0;
		for (Petal opt : options) {
			if (opt.sz.x > maxWidth)
				maxWidth = opt.sz.x;
		}
		int y = 0;
		for (Petal opt : options) {
			opt.sz.x = maxWidth;
			opt.c = new Coord(UI.scale(3), UI.scale(3) + y);
			y += opt.sz.y;
		}
		// ND: Prevent flower menu from going outside the screen
		if (ui != null && ui.gui != null) {
			if (this.c.x + maxWidth > ui.gui.sz.x){
				this.c.x = ui.gui.sz.x - maxWidth;
			}
			if (this.c.y + y > ui.gui.sz.y){
				this.c.y = ui.gui.sz.y - y;
			}
		}
	}

    public FlowerMenu(String... options) {
	super(Coord.z);
	this.options = options;
	addOptionsToDatabase(options);
	opts = new Petal[options.length];
	for(int i = 0; i < options.length; i++) {
	    add(opts[i] = new Petal(options[i], i));
	    opts[i].num = i;
	}
    }

	public FlowerMenu(Consumer<Integer> callback, String... options) {
		super(Coord.z);
		this.callback = callback;
		this.options = options;
		addOptionsToDatabase(options);
		opts = new Petal[options.length];
		for(int i = 0; i < options.length; i++) {
			add(opts[i] = new Petal(options[i], i));
			opts[i].num = i;
		}
	}

    protected void added() {
	if(c.equals(-1, -1))
	    c = parent.ui.lcc;
	mg = ui.grabmouse(this);
	kg = ui.grabkeys(this);
	organizeVertical(opts);
//	new Opening().ntick(0);
	resize(contentsz().add(new Coord(UI.scale(3), UI.scale(3))));
	tryAutoSelect();
    }

    public boolean mousedown(MouseDownEvent ev) {
	if(!anims.isEmpty())
	    return(true);
	if(!ev.propagate(this))
	    choose(null);
	return(true);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "cancel") {
	    new Cancel();
	    mg.remove();
	    kg.remove();
	} else if(msg == "act") {
	    new Chosen(opts[Utils.iv(args[0])]);
	    mg.remove();
	    kg.remove();
	}
    }

    public void draw(GOut g) {
	super.draw(g, false);
    }

    public boolean keydown(KeyDownEvent ev) {
	if((ev.c >= '0') && (ev.c <= '9')) {
	    int opt = (ev.c == '0') ? 9 : (ev.c - '1');
	    if(opt < opts.length) {
		choose(opts[opt]);
		kg.remove();
	    }
	    return(true);
	} else if(key_esc.match(ev)) {
	    choose(null);
	    kg.remove();
	    return(true);
	}
	return(super.keydown(ev));
    }

    public void choose(Petal option) {
	if(option == null) {
	    wdgmsg("cl", -1);
	} else	if(callback != null){
		callback.accept(option != null ? option.num : -1);
		ui.destroy(this);
		return;
	}
	else {

		if(AutoRepeatFlowerMenuScript.option != null){
			AutoRepeatFlowerMenuScript.option = option.name;
		}
	    wdgmsg("cl", option.num, ui.modflags());
	}
    }

	public static void setNextSelection(String name) {
		nextAutoSel = name;
	}

	public void tryAutoSelect() {
		if (nextAutoSel != null) {
			for (String option : options) {
				if (option.equals(nextAutoSel)) {
					choose(getPetalFromName(option));
					nextAutoSel = null;
					return;
				}
			}
			choose(null);
			nextAutoSel = null;
		} else {
			if(GameUI.flowerMenuAutoSelect){
				for (String option : options) {
					if (autoSelectMap.get(option)) {
						choose(getPetalFromName(option));
					}
				}
			}
		}
	}

	public Petal getPetalFromName(String name){
        for (Petal opt : opts) {
            if (opt.name.equals(name)){
                return opt;
            }
        }
		return null;
	}

	public static void updateValue(String name, boolean value) {
		autoSelectMap.put(name, value);
		updateDbValue(name, value);
	}

	private void addOptionsToDatabase(String[] options) {
		try {
			for (String option : options) {
				if (autoSelectMap.get(option) == null) {
					autoSelectMap.put(option, false);
					checkAndInsertFlowerMenuOption(option);
					if (OptWnd.flowerMenuAutoSelectManagerWindow != null) {
						OptWnd.flowerMenuAutoSelectManagerWindow.refresh();
					}
				}
			}
		} catch (Exception e) {
		}
	}



	public static void updateDbValue(String flowerMenuOptionName, boolean newValue) {
		try (java.sql.Connection conn = DriverManager.getConnection(DATABASE)) {
			String updateSql = "UPDATE flower_menu_options SET auto_use = ? WHERE name = ?";
			try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
				updatePstmt.setBoolean(1, newValue);
				updatePstmt.setString(2, flowerMenuOptionName);
				updatePstmt.executeUpdate();
			}
		} catch (SQLException e) {
			System.out.println("Problem with updating flower menu option in the database.");
		}
	}

	private static void checkAndInsertFlowerMenuOption(String flowerMenuOptionName) {
		try (java.sql.Connection conn = DriverManager.getConnection(DATABASE)) {
			String checkSql = "SELECT count(*) FROM flower_menu_options WHERE name = ?";
			try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
				pstmt.setString(1, flowerMenuOptionName);
				ResultSet rs = pstmt.executeQuery();
				if (rs.getInt(1) == 0) { // if record doesn't exist
					String insertSql = "INSERT INTO flower_menu_options(name) VALUES(?)";
					try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
						insertPstmt.setString(1, flowerMenuOptionName);
						insertPstmt.executeUpdate();
					}
				}
			}
		} catch (SQLException ignored) {
			System.out.println("Problem with inserting flower menu option to database.");
		}
	}

	public static void fillAutoChooseMap() {
		String sql = "SELECT name, auto_use FROM flower_menu_options order by name";
		try (java.sql.Connection conn = DriverManager.getConnection(DATABASE);
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				String name = rs.getString("name");
				boolean autoUse = rs.getBoolean("auto_use");
				autoSelectMap.put(name, autoUse);
			}
		} catch (SQLException e) {
			System.out.println("Problem with fetching flower menu options from database.");
		}
	}

	public static void createDatabaseIfNotExist() throws SQLException {
		try (java.sql.Connection conn = DriverManager.getConnection(DATABASE)) {
			if (conn != null) {
				createSchemaElementIfNotExist(conn, "flower_menu_options",
						"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
								"name VARCHAR(255) UNIQUE NOT NULL, " +
								"auto_use BOOLEAN DEFAULT FALSE NOT NULL",
						"table");
			}
		}
	}

	private static void createSchemaElementIfNotExist(java.sql.Connection conn, String name, String definitions, String type) throws SQLException {
		if (!schemaElementExists(conn, name, type)) {
			String sql = type.equals("table") ? "CREATE TABLE " + name + " (\n" + definitions + "\n);" : "CREATE INDEX " + name + " ON " + definitions + ";";
			try (Statement stmt = conn.createStatement()) {
				stmt.execute(sql);
			}
			System.out.println("A new " + type + " (" + name + ") has been created in the database.");
		}
	}

	private static boolean schemaElementExists(Connection conn, String name, String type) throws SQLException {
		String checkExistsQuery = "SELECT name FROM sqlite_master WHERE type='" + type + "' AND name='" + name + "';";
		try (Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery(checkExistsQuery)) {
			return rs.next();
		}
	}

}
