package haven.automated;

import haven.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static haven.OCache.posres;

public class Traverse implements Runnable {
    public class DoorShiftData {
        public String gobName;
        public ArrayList<Doors> doorList;
        public DoorShiftData(String gn, ArrayList<Doors> dl) {
            gobName = gn;
            doorList = dl;
        }
    }
    public class Doors {
        public Coord2d meshRC;
        public int meshID;
        public Doors(Coord2d c, int id) {
            meshRC = c;
            meshID = id;
        }
    }
    public class Target {
        public Coord2d c;
        public Coord s;
        public long g;
        public int m;
        public Target(Coord2d ic, Coord is, long ig, int im) {
            c = ic;
            s = is;
            g = ig;
            m = im;
        }
    }
    private final GameUI gui;
    private final ArrayList<String> gobNameSuffix = new ArrayList<String>(Arrays.asList(
            "-door",
            "upstairs",
            "downstairs",
            "cellarstairs",
            "cavein",
            "cavein2",
            "caveout"
    ));
    public final ArrayList<DoorShiftData> buildings = new ArrayList<DoorShiftData>(Arrays.asList(
            new DoorShiftData("gfx/terobjs/arch/logcabin", new ArrayList<Doors>(Collections.singletonList(
                    new Doors(new Coord2d(22, 0), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/stonehut", new ArrayList<Doors>(Collections.singletonList(
                    new Doors(new Coord2d(22, 0), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/timberhouse", new ArrayList<Doors>(Collections.singletonList(
                    new Doors(new Coord2d(33, 0), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/stonestead", new ArrayList<Doors>(Collections.singletonList(
                    new Doors(new Coord2d(44, 0), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/stonemansion", new ArrayList<Doors>(Collections.singletonList(
                    new Doors(new Coord2d(48, 0), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/greathall", new ArrayList<Doors>(Arrays.asList(
                    new Doors(new Coord2d(77, -28), 18),
                    new Doors(new Coord2d(77, 0), 17),
                    new Doors(new Coord2d(77, 28), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/stonetower", new ArrayList<Doors>(Collections.singletonList(
                    new Doors(new Coord2d(36, 0), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/windmill", new ArrayList<Doors>(Collections.singletonList(
                    new Doors(new Coord2d(0, 28), 16)
            ))),
            new DoorShiftData("gfx/terobjs/arch/greathall-door", new ArrayList<Doors>(Arrays.asList(
                    new Doors(new Coord2d(0, -30), 18),
                    new Doors(new Coord2d(0, 0), 17),
                    new Doors(new Coord2d(0, 30), 16)
            )))
    ));
    public Traverse(GameUI gui) {
        this.gui = gui;
    }
    @Override
    public void run() {
        if (gui == null) return;
        if (gui.map == null) return;
        if (gui.map.player() == null) return;
        Coord2d plc = gui.map.player().rc;
        Target targetDoor = getTarget(gui, buildings);
        Gob targetGob = getGob(gui, gobNameSuffix);
        if ((targetDoor == null) && (targetGob == null)) {
            return;
        }
        if (targetGob != null) {
            if ((targetDoor == null) || (targetGob.rc.dist(plc) < targetDoor.c.dist(plc))) { //if no door is found or another gob is closer
                targetDoor = new Target(targetGob.rc, new Coord(0, 0), targetGob.id, -1);
            }
        }
        try {
            gui.map.wdgmsg("click", targetDoor.s, targetDoor.c.floor(posres), 3, 0, 0, (int) targetDoor.g, targetDoor.c.floor(posres), 0, targetDoor.m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private Target getTarget(GameUI gui, ArrayList<DoorShiftData> b) {
        Coord2d plc = gui.map.player().rc;
        Target result = null;
        ArrayList<Target> targetList = new ArrayList<Target>();
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                try {
                    Resource res = gob.getres();
                    if (res == null) continue;
                    if (!res.name.startsWith("gfx/terobjs/arch/")) continue;
                    for (DoorShiftData bld : b) {
                        if (bld.gobName.equals(res.name)) {
                            for (Doors drs : bld.doorList) {
                                targetList.add(new Target(
                                        gob.rc.add(drs.meshRC.rotate(gob.a)),
                                        new Coord(0, 0),
                                        gob.id,
                                        drs.meshID
                                ));
                            }
                        }
                    }
                } catch (Loading l) {
                    l.printStackTrace();
                }
            }
        }
        for (Target t : targetList) {
            try {
                if ((result == null) || (t.c.dist(plc) < result.c.dist(plc)))
                    result = t;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }
    private Gob getGob(GameUI gui, ArrayList<String> gobNameAL) {
        Coord2d plc = gui.map.player().rc;
        Gob result = null;
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                try {
                    if (gob.getres() == null) continue;
                    boolean skipGob = true;
                    for (String n : gobNameAL) {
                        if ((gob.getres().name.endsWith(n)) && (!gob.getres().name.endsWith("gfx/terobjs/arch/greathall-door"))) {
                            skipGob = false;
                        }
                    }
                    if (skipGob) continue;
                    if ((result == null || gob.rc.dist(plc) < result.rc.dist(plc)) && gob.rc.dist(plc) < 440.0f) {
                        result = gob;
                    }
                } catch (Loading l) {
                    l.printStackTrace();
                }
            }
        }
        return result;
    }
}