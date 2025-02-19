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

import java.awt.Color;
import java.util.*;
import haven.render.*;
import haven.render.sl.*;
import static haven.render.sl.Cons.*;
import static haven.render.sl.Type.*;

public class Outlines implements RenderTree.Node {
    private boolean symmetric;

    private final static Uniform snrm = new Uniform(SAMPLER2D, p -> ((Draw)p.get(RUtils.adhoc)).nrm, RUtils.adhoc);
    private final static Uniform sdep = new Uniform(SAMPLER2D, p -> ((Draw)p.get(RUtils.adhoc)).depth, RUtils.adhoc);
    private final static Uniform msnrm = new Uniform(SAMPLER2DMS, p -> ((Draw)p.get(RUtils.adhoc)).nrm, RUtils.adhoc);
    private final static Uniform msdep = new Uniform(SAMPLER2DMS, p -> ((Draw)p.get(RUtils.adhoc)).depth, RUtils.adhoc);
    private final static ShaderMacro[] shaders = new ShaderMacro[4];

    private static class Draw extends RUtils.AdHoc {
	final Texture2D.Sampler2D nrm, depth;

	Draw(ShaderMacro code, Texture2D.Sampler2D nrm, Texture2D.Sampler2D depth) {
	    super(code);
	    this.nrm = nrm;
	    this.depth = depth;
	}
    }

    private static ShaderMacro shader(final boolean symmetric, final boolean ms) {
	return(new ShaderMacro() {
		Color color = Color.BLACK;
		int outlines = Utils.getprefb("disableOutlines", false) ? 0 : 1;


		Coord[] points = {
		    new Coord(-outlines,  0),
		    new Coord( outlines,  0),
		    new Coord( 0, -outlines),
		    new Coord( 0,  outlines),
		};

		Expression sample(boolean nrm, Expression c, Expression s, Coord o) {
		    if(ms) {
			Expression ctc = ivec2(floor(mul(c, FrameConfig.u_screensize.ref())));
			if(!o.equals(Coord.z))
			    ctc = add(ctc, ivec2(o));
			return(texelFetch((nrm?msnrm:msdep).ref(), ctc, s));
		    } else {
			Expression ctc = c;
			if(!o.equals(Coord.z))
			    ctc = add(c, mul(vec2(o), FrameConfig.u_pixelpitch.ref()));
			return(texture2D((nrm?snrm:sdep).ref(), ctc));
		    }
		}

		Function ofac = new Function.Def(FLOAT) {{
		    Expression sample = param(PDir.IN, INT).ref();
		    Expression tc = Tex2D.rtexcoord.ref();
		    LValue ret = code.local(FLOAT, l(0.0)).ref();
		    Expression lnrm = code.local(VEC3, pick(sample(true, tc, sample, Coord.z), "rgb")).ref();
		    Expression ldep = code.local(FLOAT, pick(sample(false, tc, sample, Coord.z), "r")).ref();
		    /* XXX: Current depth detection doesn't work well
		     * with frustum projections, perhaps because of
		     * the lack of precision in the depth buffer
		     * (though I'm not sure I buy that explanation
		     * yet). */
		    LValue dh = code.local(FLOAT, l(0.0002)).ref(), dl = code.local(FLOAT, l(-0.0002)).ref();
		    for(int i = 0; i < points.length; i++) {
			Expression cdep = pick(sample(false, tc, sample, points[i]), "r");
			cdep = sub(ldep, cdep);
			cdep = code.local(FLOAT, cdep).ref();
			code.add(stmt(ass(dh, max(dh, cdep))));
			code.add(stmt(ass(dl, min(dl, cdep))));
		    }
		    if(symmetric)
			code.add(aadd(ret, smoothstep(l(5.0), l(6.0), max(div(dh, neg(dl)), div(dl, neg(dh))))));
		    else
			code.add(aadd(ret, smoothstep(l(5.0), l(6.0), div(dh, neg(dl)))));
		    for(int i = 0; i < points.length; i++) {
			Expression cnrm = pick(sample(true, tc, sample, points[i]), "rgb");
			if(symmetric) {
			    code.add(aadd(ret, sub(l(1.0), abs(dot(lnrm, cnrm)))));
			} else {
			    cnrm = code.local(VEC3, cnrm).ref();
			    code.add(new If(gt(pick(cross(lnrm, cnrm), "z"), l(0.0)),
					    stmt(aadd(ret, sub(l(1.0), abs(dot(lnrm, cnrm)))))));
			}
		    }
		    code.add(new Return(smoothstep(l(0.4), l(0.6), min(ret, l(1.0)))));
		}};

		Function msfac = new Function.Def(FLOAT) {{
		    LValue ret = code.local(FLOAT, l(0.0)).ref();
		    LValue i = code.local(INT, null).ref();
		    code.add(new For(ass(i, l(0)), lt(i, FrameConfig.u_numsamples.ref()), linc(i),
				     stmt(aadd(ret, ofac.call(i)))));
		    code.add(new Return(div(ret, FrameConfig.u_numsamples.ref())));
		}};

		public void modify(ProgramContext prog) {
		    FragColor.fragcol(prog.fctx).mod(in -> {
			    Expression of = (!ms)?ofac.call(l(-1)):msfac.call();
			    return(vec4(col3(color), mix(l(0.0), l(1.0), of)));
			}, 0);
		}
	    });
    }

    static {
	/* XXX: It would be good to have some kind of more convenient
	 * shader internation. */
	shaders[0] = shader(false, false);
	shaders[1] = shader(false, true);
	shaders[2] = shader(true,  false);
	shaders[3] = shader(true,  true);
    }

    public Outlines(boolean symmetric) {
	this.symmetric = symmetric;
    }

    public void added(RenderTree.Slot slot) {
	RenderedNormals.get(slot.state());
	slot.add(new Rendered.ScreenQuad(false), p -> {
		FrameConfig fb = p.get(FrameConfig.slot);
		DepthBuffer<?> dbuf = p.get(DepthBuffer.slot);
		RenderedNormals nbuf = p.get(RenderedNormals.slot);
		boolean ms = fb.samples > 1;
		p.prep(Rendered.postfx);
		p.put(RenderedNormals.slot, null);
		p.put(DepthBuffer.slot, null);
		p.prep(new Draw(shaders[(symmetric?2:0) | (ms?1:0)],
				new Texture2D.Sampler2D((Texture2D)nbuf.img.tex),
				new Texture2D.Sampler2D((Texture2D)((Texture.Image)dbuf.image).tex)));
	    });
    }

    public void removed(RenderTree.Slot slot) {
	RenderedNormals.put(slot.state());
    }
}
