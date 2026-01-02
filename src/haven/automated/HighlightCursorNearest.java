package haven.automated;


import haven.*;

import java.util.HashMap;
import java.util.Map;

public class HighlightCursorNearest implements Runnable {
    private final GameUI gui;

    public HighlightCursorNearest(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        highlightClosestAttackable();

    }

    private void highlightClosestAttackable() {
        Map<String, ChatUI.MultiChat> chats = gui.chat.getMultiChannels();
        gui.map.new Hittest(gui.map.currentCursorLocation) {
            protected void hit(Coord pc, Coord2d mc, ClickData inf) {
                Gob player = gui.map.player();
                if (player == null)
                    return;
                if (inf != null) {
                    Long gobid = new Long((Integer) inf.clickargs()[1]);
                    Gob clickedGob = gui.map.glob.oc.getgob(gobid);
                    if (clickedGob != null) {
                        if (isPlayer(clickedGob)) {
                            if (!clickedGob.isFriend()) {
                                if (chats.get("Party") != null)
                                    chats.get("Party").send("@" + clickedGob.id);
                            }
                            return;
                        }
                        if (clickedGob.getres().name.equals("gfx/borka/body"))
                            if (chats.get("Party") != null)
                                chats.get("Party").send("@" + clickedGob.id);
                    }
                }


                HashMap<Long, Gob> allAttackableMap = getAllAttackableMap(gui);
                // try and find the closest animal or player to attack
                Gob closestEnemy = null;
                OUTER_LOOP:
                for (Gob gob : allAttackableMap.values()) {
                    if (isPlayer(gob) && gob.isFriend()) {
                        continue;
                    }
                    if (gob.getres().name.equals("gfx/kritter/horse/horse") && gob.occupants.size() > 0) { // ND: Wild horse special case. Tamed horses are never attacked anyway
                        for (Gob occupant : gob.occupants) {
                            if (occupant.isFriend() || occupant.isItMe()) {
                                continue OUTER_LOOP;
                            }
                        }
                    }
                    //if gob is an enemy player and not already aggroed
                    if ((closestEnemy == null || gob.rc.dist(mc) < closestEnemy.rc.dist(mc))
                            && (gob.knocked == null || (gob.knocked != null && !gob.knocked))) { // ND: Retarded workaround that I need to add, just like in Gob.java
                        closestEnemy = gob;
                    }
                }

                if (closestEnemy != null) {
                    if (chats.get("Party") != null)
                        chats.get("Party").send("@" + closestEnemy.id);
                }

            }
        }.run();
    }

    private static boolean isPlayer(Gob gob){
        return gob.getres() != null && gob.getres().name != null && gob.getres().name.equals("gfx/borka/body");
    }

    private static HashMap<Long, Gob> getAllAttackableMap(GameUI gui) {
        HashMap<Long, Gob> gobs = new HashMap<>();
        if (gui.map.plgob == -1) {
            return gobs;
        }
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                if (gob.getres() != null && gob.getres().name != null){
                    if (gob.id != gui.map.plgob) {
                        if (isPlayer(gob) || gob.getres().name.equals("gfx/kritter/horse/horse"))
                            gobs.put(gob.id, gob);
                    }
                }
            }
        }
        return gobs;
    }
}
