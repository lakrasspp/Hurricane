package haven;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GobGrowthInfo extends GobInfo {
    private static final int TREE_START = 10;
    private static final int BUSH_START = 30;
    private static final double TREE_MULT = 100.0 / (100.0 - TREE_START);
    private static final double BUSH_MULT = 100.0 / (100.0 - BUSH_START);
    private static final Color BG = new Color(0, 0, 0, 0);
	private static final Map<String, BufferedImage> stageTextCache = new HashMap<>();


	public static BufferedImage getStageImage(int stage, int maxStage) {
		String key = String.valueOf(stage);
		if (!stageTextCache.containsKey(key)) {
			BufferedImage stageImage = renderStageText(key);
			stageTextCache.put(key, stageImage);
		}
		return stageTextCache.get(key);
	}



	private static BufferedImage renderStageText(String stage) {
		return Text.std.renderstroked(stage, new Color(255, 243, 180,255), Color.BLACK).img;
	}


    protected GobGrowthInfo(Gob owner) {
	super(owner);
    }

    @Override
	protected boolean enabled() {
		return OptWnd.displayGrowthInfoCheckBox.a && !gob.isHidden;
	}

    @Override
    protected Tex render() {
	if(gob == null || gob.getres() == null) { return null;}

	BufferedImage growth = growth();

	if(growth == null) {
	    return null;
	}

	return new TexI(ItemInfo.catimgsh(3, 0, BG, growth));
    }
    
    @Override
    public void dispose() {
	super.dispose();
    }

	public final BufferedImage SEEDS_STAGE_DOT = drawDot(new Color(0, 102, 255,255));
	public final BufferedImage FINAL_STAGE_DOT = drawDot(new Color(189, 0, 0,255));

    private BufferedImage growth() {
	Text.Line line = null;
	Resource res = gob.getres();
	if(isSpriteKind(gob, "GrowingPlant", "TrellisPlant") && !(OptWnd.toggleGobHidingCheckBox.a && OptWnd.hideCropsCheckbox.a)) {
	    int maxStage = 0;
	    for (FastMesh.MeshRes layer : gob.getres().layers(FastMesh.MeshRes.class)) {
		if(layer.id / 10 > maxStage) {
		    maxStage = layer.id / 10;
		}
	    }
	    Message data = getDrawableData(gob);
	    if(data != null) {
		int stage = data.uint8();
		if(stage > maxStage) {stage = maxStage;}
		if(res != null && (res.name.contains("carrot"))) {
			if (stage == maxStage - 1) {
				return SEEDS_STAGE_DOT;
			} else if (stage == maxStage) {
				return FINAL_STAGE_DOT;
			} else {
				return getStageImage(stage, maxStage);
			}
		} else if (res != null && (res.name.contains("turnip") || res.name.contains("leek"))){
			if (stage == maxStage - 2) {
				return SEEDS_STAGE_DOT;
			} else if (stage == maxStage) {
				return FINAL_STAGE_DOT;
			} else {
				return getStageImage(stage, maxStage);
			}
		} else {
			if (stage == maxStage){
				return FINAL_STAGE_DOT;
			} else {
				return getStageImage(stage, maxStage);
			}

		}
		}
	} else if(isSpriteKind(gob, "Tree")) {
		boolean isHidden = true;
	    Message data = getDrawableData(gob);
	    if(data != null && !data.eom()) {
		data.skip(1);
		int growth = data.eom() ? -1 : data.uint8();
		if(growth >= 0) {
			if(res.name.contains("gfx/terobjs/trees") && !res.name.endsWith("log") && !res.name.endsWith("oldtrunk") && !(OptWnd.toggleGobHidingCheckBox.a && OptWnd.hideTreesCheckbox.a)) {
			growth = (int) (TREE_MULT * (growth - TREE_START));
			if (growth <= 100)
				isHidden = false;
			int oversizedTreesPercentage = OptWnd.oversizedTreesPercentageTextEntry.text().isEmpty() ? 1 : Integer.parseInt(OptWnd.oversizedTreesPercentageTextEntry.text());
			if (OptWnd.alsoShowOversizedTreesAbovePercentageCheckBox.a && growth >= oversizedTreesPercentage)
				isHidden = false;
		    } else if(res.name.startsWith("gfx/terobjs/bushes") && !(OptWnd.toggleGobHidingCheckBox.a && OptWnd.hideBushesCheckbox.a)) {
			growth = (int) (BUSH_MULT * (growth - BUSH_START));
			isHidden = false;
		    }
			if (!isHidden) {
				Color c = Utils.blendcol(growth / 100.0, Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN);
				line = Text.std.renderstroked(String.format("%d%%", growth), c, Color.BLACK);
			}
		}
	    }
	}

	if(line != null) {
	    return line.img;
	}
	return null;
    }


	private BufferedImage drawDot(Color c) {
		int diameter = 11;
		BufferedImage img = new BufferedImage(diameter, diameter * 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(c);

		g.fillOval(0, 1, diameter, diameter);

		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(1));
		g.drawOval(0, 1, diameter, diameter);

		g.dispose();
		return img;
	}


	private static Message getDrawableData(Gob gob) {
	Drawable dr = gob.getattr(Drawable.class);
	ResDrawable d = (dr instanceof ResDrawable) ? (ResDrawable) dr : null;
	if(d != null)
	    return d.sdt.clone();
	else
	    return null;
    }
    
    private static boolean isSpriteKind(Gob gob, String... kind) {
	List<String> kinds = Arrays.asList(kind);
	boolean result = false;
	Class spc;
	Drawable d = gob.getattr(Drawable.class);
	Resource.CodeEntry ce = gob.getres().layer(Resource.CodeEntry.class);
	if(ce != null) {
	    spc = ce.get("spr");
	    result = spc != null && (kinds.contains(spc.getSimpleName()) || kinds.contains(spc.getSuperclass().getSimpleName()));
	}
	if(!result) {
	    if(d instanceof ResDrawable) {
		Sprite spr = ((ResDrawable) d).spr;
		if(spr == null) {throw new Loading();}
		spc = spr.getClass();
		result = kinds.contains(spc.getSimpleName()) || kinds.contains(spc.getSuperclass().getSimpleName());
	    }
	}
	return result;
    }

    @Override
    public String toString() {
	Resource res = gob.getres();
	return String.format("GobInfo<%s>", res != null ? res.name : "<loading>");
    }
}