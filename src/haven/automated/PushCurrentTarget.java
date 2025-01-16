package haven.automated;

import haven.GameUI;
import haven.Gob;

public class PushCurrentTarget implements Runnable {
    private final GameUI gui;

    public PushCurrentTarget(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        Gob currEnemy = getEnemy();
        if(currEnemy != null && currEnemy.getres().name.contains("gfx/borka")) {
            AUtils.pushGob(gui, currEnemy);
        }
        return;
    }

    private Gob getEnemy() {
        if (gui.fv.current != null) {
            long id = gui.fv.current.gobid;
            synchronized (gui.map.glob.oc) {
                for (Gob gob : gui.map.glob.oc) {
                    if (gob.id == id) {
                        return gob;
                    }
                }
            }
        }
        return null;
    }
}
