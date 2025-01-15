package haven.res.lib.tspec;

import haven.FromResource;
import haven.GSprite;
import haven.Glob;
import haven.ItemInfo;
import haven.OwnerContext;
import haven.ResData;
import haven.Resource;
import haven.Session;
import haven.Tex;
import haven.TexI;
import haven.UI;
import java.util.List;
import java.util.Random;

@FromResource(
        name = "lib/tspec",
        version = 3,
        override = true
)
public class Spec implements GSprite.Owner, ItemInfo.SpriteOwner {
    private static final Object[] definfo = new Object[]{new Object[]{Resource.remote().loadwait("ui/tt/defn")}};
    public final Object[] info;
    public final ResData res;
    public final OwnerContext ctx;
    public static final ClassResolver<UI> uictx = new ClassResolver<UI>()
            .add(Glob.class, ui -> ui.sess.glob)
            .add(Session.class, ui -> ui.sess);
    private Random rnd = null;
    private GSprite spr = null;
    private List<ItemInfo> cinfo = null;

    public Spec(ResData res, OwnerContext ctx, Object[] info) {
        this.res = res;
        this.ctx = ctx;
        this.info = info == null ? definfo : info;
    }

    public static OwnerContext uictx(final UI ui) {
        return new OwnerContext() {
            public <C> C context(Class<C> cl) {
                return uictx.context(cl, ui);
            }
        };
    }

    public <T> T context(Class<T> cl) {
        return this.ctx.context(cl);
    }

    /** @deprecated */
    @Deprecated
    public Glob glob() {
        return (Glob)this.context(Glob.class);
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

    public GSprite sprite() {
        return this.spr;
    }

    public Resource resource() {
        return (Resource)this.res.res.get();
    }

    public GSprite spr() {
        if (this.spr == null) {
            this.spr = GSprite.create(this, (Resource)this.res.res.get(), this.res.sdt.clone());
        }

        return this.spr;
    }

    public List<ItemInfo> info() {
        if (this.cinfo == null) {
            this.cinfo = ItemInfo.buildinfo(this, this.info);
        }

        return this.cinfo;
    }

    public Tex longtip() {
        return new TexI(ItemInfo.longtip(this.info()));
    }

    public String name() {
        GSprite spr = this.spr();
        ItemInfo.Name nm = (ItemInfo.Name)ItemInfo.find(ItemInfo.Name.class, this.info());
        return nm == null ? null : nm.str.text;
    }
}

