package haven.res.ui.barterbox;

import haven.Button;
import haven.Coord;
import haven.FromResource;
import haven.GItem;
import haven.GOut;
import haven.GSprite;
import haven.Glob;
import haven.Indir;
import haven.Inventory;
import haven.ItemInfo;
import haven.Label;
import haven.Loading;
import haven.Message;
import haven.MessageBuf;
import haven.ResData;
import haven.Resource;
import haven.RichText;
import haven.Tex;
import haven.TexI;
import haven.Text;
import haven.TextEntry;
import haven.UI;
import haven.Utils;
import haven.WItem;
import haven.Widget;
import haven.res.lib.tspec.Spec;
import haven.res.ui.tt.q.qbuff.QBuff;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@FromResource(
        name = "ui/shopbox",
        override = true,
        version = 1
)
public class Shopbox extends Widget implements ItemInfo.SpriteOwner, GSprite.Owner {
    public static final Text any = Text.render( "Any");
    public static final Text qlbl = Text.render( "Quality:");
    public static final Tex bg = Resource.loadtex("ui/shopbox");
    public static final Coord itemc = new Coord(5, 5);
    public static final Coord buyc = new Coord(5, 40);
    public static final Coord buyca = new Coord(5, 66);
    public static final Coord pricec = new Coord(200, 5);
    public static final Coord qualc;
    public static final Coord cbtnc;
    public static final Coord spipec;
    public static final Coord bpipec;
    public ResData res;
    public Spec price;
    public Text num;
    public int pnum;
    public int pq;
    private Text pnumt;
    private Text pqt;
    private GSprite spr;
    private Object[] info = new Object[0];
    private Text quality;
    private Button spipe;
    private Button bpipe;
    private Button bbtn;
    private Button bbtn100;
    private TextEntry tbuy;
    private Button cbtn;
    private TextEntry pnume;
    private TextEntry pqe;
    public final boolean admin;
    public final AttrCache<Tex> itemnum = new One(this);
    private List<ItemInfo> cinfo;
    private Tex longtip = null;
    private Tex fulltip = null;
    private Tex pricetip = null;
    private Random rnd = null;
    private int count = 0;

    public static Widget mkwidget(UI ui, Object... args) {
        boolean adm = (Integer)args[0] != 0;
        return new Shopbox(adm);
    }

    public Shopbox(boolean admin) {
        super(bg.sz());
        if (this.admin = admin) {
            this.spipe = (Button)this.add(new Button(75, "Connect"), spipec);
            this.bpipe = (Button)this.add(new Button(75, "Connect"), bpipec);
            this.cbtn = (Button)this.add(new Button(75, "Change"), cbtnc);
            this.pnume = (TextEntry)this.adda(new TextEntry(30, ""), pricec.add(Inventory.invsq.sz()).add(5, 0), 0.0, 1.0);
            this.pnume.canactivate = true;
            this.pnume.dshow = true;
            this.adda(new Label("Quality:"), qualc.add(0, 0), 0.0, 1.0);
            this.pqe = (TextEntry)this.adda(new TextEntry(40, ""), qualc.add(40, 0), 0.0, 1.0);
            this.pqe.canactivate = true;
            this.pqe.dshow = true;
        }

    }

    public void draw(GOut g) {
        g.image(bg, Coord.z);
        ResData res = this.res;
        GOut sg;
        if (res != null) {
            label52: {
                sg = g.reclip(itemc, Inventory.invsq.sz());
                sg.image(Inventory.invsq, Coord.z);
                GSprite var4 = this.spr;
                if (var4 == null) {
                    try {
                        var4 = this.spr = GSprite.create(this, (Resource)res.res.get(), res.sdt.clone());
                    } catch (Loading var7) {
                        sg.image(((Resource.Image)WItem.missing.layer(Resource.imgc)).tex(), Coord.z, Inventory.sqsz);
                        break label52;
                    }
                }

                var4.draw(sg);
                if (this.itemnum.get() != null) {
                    sg.aimage((Tex)this.itemnum.get(), Inventory.sqsz, 1.0, 1.0);
                }

                if (this.num != null) {
                    g.aimage(this.num.tex(), itemc.add(Inventory.invsq.sz()).add(5, 0), 0.0, 2.3);
                }

                if (this.quality != null) {
                    g.aimage(qlbl.tex(), itemc.add(Inventory.invsq.sz()).add(5, 0), 0.0, 1.0);
                    g.aimage(this.quality.tex(), itemc.add(Inventory.invsq.sz()).add(8 + qlbl.tex().sz().x, 0), 0.0, 1.0);
                }
            }
        }

        Spec price = this.price;
        if (price != null) {
            sg = g.reclip(pricec, Inventory.invsq.sz());
            sg.image(Inventory.invsq, Coord.z);

            try {
                price.spr().draw(sg);
            } catch (Loading var6) {
                sg.image(((Resource.Image)WItem.missing.layer(Resource.imgc)).tex(), Coord.z, Inventory.sqsz);
            }

            if (!this.admin && this.pnumt != null) {
                g.aimage(this.pnumt.tex(), pricec.add(Inventory.invsq.sz()), 0.0, 1.0);
            }

            if (!this.admin && this.pqt != null) {
                g.aimage(qlbl.tex(), qualc, 0.0, 1.0);
                g.aimage(this.pqt.tex(), qualc.add(qlbl.tex().sz().x + 4, 0), 0.0, 1.0);
            }
        }

        super.draw(g);
    }

    public List<ItemInfo> info() {
        if (this.cinfo == null) {
            this.cinfo = ItemInfo.buildinfo(this, this.info);
            QBuff qb = this.quality();
            if (qb != null) {
                this.quality = Text.render("" + (int)qb.q);
            }
        }

        return this.cinfo;
    }

    private QBuff getQBuff(List<ItemInfo> infolist) {
        Iterator var2 = infolist.iterator();

        ItemInfo info;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            info = (ItemInfo)var2.next();
        } while(!(info instanceof QBuff));

        return (QBuff)info;
    }

    private QBuff quality() {
        try {
            Iterator var1 = this.info().iterator();

            ItemInfo info;
            do {
                if (!var1.hasNext()) {
                    return this.getQBuff(this.info());
                }

                info = (ItemInfo)var1.next();
            } while(!(info instanceof ItemInfo.Contents));

            return this.getQBuff(((ItemInfo.Contents)info).sub);
        } catch (Loading var3) {
            return null;
        }
    }

    public Object tooltip(Coord c, Widget prev) {
        ResData res = this.res;
        if (c.isect(itemc, Inventory.sqsz) && res != null) {
            try {
                BufferedImage ti;
                Resource.Pagina pg;
                if (this.ui.modflags() == UI.MOD_SHIFT) {
                    if (this.longtip == null) {
                        ti = ItemInfo.longtip(this.info());
                        pg = (Resource.Pagina)((Resource)res.res.get()).layer(Resource.pagina);
                        if (pg != null) {
                            ti = ItemInfo.catimgs(0, new BufferedImage[]{ti, RichText.render("\n" + pg.text, UI.scale(200), new Object[0]).img});
                        }

                        this.longtip = new TexI(ti);
                    }

                    return this.longtip;
                } else {
                    if (this.fulltip == null) {
                        ti = ItemInfo.longtip(this.info());
                        pg = (Resource.Pagina)((Resource)res.res.get()).layer(Resource.pagina);
                        if (pg != null) {
                            ti = ItemInfo.catimgs(0, new BufferedImage[]{ti, RichText.render("\n" + pg.text, UI.scale(200), new Object[0]).img});
                        }

                        this.fulltip = new TexI(ti);
                    }

                    return this.fulltip;
                }
            } catch (Loading var6) {
                return "...";
            }
        } else if (c.isect(pricec, Inventory.sqsz) && this.price != null) {
            try {
                if (this.pricetip == null) {
                    this.pricetip = this.price.longtip();
                }

                return this.pricetip;
            } catch (Loading var7) {
                return "...";
            }
        } else {
            return super.tooltip(c, prev);
        }
    }

    /** @deprecated */
    @Deprecated
    public Glob glob() {
        return this.ui.sess.glob;
    }

    public Resource resource() {
        return (Resource)this.res.res.get();
    }

    public GSprite sprite() {
        if (this.spr == null) {
            throw new Loading("Still waiting for sprite to be constructed");
        } else {
            return this.spr;
        }
    }

    public Resource getres() {
        return (Resource)this.res.res.get();
    }

    public Random mkrandoom() {
        if (this.rnd == null) {
            this.rnd = new Random();
        }

        return this.rnd;
    }

    private static Integer parsenum(TextEntry e) {
        try {
            return e.buf.line().equals("") ? 0 : Integer.parseInt(e.buf.line());
        } catch (NumberFormatException var2) {
            return null;
        }
    }

    public boolean mousedown(Coord c, int button) {
        if (button == 3 && c.isect(pricec, Inventory.sqsz) && this.price != null) {
            this.wdgmsg("pclear", new Object[0]);
            return true;
        } else {
            return super.mousedown(c, button);
        }
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
        int i;
        if (sender == this.bbtn) {
            if (this.ui.modshift && !this.ui.modctrl) {
                for(i = 0; i <= 5; ++i) {
                    this.wdgmsg("buy", new Object[0]);
                }
            } else if (this.ui.modshift && this.ui.modctrl) {
                for(i = 0; i <= 20; ++i) {
                    this.wdgmsg("buy", new Object[0]);
                }
            } else {
                this.wdgmsg("buy", new Object[0]);
            }
        } else if (sender == this.bbtn100) {
            if (this.count > 0) {
                for(i = 0; i < this.count; ++i) {
                    this.wdgmsg("buy", new Object[0]);
                }
            } else {
                this.ui.gui.error("You can't buy 0 items.");
            }
        } else if (sender == this.spipe) {
            this.wdgmsg("spipe", new Object[0]);
        } else if (sender == this.bpipe) {
            this.wdgmsg("bpipe", new Object[0]);
        } else if (sender == this.cbtn) {
            this.wdgmsg("change", new Object[0]);
        } else if (sender != this.pnume && sender != this.pqe) {
            super.wdgmsg(sender, msg, args);
        } else {
            this.wdgmsg("price", new Object[]{parsenum(this.pnume), parsenum(this.pqe)});
        }

    }

    private void updbtn() {
        boolean canbuy = this.price != null && this.pnum > 0;
        if ((!canbuy || this.bbtn != null) && this.bbtn100 != null) {
            if (!canbuy && this.bbtn != null) {
                this.bbtn.reqdestroy();
                this.bbtn = null;
                this.bbtn100 = null;
            }
        } else {
            this.bbtn = (Button)this.add(new Button(75, "Buy"), buyc);
            this.bbtn.tooltip = "Left click to buy 1, Shift left click to buy 5, ctrl shift left click to buy 20.";
            this.bbtn100 = (Button)this.add(new Button(75, "Buy x"), buyca);
            this.bbtn100.tooltip = "Type the number in box press enter and press this button.";
            this.tbuy = (TextEntry)this.add(new TextEntry(40, "") {
                String backup = this.text();

                public boolean keydown(KeyEvent e) {
                    if (e.getKeyCode() == 10) {
                        try {
                            Shopbox.this.count = Integer.parseInt(this.dtext());
                            return true;
                        } catch (NumberFormatException var5) {
                        }
                    }

                    this.backup = this.text();
                    boolean b = super.keydown(e);

                    try {
                        if (!this.text().isEmpty()) {
                            Integer.parseInt(this.text());
                        }

                        if (this.text().length() > 2) {
                            this.settext(this.backup);
                        }
                    } catch (Exception var4) {
                        this.settext(this.backup);
                    }

                    return b;
                }
            }, new Coord(85, 70));
        }

    }

    private static Text rnum(String fmt, int n) {
        return n < 1 ? null : Text.render(String.format(fmt, n));
    }

    public void uimsg(String name, Object... args) {
        if (name == "res") {
            this.res = null;
            this.spr = null;
            if (args.length > 0) {
                ResData res = new ResData(this.ui.sess.getres((Integer)args[0]), Message.nil);
                if (args.length > 1) {
                    res.sdt = new MessageBuf((byte[])args[1]);
                }

                this.res = res;
            }

            this.updbtn();
        } else if (name == "tt") {
            this.info = args;
            this.cinfo = null;
            this.longtip = null;
            this.fulltip = null;
        } else {
            int a;
            if (name == "n") {
                a = (Integer)args[0];
                this.num = Text.render(String.format("%d left", a));
            } else if (name == "price") {
                a = 0;
                if (args[a] == null) {
                    a = a + 1;
                    this.price = null;
                } else {
                    a = a + 1;
                    Indir<Resource> res = this.ui.sess.getres((Integer)args[a]);
                    Message sdt = Message.nil;
                    if (args[a] instanceof byte[]) {
                        sdt = new MessageBuf((byte[])args[a++]);
                    }

                    Object[] info = null;
                    if (args[a] instanceof Object[]) {
                        for(info = new Object[0][]; args[a] instanceof Object[]; info = Utils.extend((Object[])info, args[a++])) {
                        }
                    }

                    this.price = new Spec(new ResData(res, (Message)sdt), Spec.uictx(this.ui), (Object[])info);
                }

                this.pricetip = null;
                this.pnum = (Integer)args[a++];
                this.pq = (Integer)args[a++];
                if (!this.admin) {
                    this.pnumt = rnum("×%d", this.pnum);
                    this.pqt = this.pq > 0 ? rnum("%d+", this.pq) : any;
                } else {
                    this.pnume.settext(this.pnum > 0 ? Integer.toString(this.pnum) : "");
                    this.pnume.commit();
                    this.pqe.settext(this.pq > 0 ? Integer.toString(this.pq) : "");
                    this.pqe.commit();
                }

                this.updbtn();
            } else {
                super.uimsg(name, args);
            }
        }

    }

    public <C> C context(Class<C> con) {
        return Spec.uictx.context(con, this.ui);
    }

    static {
        qualc = (new Coord(200, 5)).add(Inventory.invsq.sz()).add(40, 0);
        cbtnc = new Coord(200, 66);
        spipec = new Coord(85, 40);
        bpipec = new Coord(280, 66);
    }

    class One extends AttrCache<Tex> {
        One(Shopbox shopbox) {
            super(shopbox);
        }

        protected Tex find(List<ItemInfo> info) {
            GItem.NumberInfo numberInfo = (GItem.NumberInfo)ItemInfo.find(GItem.NumberInfo.class, info);
            return numberInfo == null ? null : new TexI(Utils.outline2(Text.render(Integer.toString(numberInfo.itemnum()), Color.WHITE).img, Utils.contrast(Color.WHITE)));
        }
    }

    public abstract class AttrCache<T> {
        private List<ItemInfo> forinfo = null;
        private T save = null;

        public AttrCache(Shopbox shopbox) {
        }

        public T get() {
            try {
                List<ItemInfo> info = Shopbox.this.info();
                if (info != this.forinfo) {
                    this.save = this.find(info);
                    this.forinfo = info;
                }
            } catch (Loading var2) {
                return null;
            }

            return this.save;
        }

        protected abstract T find(List<ItemInfo> var1);
    }
}
