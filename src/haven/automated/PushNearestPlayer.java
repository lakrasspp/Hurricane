package haven.automated;

import haven.GameUI;
import haven.Gob;

import java.util.HashMap;

public class PushNearestPlayer implements Runnable {
    private final GameUI gui;

    public PushNearestPlayer(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        pushClosestAttackablePlayer();
        return;
    }

    private void pushClosestAttackablePlayer() {
        Gob player = gui.map.player();
        if (player == null)
            return;
        HashMap<Long, Gob> allAttackableMap = AUtils.getAllAttackablePlayersMap(gui);

        Gob closestEnemy = null;
        for (Gob gob : allAttackableMap.values()) {
            if (isPlayer(gob) && gob.isFriend()) {
                continue;
            }
            //if gob is an enemy player and not already aggroed
            if ((closestEnemy == null || gob.rc.dist(player.rc) < closestEnemy.rc.dist(player.rc))
                    && (gob.knocked == null || (gob.knocked != null && !gob.knocked))) {
                closestEnemy = gob;
            }
        }

        if (closestEnemy != null) {
            AUtils.pushGob(gui, closestEnemy);
            return;
        }
    }

    private boolean isPlayer(Gob gob){
        return gob.getres() != null && gob.getres().name != null && gob.getres().name.equals("gfx/borka/body");
    }
}
