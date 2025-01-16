package haven.automated;

import haven.*;

import static java.lang.Thread.sleep;

import static haven.OCache.posres;

public class steakMaker implements Runnable {
    private GameUI gui;
    private boolean stop;
    private final double MAX_DISTANCE = 12 * 10;

    /**
     * Wait for a pose change on the gob.
     *
     * @param  gob         gob to inspect
     * @param  pose        pose string to compare
     * @param  changedTo   false = pose changed from, true = pose changed to
     * @param  delay       the amount the value should be incremented by
     * @param  timeout     timeout when no change was detected
     * @return             true when the pose hasn't changed
     */
    private boolean waitPose(Gob gob, String pose, boolean changedTo, int delay, int timeout) throws InterruptedException{
        int counter = 0;
        while(gob.getPoses().contains(pose) ^ changedTo){
            if(counter >= timeout){
                return true;
            }
            counter += delay;
            Thread.sleep(delay);
        }
        return false;
    }

    public steakMaker(GameUI gui) {
        this.gui = gui;
        stop = false;
    }

    @Override
    public void run() {
        try {
            while (!stop) {
                Gob target = null;

                Gob player = gui.map.player();
                if (player == null)
                    return;

                Resource res = null;
                for (Gob gob : Utils.getAllGobs(gui)) {
                    try {
                        res = gob.getres();
                    } catch (Loading l) {
                    }
                    if (res != null) {
                        double distFromPlayer = gob.rc.dist(player.rc);

                        if (distFromPlayer > MAX_DISTANCE) {
                            continue;
                        }

                        if (res.name.equals("gfx/borka/body") && gob.isDeadPlayer) {
                            if (target == null || gob.rc.dist(player.rc) < target.rc.dist(player.rc)) {
                                target = gob;
                            }
                        }

                    }
                }

                if (target == null) {
                    throw new InterruptedException("No liftable found.");
                }

                //Lift the object
                gui.wdgmsg("act", "carry");
                gui.map.wdgmsg("click", Coord.z, target.rc.floor(posres), 1, 0, 0, (int) target.id, target.rc.floor(posres), 0, -1);
                if (waitPose(player, "banzai", true, 1, 3000)) {
                    throw new InterruptedException("Lifting object took to long");
                }

                //Place the object
                gui.map.wdgmsg("click", Coord.z, player.rc.add(0, 5.5).floor(posres), 3, 0);
                if (waitPose(player, "banzai", false, 1, 6000)) {
                    throw new InterruptedException("Storing in vehicle took to long");
                }

                sleep(10);
            }
        } catch (InterruptedException e) {
        }
    }
}
