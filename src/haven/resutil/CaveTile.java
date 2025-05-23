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

package haven.resutil;

import java.util.*;
import haven.*;
import haven.MapMesh.Scan;
import haven.Surface.Vertex;
import haven.render.Pipe;

public class CaveTile extends Tiler {
    public static final float h = 16;
	public static Coord3f tilesz = Coord3f.of((float)MCache.tilesz.x,(float)MCache.tilesz.y, 0.0F);
    public final Material wtex;
    public final Tiler ground;
	public static final Map<Material, Tex> tiles = new HashMap<>();

    public static class Walls {
	public final MapMesh m;
	public final Scan cs;
	public final Vertex[][] wv;
	private MapMesh.MapSurface ms;
	private static float py = 0.27272728F;

	public Walls(MapMesh m) {
	    this.m = m;
	    this.ms = m.data(MapMesh.gnd);
	    cs = new Scan(Coord.z, m.sz.add(1, 1));
	    wv = new Vertex[cs.l][];
	}

	public Vertex[] fortile(Coord tc) {
	    if(wv[cs.o(tc)] == null) {
//		if (OptWnd.flatCaveWallsCheckBox.a) {
//			Vertex[] buf = wv[cs.o(tc)] = new Vertex[4];
//			buf[0] = ms.fortile(tc);
//			buf[1] = ms.new Vertex(buf[0].x, buf[0].y, buf[0].z + 5f);
//			buf[2] = ms.new Vertex(buf[0].x + 1, buf[0].y + 1, buf[0].z + 5f);
//			buf[3] = ms.new Vertex(buf[0].x, buf[0].y, buf[0].z);
//		} else {
		Random rnd = m.grnd(tc.add(m.ul));
		Vertex[] buf = wv[cs.o(tc)] = new Vertex[4];
		buf[0] = ms.new Vertex(ms.fortile(tc));
		for(int i = 1; i < buf.length; i++) {
		    buf[i] = ms.new Vertex(buf[0].x, buf[0].y, buf[0].z + (i * h / (buf.length - 1)));
		    buf[i].x += (rnd.nextFloat() - 0.5f) * 3.0f;
		    buf[i].y += (rnd.nextFloat() - 0.5f) * 3.0f;
		    buf[i].z += (rnd.nextFloat() - 0.5f) * 3.5f;
		}
//	    }
		}
	    return(wv[cs.o(tc)]);
	}

	public Vertex[] fortileFlat(Coord tc, float mul) {
		if(wv[cs.o(tc)] == null) {
			Vertex[] buf = wv[cs.o(tc)] = new Vertex[8];
			buf[0] = ms.new Vertex(ms.fortile(tc).add(0.0F, 0.0F, 3.0F * mul));
			buf[1] = ms.new Vertex(buf[0].add(0.0F, -CaveTile.tilesz.y, 0.0F));
			buf[2] = ms.new Vertex(buf[0].add(CaveTile.tilesz.x, -CaveTile.tilesz.y, 0.0F));
			buf[3] = ms.new Vertex(buf[0].add(CaveTile.tilesz.x, 0.0F, 0.0F));
			buf[4] = ms.new Vertex(buf[0].add(0.0F, 0.0F, -3.0F));
			buf[5] = ms.new Vertex(buf[4].add(0.0F, -CaveTile.tilesz.y, 0.0F));
			buf[6] = ms.new Vertex(buf[4].add(CaveTile.tilesz.x, -CaveTile.tilesz.y, 0.0F));
			buf[7] = ms.new Vertex(buf[4].add(CaveTile.tilesz.x, 0.0F, 0.0F));
		}
		return(wv[cs.o(tc)]);
	}
    }
    public static final MapMesh.DataID<Walls> walls = MapMesh.makeid(Walls.class);

    @ResName("cave")
    public static class Factory implements Tiler.Factory {
	public Tiler create(int id, Tileset set) {
	    KeywordArgs desc = new KeywordArgs(set.ta, set.getres().pool);
	    Material wtex = set.getres().flayer(Material.Res.class, Utils.iv(desc.get("wmat"))).get();
	    Tiler ground = desc.oget("gnd").map(r -> Utils.irv(r).get().flayer(Tileset.class)).map(ts -> ts.tfac().create(id, ts)).orElse(null);
	    return(new CaveTile(id, set, wtex, ground));
	}
    }

    public CaveTile(int id, Tileset set, Material wtex, Tiler ground) {
	super(id);
	this.wtex = wtex;
	this.ground = ground;
    }

    private static final Coord[] tces = {new Coord(0, -1), new Coord(1, 0), new Coord(0, 1), new Coord(-1, 0)};
    private static final Coord[] tccs = {new Coord(0, 0), new Coord(1, 0), new Coord(1, 1), new Coord(0, 1)};

    private void modelwall(Walls w, Coord ltc, Coord rtc) {
	Vertex[] lw = w.fortile(ltc), rw = w.fortile(rtc);
	for(int i = 0; i < lw.length - 1; i++) {
	    w.ms.new Face(lw[i + 1], lw[i], rw[i + 1]);
	    w.ms.new Face(lw[i], rw[i], rw[i + 1]);
	}
    }

	private void modelwallFlat(Walls w, Coord tc, boolean[] walled) {
		Vertex[] lw = w.fortileFlat(tc, 1.0F);
		MapMesh.MapSurface varW = w.ms;
		Objects.requireNonNull(varW);
		varW.new Face(lw[0], lw[1], lw[2]);
		varW.new Face(lw[0], lw[2], lw[3]);
	}

    public void model(MapMesh m, Random rnd, Coord lc, Coord gc) {
	super.model(m, rnd, lc, gc);
	Walls w = null;
	boolean[] walled = new boolean[4];

	for(int i = 0; i < 4; ++i) {
	    int cid = m.map.gettile(gc.add(tces[i]));
		if(OptWnd.flatCaveWallsCheckBox.a) {
	    	if(cid > id || !(m.map.tiler(cid) instanceof CaveTile)) {
				if(w == null) {
					w = m.data(walls);
				}
				walled[i] = true;
			}

			if(walled[0] || walled[1] || walled[2] || walled[3]) {
				modelwallFlat(w, lc, walled);
			}
	} else {
			if(cid <= id || (m.map.tiler(cid) instanceof CaveTile))
				continue;
			if(w == null) w = m.data(walls);
			modelwall(w, lc.add(tccs[(i + 1) % 4]), lc.add(tccs[i]));
		}
	}

    }

	private Pipe.Op mkWtex() {
		return this.wtex;
	}

    private void mkwall(MapMesh m, Walls w, Coord ltc, Coord rtc) {
	Vertex[] lw = w.fortile(ltc), rw = w.fortile(rtc);
	MapMesh.Model mod = MapMesh.Model.get(m, wtex);
	MeshBuf.Vertex[] lv = new MeshBuf.Vertex[lw.length], rv = new MeshBuf.Vertex[rw.length];
	MeshBuf.Tex tex = mod.layer(mod.tex);
	for(int i = 0; i < lv.length; i++) {
	    float ty = (float)i / (float)(lv.length - 1);
	    lv[i] = new Surface.MeshVertex(mod, lw[i]);
	    tex.set(lv[i], new Coord3f(0, ty, 2));
	    rv[i] = new Surface.MeshVertex(mod, rw[i]);
	    tex.set(rv[i], new Coord3f(1, ty, 2));
	}
	for(int i = 0; i < lv.length - 1; i++) {
	    mod.new Face(lv[i + 1], lv[i], rv[i + 1]);
	    mod.new Face(lv[i], rv[i], rv[i + 1]);
	}
    }

	private void mkwallFlat(MapMesh m, Walls w, Coord tc) {
		Surface.Vertex[] lw = w.fortileFlat(tc, 1.0F);
		MapMesh.Model mod = MapMesh.Model.get(m, mkWtex());
		MeshBuf.Vertex[] lv = new MeshBuf.Vertex[20];
		MeshBuf.Tex tex = mod.layer(mod.tex);
		int i = 0;
		lv[i] = new Surface.MeshVertex(mod, lw[0]);
		tex.set(lv[i++], new Coord3f(0.0F, 1.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[1]);
		tex.set(lv[i++], new Coord3f(0.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[2]);
		tex.set(lv[i++], new Coord3f(1.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[3]);
		tex.set(lv[i++], new Coord3f(1.0F, 1.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[3]);
		tex.set(lv[i++], new Coord3f(0.0F, Walls.py, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[7]);
		tex.set(lv[i++], new Coord3f(0.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[4]);
		tex.set(lv[i++], new Coord3f(1.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[0]);
		tex.set(lv[i++], new Coord3f(1.0F, Walls.py, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[2]);
		tex.set(lv[i++], new Coord3f(0.0F, Walls.py, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[6]);
		tex.set(lv[i++], new Coord3f(0.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[7]);
		tex.set(lv[i++], new Coord3f(1.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[3]);
		tex.set(lv[i++], new Coord3f(1.0F, Walls.py, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[1]);
		tex.set(lv[i++], new Coord3f(0.0F, Walls.py, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[5]);
		tex.set(lv[i++], new Coord3f(0.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[6]);
		tex.set(lv[i++], new Coord3f(1.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[2]);
		tex.set(lv[i++], new Coord3f(1.0F, Walls.py, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[0]);
		tex.set(lv[i++], new Coord3f(0.0F, Walls.py, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[4]);
		tex.set(lv[i++], new Coord3f(0.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[5]);
		tex.set(lv[i++], new Coord3f(1.0F, 0.0F, 0.0F));
		lv[i] = new Surface.MeshVertex(mod, lw[1]);
		tex.set(lv[i++], new Coord3f(1.0F, Walls.py, 0.0F));

		for(int j = 0; j < 20; j += 4) {
			Objects.requireNonNull(mod);
			Objects.requireNonNull(mod);
			mod.new Face( lv[j], lv[j + 1], lv[j + 2]);
			Objects.requireNonNull(mod);
			Objects.requireNonNull(mod);
			mod.new Face( lv[j], lv[j + 2], lv[j + 3]);
		}

	}

    public void lay(MapMesh m, Random rnd, Coord lc, Coord gc) {
	Walls w = null;
	boolean[] walled = new boolean[4];

	for(int i = 0; i < 4; ++i) {
		int cid = m.map.gettile(gc.add(tces[i]));
		if (OptWnd.flatCaveWallsCheckBox.a) {
			if (cid > id && !(m.map.tiler(cid) instanceof CaveTile)) {
				if (w == null) {
					w = m.data(walls);
				}
				walled[i] = true;
			}

		}
		if (walled[0] || walled[1] || walled[2] || walled[3]) {
			this.mkwallFlat(m, w, lc);
		} else {
			if(cid <= id || (m.map.tiler(cid) instanceof CaveTile))
				continue;
			if(w == null) w = m.data(walls);
			mkwall(m, w, lc.add(tccs[(i + 1) % 4]), lc.add(tccs[i]));
		}
	}


	if(ground != null) {
		Tex tex = tiles.get(wtex);
		if (tex == null && OptWnd.flatCaveWallsCheckBox.a) {
			for (Pipe.Op gs : wtex.statesForTiles) {
				if (gs instanceof TexRender.TexDraw) {
					if (gs.toString().contains("gfx/tiles/mountain-tex"))
						break;
					tiles.put(wtex, tex = ((TexRender.TexDraw) gs).tex);
					break;
				}
			}
		}
		if (tex != null && OptWnd.flatCaveWallsCheckBox.a) {
			if (ground instanceof GroundTile) {
				MapMesh.MapSurface s = m.data(m.gnd);
				GroundTile grn = ((GroundTile) ground);
				MPart d = MPart.splitquad(lc, gc, s.fortilea(lc), s.split[s.ts.o(lc)]);
				grn._faces(m, tex, 0, d.v, d.tcx, d.tcy, d.f);
			} else {
				ground.lay(m, rnd, lc, gc);
			}
		} else
		ground.lay(m, rnd, lc, gc);
	}
    }

    public void trans(MapMesh m, Random rnd, Tiler gt, Coord lc, Coord gc, int z, int bmask, int cmask) {}
}
