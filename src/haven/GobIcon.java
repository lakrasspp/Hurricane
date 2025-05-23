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

import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.io.*;
import java.nio.file.*;
import java.awt.image.*;
import java.awt.Color;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.filechooser.*;

public class GobIcon extends GAttrib {
    private static final int size = UI.scale(20);
    public static final PUtils.Convolution filter = new PUtils.Hanning(1);
    public final Indir<Resource> res;
    public final byte[] sdt;
    private Icon icon;
	private static LinkedHashMap<String, ArrayList<String>> mapIconPresets = new LinkedHashMap<String, ArrayList<String>>();

    public GobIcon(Gob g, Indir<Resource> res, byte[] sdt) {
	super(g);
	this.res = res;
	this.sdt = sdt;
    }


    public static abstract class Icon {
	public static final Object[] nilid = new Object[0];
	public final OwnerContext owner;
	public final Resource res;

	public Icon(OwnerContext owner, Resource res) {
	    this.owner = owner;
	    this.res = res;
	}

	public static enum Markable {
	    UNMARKABLE, NONDEFAULT, DEFAULT
	}

	public abstract String name();
	public abstract BufferedImage image();
	public abstract void draw(GOut g, Coord cc);
	public abstract boolean checkhit(Coord c);
	public Object[] id() {return(nilid);}
	public int z() {return(0);}
	public Markable markable() {return(Markable.UNMARKABLE);}

	@Resource.PublishedCode(name = "mapicon")
	public static interface Factory {
	    public Icon create(OwnerContext owner, Resource res, Message sdt);
	    public Collection<? extends Icon> enumerate(OwnerContext owner, Resource res, Message sdt);
	}

		public String tooltip() {
			String tt = name();
			try {
				Resource.Tooltip name = res.layer(Resource.tooltip);
				tt = (name == null) ? res.name : name.t;
			} catch (Resource.Loading ignored) {}
			return tt;
		}
    }

    public static class Image {
	private static final Map<Resource, Image> cache = new WeakHashMap<>();
	public final BufferedImage img;
	public final Tex tex;
	public Coord cc;
	public boolean rot;
	public double ao;
	public int z;

	public Image(Resource res) {
	    Resource.Image rimg = res.layer(Resource.imgc);
	    BufferedImage img = rimg.scaled();
	    Tex tex = rimg.tex();
	    if ((tex.sz().x > size) || (tex.sz().y > size)) {
		BufferedImage buf = rimg.img;
		buf = PUtils.rasterimg(PUtils.blurmask2(buf.getRaster(), 1, 1, Color.BLACK));
		Coord tsz;
		if(buf.getWidth() > buf.getHeight())
		    tsz = new Coord(size, (size * buf.getHeight()) / buf.getWidth());
		else
		    tsz = new Coord((size * buf.getWidth()) / buf.getHeight(), size);
		buf = PUtils.convolve(buf, tsz, filter);
		tex = new TexI(img = buf);
	    }
	    this.img = img;
	    this.tex = tex;
	    this.cc = tex.sz().div(2);
	    byte[] data = rimg.kvdata.get("mm/rot");
	    if(data != null) {
		this.rot = true;
		this.ao = Utils.float32d(data, 0) * (Math.PI / 180f);
	    }
	    this.z = rimg.z;
	    data = rimg.kvdata.get("mm/z");
	    if(data != null)
		this.z = Utils.intvard(data, 0);
	}

	public static Image get(Resource res) {
	    synchronized(cache) {
		Image img = cache.get(res);
		if(img == null) {
		    img = new Image(res);
		    cache.put(res, img);
		}
		return(img);
	    }
	}
    }

    public static class ImageIcon extends Icon {
	public final Image img;
	private final Gob gob = owner.fcontext(Gob.class, false);

	public ImageIcon(OwnerContext owner, Resource res, Image img) {
	    super(owner, res);
	    this.img = img;
	}

	public String name() {
	    Resource.Tooltip name = res.layer(Resource.tooltip);
	    return(name == null ? "???" : name.t);
	}

	public BufferedImage image() {
	    return(res.flayer(Resource.imgc).img);
	}

	public void draw(GOut g, Coord cc) {
	    if(!img.rot)
		g.image(img.tex, cc.sub(img.cc));
	    else
		g.rotimage(img.tex, cc, img.cc, ((gob == null) ? 0 : -gob.a) + img.ao);
	}

	public boolean checkhit(Coord c) {
	    Coord oc = c.add(img.cc);
	    if(!oc.isect(Coord.z, PUtils.imgsz(img.img)))
		return(false);
	    if(img.img.getRaster().getNumBands() < 4)
		return(true);
	    return(img.img.getRaster().getSample(oc.x, oc.y, 3) >= 128);
	}

	public int z() {
	    return(img.z);
	}

	private int markdata() {
	    byte[] data = res.flayer(Resource.imgc).kvdata.get("mm/mark");
	    if(data == null)
		return(0);
	    return(Utils.intvard(data, 0));
	}

	public Markable markable() {
	    switch(markdata()) {
	    case 1:
		return(Markable.NONDEFAULT);
	    case 2:
		return(Markable.DEFAULT);
	    default:
		return(Markable.UNMARKABLE);
	    }
	}

	public static final Factory factory = new Factory() {
		public ImageIcon create(OwnerContext owner, Resource res, Message sdt) {
		    return(new ImageIcon(owner, res, Image.get(res)));
		}

		public Collection<ImageIcon> enumerate(OwnerContext owner, Resource res, Message sdt) {
		    return(Collections.singletonList(new ImageIcon(owner, res, Image.get(res))));
		}
	    };
    }

    public static Icon.Factory getfac(Resource res) {
	Icon.Factory fac = res.getcode(Icon.Factory.class, false);
	if(fac != null)
	    return(fac);
	return(ImageIcon.factory);
    }

    public Icon icon() {
	if(this.icon == null) {
	    Resource res = this.res.get();
	    this.icon = getfac(res).create(gob, res, new MessageBuf(sdt));
	}
	return(this.icon);
    }

    private static Consumer<UI> resnotif(String nm) {
	return(ui -> {
		Indir<Resource> resid = Resource.local().load(nm);
		ui.sess.glob.loader.defer(() -> {
			Resource res;
			try {
			    res = resid.get();
			} catch(Loading l) {
			    throw(l);
			} catch(RuntimeException e) {
			    ui.error("Could not play " + nm);
			    return;
			}
			Audio.CS clip = Audio.fromres(res);
			ui.sfx(clip);
		    }, null);
	    });
    }

    private static Consumer<UI> wavnotif(Path path) {
	return(ui -> {
		ui.sess.glob.loader.defer(() -> {
			Audio.CS clip;
			InputStream fail = null;
			try {
			    fail = Files.newInputStream(path);
			    clip = Audio.PCMClip.fromwav(new BufferedInputStream(fail));
			    fail = null;
			} catch(IOException e) {
			    String msg = e.getMessage();
			    if(e instanceof FileSystemException)
				msg = "Could not open file";
			    ui.error("Could not play " + path + ": " + msg);
			    return;
			} finally {
			    if(fail != null) {
				try {
				    fail.close();
				} catch(IOException e) {
				    new Warning(e, "unexpected error on close").issue();
				}
			    }
			}
			ui.sfx(clip);
		    }, null);
	    });
    }

    private static final Map<Object, Double> lastnotifs = new HashMap<>();
    private static Consumer<UI> notiflimit(Consumer<UI> bk, Object id) {
	return(ui -> {
		double now = Utils.rtime();
		synchronized(lastnotifs) {
		    Double last = lastnotifs.get(id);
		    if((last != null) && (now - last < 0.5))
			return;
		    lastnotifs.put(id, now);
		}
		bk.accept(ui);
	    });
    }

    public static class Setting implements Serializable {
	public final ID id;
	public final Icon icon;
	public final Settings.ResID from;
	public Resource.Saved res;
	public boolean show, defshow, notify;
	public String resns;
	public Path filens;
	public boolean mark, markset;

	public static class ID {
	    public final String res;
	    public final Object[] sub;

	    public ID(String res, Object[] sub) {
		this.res = res;
		this.sub = sub;
	    }

	    public int hashCode() {
		return((res.hashCode() * 31) + Arrays.deepHashCode(sub));
	    }

	    public boolean equals(ID that) {
		return(this.res.equals(that.res) && Arrays.deepEquals(this.sub, that.sub));
	    }

	    public boolean equals(Object x) {
		return((x instanceof ID) && equals((ID)x));
	    }
	}

	public Setting(Resource.Saved res, Object[] id, Icon icon, Settings.ResID from) {
	    this.res = res;
	    this.id = new ID(res.name, id);
	    this.icon = icon;
	    this.from = from;
	}

	public Setting(Resource.Saved res, Object[] id) {
	    this(res, id, null, null);
	}

	public Setting(Icon icon, Settings.ResID from) {
	    this(new Resource.Saved(Resource.remote(), icon.res.name, icon.res.ver), icon.id(), icon, from);
	}

	public Consumer<UI> notification() {
	    if(resns != null)
		return(notiflimit(resnotif(resns), resns));
	    if(filens != null)
		return(notiflimit(wavnotif(filens), filens));
	    return(null);
	}

	private Resource lres;
	public Resource resource() {
	    if(this.lres != null)
		return(this.lres);
	    return(this.lres = this.res.get());
	}

	public boolean getmarkablep() {
	    return(icon.markable() != Icon.Markable.UNMARKABLE);
	}

	public boolean getmarkp() {
	    if(markset)
		return(mark);
	    return(icon.markable() == Icon.Markable.DEFAULT);
	}
    }

    public static class Settings implements OwnerContext, Serializable {
	public static final byte[] sig = "Icons".getBytes(Utils.ascii);
	public final UI ui;
	public final String filename;
	public Map<Setting.ID, Setting> settings = new HashMap<>();
	public int tag = -1;
	public boolean notify = false;

	public Settings(UI ui, String filename) {
	    this.ui = ui;
	    this.filename = filename;
	}

	public Setting get(Icon icon) {
	    return(settings.get(new Setting.ID(icon.res.name, icon.id())));
	}

	public static class ResID {
	    public final Resource.Saved res;
	    public final byte[] data;

	    public ResID(Resource.Saved res, byte[] data) {
		this.res = res;
		this.data = data;
	    }

	    public int hashCode() {
		return((res.name.hashCode() * 31) + Arrays.hashCode(data));
	    }

	    public boolean equals(ResID that) {
		return(this.res.name.equals(that.res.name) && Arrays.equals(this.data, that.data));
	    }

	    public boolean equals(Object x) {
		return((x instanceof ResID) && equals((ResID)x));
	    }
	}

	private Loader loading = null;
	public class Loader implements Runnable {
	    public final Queue<ResID> load = new ArrayDeque<>();
	    public final Map<ResID, Setting> defaults = new HashMap<>();
	    public final Map<ResID, Collection<Setting>> resolve = new HashMap<>();
	    public boolean save = false, adv = false;
	    public Integer tag = null;
	    private final Collection<Icon> advbuf = new ArrayList<>();
	    private ResID r = null;
	    private Loader next = null;
	    private Map<Setting.ID, Setting> nset = null;

	    private void merge(Setting set, Setting conf) {
		set.show    = conf.show;
		set.defshow = conf.defshow;
		set.notify  = conf.notify;
		set.resns   = conf.resns;
		set.filens  = conf.filens;
		if(set.markset = conf.markset)
		    set.mark = conf.mark;
		if (Config.mandatoryAlwaysEnabledMapIcons.keySet().stream().anyMatch(set.icon.name()::equals)) // ND: Do this to make sure mandatory icons are always enabled. Idk if there's a better place to put this.
			set.defshow = set.show = true;
	    }

	    public void run() {
		if(nset == null)
		    nset = new HashMap<>(settings);
		while(true) {
		    if((r == null) && ((r = load.poll()) == null))
			break;
		    Resource res;
		    try {
			res = r.res.get(-10);
		    } catch(Resource.NoSuchResourceException e) {
			r = null;
			continue;
		    }
		    Icon.Factory fac = getfac(res);
		    for(Icon icon : fac.enumerate(Settings.this, res, new MessageBuf(r.data))) {
			Setting set = new Setting(icon, r);
			Setting def = defaults.get(r);
			if(def != null)
			    merge(set, def);
			Setting prev = nset.get(set.id);
			if((prev == null) || (prev.res.ver < set.res.ver)) {
			    if(prev != null)
				merge(set, prev);
			    else
				advbuf.add(icon);
			    nset.put(set.id, set);
			}
		    }
		    Collection<Setting> sets = resolve.remove(r);
		    if(sets != null) {
			for(Setting conf : sets) {
			    Setting set = nset.get(conf.id);
			    if(set != null)
				merge(set, conf);
			}
		    }
		    r = null;
		}
		settings = nset;
		if(tag != null)
		    Settings.this.tag = tag;
		if(save)
		    save();
		if(adv && notify) {
		    Set<String> names = new HashSet<>();
		    advbuf.forEach(icon -> names.add(icon.name()));
		    synchronized(ui) {
			for(String nm : names)
			    ui.msg(String.format("%s added to list of seen icons.", nm));
		    }
		}
		synchronized(Settings.this) {
		    if(loading == this)
			loading = null;
		    else
			ui.loader.defer(next, null);
		}
	    }

	    public void submit() {
		synchronized(Settings.this) {
		    if(loading == null)
			ui.loader.defer(this, null);
		    else
			loading.next = this;
		    loading = this;
		}
	    }
	}

	private final ClassResolver<Settings> ctxr = new ClassResolver<Settings>()
	    .add(Glob.class, s -> s.ui.sess.glob)
	    .add(Session.class, s -> s.ui.sess);
	public <T> T context(Class<T> cl) {return(ctxr.context(cl, this));}

	public void receive(Object[] args) {
	    int tag = Utils.iv(args[0]);
	    if(args[1] instanceof String) {
		int a = 1;
		Resource.Saved res = new Resource.Saved(Resource.remote(), (String)args[a++], Utils.iv(args[a++]));
		byte[] data = (args[a] instanceof byte[]) ? (byte[])args[a++] : new byte[0];
		ResID id = new ResID(res, data);
		Setting def = new Setting(res, Icon.nilid);
		def.show = def.defshow = Utils.bv(args[a++]);
		Loader l = new Loader();
		l.save = true;
		l.adv = true;
		l.tag = tag;
		l.load.add(id);
		l.defaults.put(id, def);
		l.submit();
	    } else if(args[1] instanceof Object[]) {
		Object[] sub = (Object[])args[1];
		int a = 0;
		Loader l = new Loader();
		l.save = true;
		l.tag = tag;
		Collection<GobIcon.Setting> csets = new ArrayList<>();
		while(a < sub.length) {
		    Resource.Saved res = new Resource.Saved(Resource.remote(), (String)sub[a++], Utils.iv(sub[a++]));
		    byte[] data = (sub[a] instanceof byte[]) ? (byte[])sub[a++] : new byte[0];
		    int fl = Utils.iv(sub[a++]);
		    ResID id = new ResID(res, data);
		    Setting def = new Setting(res, Icon.nilid);
		    def.show = def.defshow = ((fl & 1) != 0);
		    l.load.add(id);
		    l.defaults.put(id, def);
		}
		l.submit();
	    }
	}

	private static void encodeset(Map<Object, Object> buf, Setting set) {
	    if(set.show)    buf.put("s", 1);
	    if(set.defshow) buf.put("d", 1);
	    if(set.notify)  buf.put("n", 1);
	    if(set.markset) buf.put("m", set.mark ? 1 : 0);
	    if(set.resns != null)  buf.put("R", set.resns);
	    if(set.filens != null) buf.put("W", set.filens.toString());
	}

	public void save(Message dst) {
	    Map<ResID, Collection<Setting>> byid = new HashMap<>();
	    for(Setting set : settings.values())
		byid.computeIfAbsent(set.from, k -> new ArrayList<>()).add(set);
	    Map<Object, Object> buf = new HashMap<>();
	    buf.put("tag", tag);
	    if(notify)
		buf.put("notify", 1);
	    List<Object> abuf = new ArrayList<>();
	    for(Map.Entry<ResID, Collection<Setting>> ent : byid.entrySet()) {
		ResID id = ent.getKey();
		Map<Object, Object> rbuf = new HashMap<>();
		if(id.data.length == 0)
		    rbuf.put("res", new Object[] {id.res.name, id.res.savever()});
		else
		    rbuf.put("res", new Object[] {id.res.name, id.res.savever(), id.data});
		Collection<Object> sub = new ArrayList<>();
		for(Setting set : ent.getValue()) {
		    if(set.id.sub.length == 0) {
			encodeset(rbuf, set);
		    } else {
			Map<Object, Object> sbuf = new HashMap<>();
			sbuf.put("id", set.id.sub);
			encodeset(sbuf, set);
			sub.add(Utils.mapencn(sbuf));
		    }
		}
		if(!sub.isEmpty())
		    rbuf.put("sub", sub.toArray(new Object[0]));
		abuf.add(Utils.mapencn(rbuf));
	    }
	    buf.put("icons", abuf.toArray(new Object[0]));

	    dst.addbytes(sig);
	    dst.adduint8(3);
	    dst.addlist(Utils.mapencn(buf));
	}

	private static void parseset(Setting set, Map<Object, Object> data) {
	    set.show    = Utils.bv(data.getOrDefault("s", 0));
	    set.defshow = Utils.bv(data.getOrDefault("d", 0));
	    set.notify  = Utils.bv(data.getOrDefault("n", 0));
	    set.resns   = (String)data.getOrDefault("R", null);
	    if(data.containsKey("m")) {
		set.markset = true;
		set.mark = Utils.bv(data.get("m"));
	    }
	    try {
		set.filens = Utils.path((String)data.getOrDefault("W", null));
	    } catch(RuntimeException e) {
		new Warning(e, "could not read path").issue();
	    }
	}

	public void load(Message blob) {
	    if(!Arrays.equals(blob.bytes(sig.length), sig))
		throw(new Message.FormatError("Invalid signature"));
	    int ver = blob.uint8();
	    if((ver < 3) || (ver > 3))
		throw(new Message.FormatError("Unknown version: " + ver));
	    Map<Object, Object> root = Utils.mapdecn(blob.tto());
	    this.tag = Utils.iv(root.get("tag"));
	    this.notify = Utils.bv(root.getOrDefault("notify", 0));
	    Loader l = new Loader();
	    for(Object eicon : (Object[])root.get("icons")) {
		Map<Object, Object> icon = Utils.mapdecn(eicon);
		Object[] eres = (Object[])icon.get("res");
		ResID res = new ResID(new Resource.Saved(Resource.remote(), (String)eres[0], Utils.iv(eres[1])),
				      (eres.length > 2) ? (byte[])eres[2] : new byte[0]);
		Collection<Setting> sets = new ArrayList<>();
		Setting set = new Setting(res.res, Icon.nilid);
		parseset(set, icon);
		sets.add(set);
		if(icon.containsKey("sub")) {
		    for(Object esub : (Object[])icon.get("sub")) {
			Map<Object, Object> sub = Utils.mapdecn(esub);
			set = new Setting(res.res, (Object[])sub.get("id"));
			parseset(set, sub);
			sets.add(set);
		    }
		}
		l.load.add(res);
		l.resolve.put(res, sets);
	    }
		GobIconsCustom.addCustomSettings(l, ui);
	    l.submit();
	}

	public void save() {
	    if(ResCache.global == null)
		return;
	    try(StreamMessage fp = new StreamMessage(ResCache.global.store(filename))) {
		save(fp);
	    } catch(Exception e) {
		new Warning(e, "failed to store icon-conf").issue();
	    }
	}

	private boolean saveagain = false, saving = false;
	private void dsave0() {
	    save();
	    synchronized(this) {
		if(saveagain) {
		    Defer.later(this::dsave0, null);
		    saveagain = false;
		} else {
		    saving = false;
		}
	    }
	}

	public void dsave() {
	    synchronized(this) {
		if(!saving) {
		    Defer.later(this::dsave0, null);
		    saving = true;
		} else {
		    saveagain = true;
		}
	    }
	}

	public static Settings load(UI ui, String name) throws IOException {
	    if(ResCache.global == null)
		return(new Settings(ui, name));
	    try(StreamMessage fp = new StreamMessage(ResCache.global.fetch(name))) {
		Settings ret = new Settings(ui, name);
		ret.load(fp);
		return(ret);
	    } catch(FileNotFoundException e) {
	    }
	    return(new Settings(ui, name));
	}
    }

    public static class NotificationSetting {
	public final String name, res;
	public final Path wav;

	private NotificationSetting(String name, String res, Path wav) {this.name = name; this.res = res; this.wav = wav;}
	public NotificationSetting(String name, String res) {this(name, res, null);}
	public NotificationSetting(String name, Path wav)   {this(name, null, wav);}
	public NotificationSetting(Path wav) {this(wav.getFileName().toString(), wav);}

	public boolean act(Setting conf) {
	    return(Utils.eq(conf.resns, this.res) && Utils.eq(conf.filens, wav));
	}

	public static final NotificationSetting nil = new NotificationSetting("None", null, null);
	public static final NotificationSetting other = new NotificationSetting("Select file...", null, null);
	public static final List<NotificationSetting> builtin;

	static {
	    List<NotificationSetting> buf = new ArrayList<>();
	    buf.add(new NotificationSetting("Bell 1", "sfx/hud/mmap/bell1"));
	    buf.add(new NotificationSetting("Bell 2", "sfx/hud/mmap/bell2"));
	    buf.add(new NotificationSetting("Bell 3", "sfx/hud/mmap/bell3"));
	    buf.add(new NotificationSetting("Wood 1", "sfx/hud/mmap/wood1"));
	    buf.add(new NotificationSetting("Wood 2", "sfx/hud/mmap/wood2"));
	    buf.add(new NotificationSetting("Wood 3", "sfx/hud/mmap/wood3"));
	    buf.add(new NotificationSetting("Wood 4", "sfx/hud/mmap/wood4"));
	    builtin = buf;
	}
    }

    public static class SettingsWindow extends Window {
	public final Settings conf;
	private final PackCont.LinPack cont;
	private final IconList list;
	private Widget setbox;
	private final CheckBox selectAllCheckBox;
	private GobIconCategoryList.GobCategory category = GobIconCategoryList.GobCategory.ALL;
	private GobIconCategoryList iconCategoriesList;
	private OldDropBox iconPresetsDropbox;
	private String selectedPreset = null;
	private ArrayList<String> enabledIcons = null;
	private TextEntry newPresetName = null;
	Window confirmOverwriteWnd = null;
	private static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private static Future<?> future;

	public static class ListIcon {
	    public final Setting conf;
	    public final String name;
	    public final Object[] id;
		public Tex texName = null;

	    public ListIcon(Setting conf) {
		this.conf = conf;
		this.name = conf.icon.name();
		this.id = conf.icon.id();
	    }

		private Tex texImg = null;
		public Tex texImg() {
			if(this.texImg == null) {
				this.texImg = tex(conf.res.get().layer(Resource.imgc).img);
			}
			return(this.texImg);
		}

		public static Tex tex(BufferedImage img) {
			Coord tsz;
			if(img.getWidth() > img.getHeight())
				tsz = new Coord(elh, (elh * img.getHeight()) / img.getWidth());
			else
				tsz = new Coord((elh * img.getWidth()) / img.getHeight(), elh);
			return(new TexI(PUtils.convolve(img, tsz, filter)));
		}
	}

	private <T> Consumer<T> andsave(Consumer<T> main) {
	    return(val -> {main.accept(val); conf.dsave();});
	}

	private static final Text.Foundry elf = CharWnd.attrf;
	private static final int elh = elf.height() + UI.scale(2);
	public class IconList extends SSearchBox<ListIcon, IconList.IconLine> {
	    private List<ListIcon> ordered = Collections.emptyList();
	    private Map<Setting.ID, Setting> cur = null;
		private List<ListIcon> categorized = Collections.emptyList();
		private boolean needsReorder = false;

	    private IconList(Coord sz) {
		super(sz, elh);
	    }

	    public class IconLine extends SListWidget.ItemWidget<ListIcon> {
		public IconLine(Coord sz, ListIcon icon) {
		    super(IconList.this, sz, icon);
		    Widget prev;
		    prev = adda(new CheckBox("").state(() -> icon.conf.notify).set(andsave(val -> icon.conf.notify = val)).settip("Notify"),
				sz.x - UI.scale(2) - (sz.y / 2), sz.y / 2, 0.5, 0.5);
		    prev = adda(new CheckBox(""){
						@Override
						public void set(boolean val) {
							if (Config.mandatoryAlwaysEnabledMapIcons.keySet().stream().anyMatch(icon.name::equals)){
								icon.conf.show = true;
								ui.gui.error(Config.mandatoryAlwaysEnabledMapIcons.get(icon.name));
							} else {
								icon.conf.show = val;
							}
							SettingsWindow.this.conf.dsave();
							updateSelectAllCheckbox();
							if (ui != null && ui.gui != null) {
								ui.sess.glob.oc.gobAction(Gob::updateHidingBoxes);
								ui.gui.map.updatePlobHidingBox();
							}
						}
					}.state(() -> icon.conf.show).settip("Show icon on map"),
				prev.c.x - UI.scale(2) - (sz.y / 2), sz.y / 2, 0.5, 0.5);
		    add(SListWidget.IconText.of(Coord.of(prev.c.x - UI.scale(2), sz.y), item.conf.icon::image, item.conf.icon::name), Coord.z);
		}
	    }

	    protected boolean searchmatch(ListIcon icon, String text) {
		return((icon.name != null) &&
		       (icon.name.toLowerCase().indexOf(text.toLowerCase()) >= 0));
	    }

		@Override
		public void updinfo() {
			super.updinfo();
			updateSelectAllCheckbox();
		}

		protected List<ListIcon> allitems() {return(categorized);}
	    protected IconLine makeitem(ListIcon icon, int idx, Coord sz) {return(new IconLine(sz, icon));}

	    public void tick(double dt) {
		Map<Setting.ID, Setting> cur = this.cur;
		if(cur != conf.settings || needsReorder) {
		    cur = conf.settings;
		    ArrayList<ListIcon> ordered = new ArrayList<>(cur.size());
		    for(Setting conf : cur.values())
			ordered.add(new ListIcon(conf));
		    this.cur = cur;
		    this.ordered = ordered;
		    Collections.sort(ordered, (a, b) -> {
			    int c;;
			    if((c = a.name.compareTo(b.name)) != 0)
				return(c);
			    if((c = Utils.compare(a.id, b.id)) != 0)
				return(c);
			    return(0);
			});
			categorized = list.ordered.stream()
					.filter(category::matches)
					.collect(Collectors.toList());
			needsReorder = false;
			updateSelectAllCheckbox();
			stopsearch(); // ND: Do this here to clear the search text when switching categories.
		}
		super.tick(dt);
	    }

	    public boolean keydown(KeyDownEvent ev) {
//		if(ev.code == ev.awt.VK_SPACE) {
//		    if(sel != null) {
//			sel.conf.show = !sel.conf.show;
//			conf.dsave();
//		    }
//		    return(true);
//		}
		return(super.keydown(ev));
	    }

	    public void change(ListIcon icon) {
		super.change(icon);
		if(setbox != null) {
		    setbox.destroy();
		    setbox = null;
		}
		if(icon != null) {
		    setbox = cont.after(new IconSettings(sz.x - UI.scale(10), icon.conf), list, UI.scale(5));
		}
	    }
	}

	public class IconSettings extends Widget {
	    public final Setting conf;
	    public final NotifBox nb;

	    public IconSettings(int w, Setting conf) {
		super(Coord.z);
		this.conf = conf;
		Widget prev = add(new CheckBox("Show icon on map"){
					@Override
					public void set(boolean val) {
						if (Config.mandatoryAlwaysEnabledMapIcons.keySet().stream().anyMatch(conf.icon.name()::equals)){
							conf.show = true;
							ui.gui.error(Config.mandatoryAlwaysEnabledMapIcons.get(conf.icon.name()));
						} else {
							conf.show = val;
						}
						SettingsWindow.this.conf.dsave();
						updateSelectAllCheckbox();
						if (ui != null && ui.gui != null) {
							ui.sess.glob.oc.gobAction(Gob::updateHidingBoxes);
							ui.gui.map.updatePlobHidingBox();
						}
					}
				}.state(() -> conf.show),
				  0, 0);
		add(new CheckBox("Notify").state(() -> conf.notify).set(andsave(val -> conf.notify = val)),
		    w / 2, 0);
		Button pb = new Button(UI.scale(50), "Play") {
			protected void depress() {}
			protected void unpress() {}
			public void click() {play();}
		    };
		prev = add(new Label("Sound to play on notification:"), prev.pos("bl").adds(0, 5));
		nb = new NotifBox(w - pb.sz.x - UI.scale(15));
		addhl(prev.pos("bl").adds(0, 2), w, prev = Frame.with(nb, false), pb);
		if(conf.getmarkablep()) {
		    add(new CheckBox("Place permanent marker")
			.state(() -> conf.markset ? conf.mark : conf.getmarkp())
			.set(andsave(val -> {conf.markset = true; conf.mark = val;})),
			prev.pos("bl").adds(0, 5));
		}
		pack();
	    }

	    public class NotifBox extends SDropBox<NotificationSetting, Widget> {
		private final List<NotificationSetting> items = new ArrayList<>();

		public NotifBox(int w) {
		    super(w, UI.scale(160), UI.scale(20));
		    items.add(NotificationSetting.nil);
		    for(NotificationSetting notif : NotificationSetting.builtin)
			items.add(notif);
		    if(conf.filens != null)
			items.add(new NotificationSetting(conf.filens));
		    items.add(NotificationSetting.other);
		    for(NotificationSetting item : items) {
			if(item.act(conf)) {
			    change(item);
			    break;
			}
		    }
		}

		protected List<NotificationSetting> items() {return(items);}
		protected Widget makeitem(NotificationSetting item, int idx, Coord sz) {return(SListWidget.TextItem.of(sz, Text.std, () -> item.name));}

		private void selectwav() {
		    java.awt.EventQueue.invokeLater(() -> {
			    JFileChooser fc = new JFileChooser();
			    fc.setFileFilter(new FileNameExtensionFilter("PCM wave file", "wav"));
			    if(fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
				return;
			    for(Iterator<NotificationSetting> i = items.iterator(); i.hasNext();) {
				NotificationSetting item = i.next();
				if(item.wav != null)
				    i.remove();
			    }
			    NotificationSetting ws = new NotificationSetting(fc.getSelectedFile().toPath());
			    items.add(items.indexOf(NotificationSetting.other), ws);
			    change(ws);
			});
		}

		public void change(NotificationSetting item) {
		    super.change(item);
		    if(item == NotificationSetting.other) {
			selectwav();
		    } else {
			conf.resns = item.res;
			conf.filens = item.wav;
			SettingsWindow.this.conf.dsave();
		    }
		}
	    }

	    private void play() {
		NotificationSetting sel = nb.sel;
		if(sel == null) sel = NotificationSetting.nil;
		if(sel.res != null)
		    resnotif(sel.res).accept(ui);
		else if(sel.wav != null)
		    wavnotif(sel.wav).accept(ui);
	    }
	}

	public SettingsWindow(Settings conf) {
	    super(Coord.z, "Map Icons Settings");
	    this.conf = conf;
		PackCont.LinPack.VPack left = new PackCont.LinPack.VPack();
		PackCont.LinPack root = new PackCont.LinPack.HPack();
		add(root, Coord.z).margin(UI.scale(5)).packpar(true);
		root.last(left, 0).margin(UI.scale(5)).packpar(true);
		root.last(new VRuler(UI.scale(500)), 0);
		root.last(this.cont = new PackCont.LinPack.VPack(), 0).margin(UI.scale(5)).packpar(true);
		selectAllCheckBox = cont.last(new CheckBox("Select All") {
			@Override
			public void changed(boolean val) {
				try {
					list.items().forEach(icon -> {
						// ND: First check if it's a mandatory icon, and keep it enabled at all times
						if (Config.mandatoryAlwaysEnabledMapIcons.keySet().stream().anyMatch(icon.conf.res.name::equals))
							icon.conf.show = true;
						else
							icon.conf.show = val;
					});
					conf.dsave();
					if (ui != null && ui.gui != null) {
						ui.sess.glob.oc.gobAction(Gob::updateHidingBoxes);
						ui.gui.map.updatePlobHidingBox();
					}
				} catch (Loading ignored){} // ND: It crashes if you click on "Select all" while some buttons are still loading. This should prevent it.
			}}, 0);
	    list = cont.last(new IconList(UI.scale(280, 500)), 0);

		left.last(iconCategoriesList = new GobIconCategoryList(UI.scale(164), 13, elh){
			@Override
			public void change(GobIconCategoryList.GobCategory item) {
				super.change(item);
				SettingsWindow.this.category = item;
				list.needsReorder = true;
			}
		}, 0).change(GobIconCategoryList.GobCategory.ALL);

	    left.last(new HRuler(164), 0);
	    left.last(new CheckBox("Notify new icon discovery") {
		    {this.a = conf.notify;}

		    public void changed(boolean val) {
			conf.notify = val;
			conf.dsave();
		    }
		}, UI.scale(5));

		Widget selectPresetLabel = left.last(new Label("Select Preset:"), UI.scale(0));
		iconPresetsDropbox = new OldDropBox<String>(UI.scale(116), 10, UI.scale(17)) {
			{
				super.change(0);
				selectedPreset = "";
			}
			@Override
			protected String listitem(int i) {
				List<String> keys = new ArrayList<String>(mapIconPresets.keySet());
				if (keys.size() > 0)
					return keys.get(i);
				else return "";
			}
			@Override
			protected int listitems() {
				return mapIconPresets.keySet().size();
			}
			@Override
			protected void drawitem(GOut g, String item, int i) {
				g.aimage(Text.renderstroked(item).tex(), Coord.of(UI.scale(3), g.sz().y / 2), 0.0, 0.5);
			}
			@Override
			public void change(String item) {
				super.change(item);
				selectedPreset = item;
			}
		};

		left.last(new Button(UI.scale(170), "Load Selected Preset", false).action(() -> {
			if (!selectedPreset.equals("")) {
				iconCategoriesList.change(GobIconCategoryList.GobCategory.ALL);
				enabledIcons = new ArrayList<String>(mapIconPresets.get(selectedPreset));
				if (future != null)
					future.cancel(true);
				future = executor.scheduleWithFixedDelay(this::applyPreset, 200, 300, TimeUnit.MILLISECONDS);
				applyPreset();
			} else {
				ui.gui.error("Please select a preset to load!");
			}
		}), UI.scale(10));

		left.last(new Button(UI.scale(170), "Delete Selected Preset", false).action(() -> {
			if (!selectedPreset.equals("")) {
				mapIconPresets.remove(selectedPreset);

				ui.gui.msg(new UI.InfoMessage(selectedPreset + " map icons preset has been deleted!", Color.WHITE, UI.InfoMessage.sfx));
				selectedPreset = "";
				iconPresetsDropbox.change(0);
				savePresetsToFile();
			} else {
				ui.gui.error("Please select a preset to delete!");
			}
		}), UI.scale(10));

		left.last(new Label(""), UI.scale(0));
		Widget newPresetWidget = left.last(new Label("New Preset:"), UI.scale(8));
		newPresetName = new TextEntry(UI.scale(120), ""){
			public boolean keydown(KeyDownEvent e) {
				if(e.awt.getKeyCode() == KeyEvent.VK_ESCAPE) {
					setfocus(SettingsWindow.this.cont);
				}
				return(buf.key(e.awt));
			}
		};
		left.last(new Button(UI.scale(170), "Save New Preset", false).action(() -> {
			if (newPresetName.text().equals(""))
				ui.gui.error("Please set a name for the new map icons preset!");
			else if (newPresetName.text().trim().length() == 0)
				ui.gui.error("Brother don't just use a bunch of spaces as the preset name, that's stupid. Give it a nice name.");
			else if (mapIconPresets.keySet().stream().anyMatch(newPresetName.text()::equals)) {
//					ui.gui.error("A preset named " + "\"" + newPresetName.text() + "\"" + " already exists. Please choose a different name, or delete the old one.");
				SettingsWindow thisSettingsWindow = this;
				if (confirmOverwriteWnd != null){
					confirmOverwriteWnd.remove();
					confirmOverwriteWnd = null;
				}
				confirmOverwriteWnd = new Window(UI.scale(new Coord(235, 90)), "Preset already exists!") {
					{
						Widget prev;
						add(new Label("Are you sure you want to overwrite the preset?"), UI.scale(new Coord(10, 10)));
						prev = add(new Label("Preset Name: "), UI.scale(new Coord(10, 30)));
						add(new Label(newPresetName.text()), prev.pos("ur").adds(10, 0)).setcolor(new Color(255, 205, 0,255));

						Button add = new Button(UI.scale(90), "Overwrite") {
							@Override
							public void click() {
								iconCategoriesList.change(GobIconCategoryList.GobCategory.ALL);

								if (future != null)
									future.cancel(true);
								future = executor.scheduleWithFixedDelay(thisSettingsWindow::savePreset, 200, 300, TimeUnit.MILLISECONDS);

								parent.reqdestroy();
							}
						};
						add(add, UI.scale(new Coord(15, 60)));

						Button cancel = new Button(UI.scale(90), "No! Cancel!") {
							@Override
							public void click() {
								parent.reqdestroy();
							}
						};
						add(cancel, UI.scale(new Coord(130, 60)));
					}

					@Override
					public void wdgmsg(Widget sender, String msg, Object... args) {
						if (msg.equals("close"))
							reqdestroy();
						else
							super.wdgmsg(sender, msg, args);
					}

				};
				ui.gui.add(confirmOverwriteWnd, new Coord((ui.gui.sz.x - confirmOverwriteWnd.sz.x) / 2, (ui.gui.sz.y - confirmOverwriteWnd.sz.y*3) / 2));
				confirmOverwriteWnd.show();
			} else {
				iconCategoriesList.change(GobIconCategoryList.GobCategory.ALL);

				if (future != null)
					future.cancel(true);
				future = executor.scheduleWithFixedDelay(this::savePreset, 200, 300, TimeUnit.MILLISECONDS);
			}
		}), UI.scale(10));



	    cont.pack();
		left.pack();
		left.add(iconPresetsDropbox, selectPresetLabel.pos("ur").adds(3, 2));
		left.add(newPresetName, newPresetWidget.pos("ur").adds(4, -1));
		root.pack();
		updateSelectAllCheckbox();
	}

	private void updateSelectAllCheckbox() {
		if(selectAllCheckBox == null) {
			return;
		}
		List<? extends ListIcon> items = list != null ? list.items() : null;
		selectAllCheckBox.a = items != null
				&& !items.isEmpty()
				&& items.stream().allMatch(icon -> icon.conf.show);
	}

	private void applyPreset(){
		if (!list.needsReorder){
			future.cancel(true);
			list.items().forEach(icon -> {
				if (enabledIcons.stream().anyMatch(icon.name::equals))
					icon.conf.show = true;
				else
					icon.conf.show = false;
				if (Config.mandatoryAlwaysEnabledMapIcons.keySet().stream().anyMatch(icon.name::equals))
					icon.conf.show = true;
			});
			conf.dsave();
			ui.gui.msg(new UI.InfoMessage(selectedPreset + " map icons preset has been set!", Color.WHITE, UI.InfoMessage.sfx));
			if (ui != null && ui.gui != null) {
				ui.sess.glob.oc.gobAction(Gob::updateHidingBoxes);
				ui.gui.map.updatePlobHidingBox();
			}
		}
	}

	private void savePreset(){
		if (!list.needsReorder){
			future.cancel(true);
			String presetName = newPresetName.text();
			mapIconPresets.put(presetName, new ArrayList<String>() {{
				list.items().forEach(icon -> {
					if (icon.conf.show) {
						add(icon.name);
					}
				});
			}});
			ui.gui.msg(new UI.InfoMessage(presetName + " map icons preset has been saved!", Color.WHITE, UI.InfoMessage.sfx));
			newPresetName.settext("");
			savePresetsToFile();
		}
	}

    }

    @OCache.DeltaType(OCache.OD_ICON)
    public static class $icon implements OCache.Delta {
	public void apply(Gob g, OCache.AttrDelta msg) {
	    int resid = msg.uint16();
	    if(resid == 65535) {
		g.delattr(GobIcon.class);
	    } else {
		Indir<Resource> res = OCache.Delta.getres(g, resid);
		int ifl = msg.uint8();
		byte[] sdt = msg.bytes();
		GobIcon cur = g.getattr(GobIcon.class);
		if((cur == null) || (cur.res != res) || !Arrays.equals(cur.sdt, sdt))
		    g.setattr(new GobIcon(g, OCache.Delta.getres(g, resid), sdt));
	    }
	}
    }

	public static void initPresets() {
		load();
	}

	public static void load() {
		mapIconPresets.clear();
		File config = new File(haven.MainFrame.gameDir + "MapIconsPresets/yourSavedPresets");
		if (!config.exists()) {
			defaultPresets();
		} else {
			loadPresetsFromFile(config);
		}
	}
	private static void loadPresetsFromFile(File config) {
		try {
			mapIconPresets.put("", null);
			for (String s : Files.readAllLines(Paths.get(config.toURI()), StandardCharsets.UTF_8)) {
				String[] split = s.split("(;)");
				if (!mapIconPresets.containsKey(split[0])) {
					mapIconPresets.put(split[0], new ArrayList<String>() {{
						for (int x = 1; x < split.length; x++) {
							add(split[x]);
						}
					}});
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void defaultPresets() {
		mapIconPresets.clear();
		loadPresetsFromFile(new File(haven.MainFrame.gameDir + "MapIconsPresets/defaultPresets"));
	}

	public static void savePresetsToFile() {
		try {
			BufferedWriter bw = Files.newBufferedWriter(Paths.get(new File(haven.MainFrame.gameDir + "MapIconsPresets/yourSavedPresets").toURI()), StandardCharsets.UTF_8);
			for (int x = 1; x < mapIconPresets.keySet().size(); x++) { // ND: Start at 1, cause 0 is always an empty string added in the code when settings are loaded
				String presetName = ((String) mapIconPresets.keySet().toArray()[x]);
				StringBuilder enabledIcons = new StringBuilder();
				for (String icon : mapIconPresets.get(presetName)){
					enabledIcons.append(";").append(icon);
				}
				bw.write(presetName + enabledIcons + "\n");
			}
			bw.flush();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
