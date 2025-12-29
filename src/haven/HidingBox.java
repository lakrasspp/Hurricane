package haven;

import haven.render.*;
import haven.res.lib.tree.TreeScale;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class HidingBox extends SlottedNode implements Rendered {
	public boolean filled;
	private static final VertexArray.Layout LAYOUT =
			new VertexArray.Layout(new VertexArray.Layout.Input(
					Homo3D.vertex,
					new VectorFormat(3, NumberFormat.FLOAT32),
					0, 0, 12));
	private Model model;
	private final Gob gob;
	private Pipe.Op state = null;

	private final Pipe.Op filledOp;
	private final Pipe.Op hollowOp;

	private final Color color;

	private static final Map<String, Model> MODEL_CACHE = new HashMap<>();
	private static final float Z = 0.1f;
	public static final float WIDTH = 2f;

	public static final Pipe.Op TOP = Pipe.Op.compose(
			Rendered.last,
			States.Depthtest.none,
			States.maskdepth);

	private HidingBox(Gob gob, boolean filled, Color color) {
		this.gob = gob;
		this.filled = filled;
		this.color = color;
		this.filledOp = Pipe.Op.compose(
				new BaseColor(color),
				new States.Facecull(States.Facecull.Mode.NONE),
				new States.DepthBias(-1, -110),
				Rendered.last);
		this.hollowOp = Pipe.Op.compose(
				new BaseColor(new Color(color.getRed(),
						color.getGreen(),
						color.getBlue(),
						153)),
				new States.LineWidth(WIDTH),
				TOP);
		this.state = filled ? filledOp : hollowOp;
		this.model = getModel(gob, filled);
	}

	public static HidingBox forGob(Gob gob, boolean filled, Color color) {
		try {
			return new HidingBox(gob, filled, color);
		} catch (Loading ignored) { }
		return null;
	}

	@Override
	public void added(RenderTree.Slot slot) {
		super.added(slot);
		slot.ostate(state);
		updateState();
	}

	@Override
	public void draw(Pipe context, Render out) {
		if(model != null) {
			out.draw(context, model);
		}
	}

	public void updateState() {
		if (this.state != null)
			this.state = filled ? filledOp : hollowOp;
		if (model != null && slots != null) {
			try {
				Model m = getModel(gob, filled);
				if (m != null && m != model) {
					model = m;
					for (RenderTree.Slot s : slots)
						s.update();
				}
			} catch (Loading ignored) {}
			for (RenderTree.Slot slot : slots)
				slot.ostate(state);
		}
	}

	private static Model getModel(Gob gob, boolean filled) {
		Model model = null;
		Resource res = getResource(gob);
		String resName = res.name;
		TreeScale treeScale = null;
		float boxScale = 1.0f;
		boolean growingTreeOrBush = false;
		if ((resName.startsWith("gfx/terobjs/trees") &&
				!resName.endsWith("log") &&
				!resName.endsWith("oldtrunk"))
				|| resName.startsWith("gfx/terobjs/bushes")) {
			treeScale = gob.getattr(TreeScale.class);
			if (treeScale != null) {
				if (treeScale.scale < 1f || treeScale.scale > 1f) { // ND: Don't care about the original scale, cause the collision always assumes it's a fully grown tree
					boxScale = 1.0f / treeScale.scale;
					growingTreeOrBush = true;
				}
			}
		}
		synchronized (MODEL_CACHE) {
			if (!growingTreeOrBush)
				model = MODEL_CACHE.get(resName + (filled ? "(filled)" : "(hollow)"));
			if (model == null) {
				List<List<Coord3f>> polygons = new LinkedList<>();
				float scaleFinal = boxScale;
				Collection<Resource.Neg> negs = res.layers(Resource.Neg.class);
				if (negs != null) { // ND: This happens for stuff like stockpiles, so we manually draw them I guess
					for (Resource.Neg neg : negs) {
						List<Coord3f> box = new LinkedList<>();
						box.add(new Coord3f(neg.ac.x * scaleFinal, -neg.ac.y * scaleFinal, Z));
						box.add(new Coord3f(neg.bc.x * scaleFinal, -neg.ac.y * scaleFinal, Z));
						box.add(new Coord3f(neg.bc.x * scaleFinal, -neg.bc.y * scaleFinal, Z));
						box.add(new Coord3f(neg.ac.x * scaleFinal, -neg.bc.y * scaleFinal, Z));
						polygons.add(box);
					}
				}

				Collection<Resource.Obstacle> obstacles = res.layers(Resource.Obstacle.class);
				if (obstacles != null) {
					for (Resource.Obstacle ob : obstacles) {
						if ("build".equals(ob.id))
							continue;
						for (Coord2d[] poly : ob.p) {
							polygons.add(Arrays.stream(poly)
									.map(c -> new Coord3f(
											(float) c.x * scaleFinal,
											(float) -c.y * scaleFinal,
											Z))
									.collect(Collectors.toList()));
						}
					}
				}
				if (!polygons.isEmpty()) {
					List<Float> vertices = new LinkedList<>();

					for (List<Coord3f> poly : polygons)
						addLoopedVertices(vertices, poly, filled);
					float[] data = toFloatArray(vertices);

					VertexArray.Buffer vbo = new VertexArray.Buffer(
							data.length * 4,
							DataBuffer.Usage.STATIC,
							DataBuffer.Filler.of(data));

					VertexArray va = new VertexArray(LAYOUT, vbo);

					model = new Model(
							filled ? Model.Mode.TRIANGLES : Model.Mode.LINES,
							va,
							null);
					if (!growingTreeOrBush)
						MODEL_CACHE.put(resName + (filled ? "(filled)" : "(hollow)"), model);
				}
			}
		}
		return model;
	}

	private static float[] toFloatArray(List<Float> list) {
		float[] ret = new float[list.size()];
		int i = 0;
		for (Float f : list)
			ret[i++] = f;
		return ret;
	}

	private static void addLoopedVertices(List<Float> out, List<Coord3f> v, boolean filled) {
		//triangulation magic
		int n = v.size();
		if (!filled) {
			for (int i = 0; i < n; i++) {
				Coord3f a = v.get(i);
				Coord3f b = v.get((i + 1) % n);
				Collections.addAll(out, a.x, a.y, a.z);
				Collections.addAll(out, b.x, b.y, b.z);
			}
			return;
		}

		Coord3f origin = v.get(0);
		for (int i = 1; i < n - 1; i++) {
			Coord3f a = v.get(i);
			Coord3f b = v.get(i + 1);

			Collections.addAll(out, origin.x, origin.y, origin.z);
			Collections.addAll(out, a.x, a.y, a.z);
			Collections.addAll(out, b.x, b.y, b.z);
		}
	}

	private static Resource getResource(Gob gob) {
		Resource res = gob.getres();
		if (res == null)
			throw new Loading();
		Collection<RenderLink.Res> links = res.layers(RenderLink.Res.class);
		for (RenderLink.Res link : links) {
			if (link.l instanceof RenderLink.MeshMat) {
				return ((RenderLink.MeshMat) link.l).mesh.get();
			}
		}
		return res;
	}
}
