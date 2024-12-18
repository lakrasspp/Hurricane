package haven.automated;


import haven.*;

import static haven.OCache.posres;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.lang.Math;

public class WagonNearestLiftable implements Runnable {
    private GameUI gui;

    // Maximum distance to search for a gob
    private final double MAX_DISTANCE = 12 * 5;

    // Liftables that need to be checked if they are knocked (dead)
    // You don't want try to lift an alive bear..
    private final HashSet<String> LIFTABLES_KNOCKED = new HashSet<String>(Arrays.asList(
        "gfx/kritter/badger/badger",
        "gfx/kritter/cattle/cattle",
        "gfx/kritter/badger/badger",
        "gfx/kritter/bear/bear",
        "gfx/kritter/beaver/beaver",
        "gfx/kritter/boar/boar",
        "gfx/kritter/caveangler/caveangler",
        "gfx/kritter/cavelouse/cavelouse",
        "gfx/kritter/chasmconch/chasmconch",
        "gfx/kritter/eagleowl/eagleowl",
        "gfx/kritter/fox/fox",
        "gfx/kritter/goat/wildgoat",
        "gfx/kritter/goldeneagle/goldeneagle",
        "gfx/kritter/greyseal/greyseal",
        "gfx/kritter/horse/horse",
        "gfx/kritter/lynx/lynx",
        "gfx/kritter/moose/moose",
        "gfx/kritter/sheep/sheep",
        "gfx/kritter/ooze/greenooze",
        "gfx/kritter/otter/otter",
        "gfx/kritter/pelican/pelican",
        "gfx/kritter/reddeer/reddeer",
        "gfx/kritter/reindeer/reindeer",
        "gfx/kritter/roedeer/roedeer",
        "gfx/kritter/stoat/stoat",
        "gfx/kritter/swan/swan",
        "gfx/kritter/troll/troll",
        "gfx/kritter/walrus/walrus",
        "gfx/kritter/wolf/wolf",
        "gfx/kritter/wolverine/wolverine",
        "gfx/kritter/woodgrouse/woodgrouse-m"
    ));

    // Liftables that dont need any additional checks
    // Logs are handled differently and are already included
    private final HashSet<String> LIFTABLES_GENERIC = new HashSet<String>(Arrays.asList(
        "gfx/terobjs/crate",
        "gfx/terobjs/chest",
        "gfx/terobjs/largechest",
        "gfx/terobjs/map/stonekist",
        "gfx/terobjs/map/jotunclam",
        "gfx/terobjs/barrel"  
    ));

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

    /**
    * Tries to exit a wagon at an angle to get around a blocked exit point
    * Should be followed up by an other move command
    *
    * @param  target      target location to exit to
    * @param  degree      new exit angle in degree
    * @return             true when exit failed
    */
    private boolean TryExitWagonAtAngle(Coord2d target, double degree) throws InterruptedException{
        Gob player = gui.map.player();

        Set<String> poses = player.getPoses();
        String startPose = null;

        // Determine which passenger slot is occuppied by the player
        // To wait for the specifc pose later
        if(poses.contains("wagondrivan")){
            startPose = "wagondrivan";
        }else if(poses.contains("wagonsittan")){
            startPose = "wagonsittan";
        }else{
            throw new InterruptedException("Exiting Vehicle failed, unknown pose: (" + String.join(" / ", poses) + ")");
        }

        double angle = player.rc.angle(target) + Math.toRadians(degree);
        double distFromPlayer = target.dist(player.rc);

        Coord2d exitCoords = player.rc.add(Math.cos(angle) * distFromPlayer, Math.sin(angle) * distFromPlayer);
        gui.map.wdgmsg ( "click", Coord.z, exitCoords.floor ( posres ), 1, UI.MOD_CTRL, 0);

        return waitPose(player, startPose, false, 25, Math.max(150, GameUI.getPingValue()+10));
    }

    private enum VehicleType {
        WAGON,
        CART,
        UNKNOWN
    }

    /**
    * Converts a res into a VehicleType
    *
    * @param  res      Resource trying to convert
    * @return          The vehicleType of the res, every unsupported vehicle is unknown.
    */
    private VehicleType ResToVehicleType(Resource res){
        switch (res.name) {
            case "gfx/terobjs/vehicle/wagon":
                return VehicleType.WAGON;
            case "gfx/terobjs/vehicle/cart":
                return VehicleType.CART;
            default:
                return VehicleType.UNKNOWN;
        }
    }

    public WagonNearestLiftable(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        try {
            Gob vehicle = null;
            Gob target = null;
            boolean isOnWagon = false;
            
            Gob player = gui.map.player();
            if (player == null)
                return;

            Coord3f raw = player.placed.getc();
            boolean isOnVehicle = (raw == null);
            
            boolean doLiftAnimal = Utils.getprefb("wagonNearestLiftable_animalcarcass", true);
            boolean doLiftContainer = Utils.getprefb("wagonNearestLiftable_container", true);
            boolean doLiftLog = Utils.getprefb("wagonNearestLiftable_log", true);

            for (Gob gob : Utils.getAllGobs(gui)) {
                Resource res = null;
                try {
                    res = gob.getres();
                } catch (Loading l) {
                }
                if (res != null) {
                    double distFromPlayer = gob.rc.dist(player.rc);
                    VehicleType vehicleType = ResToVehicleType(res);
                    
                    if (vehicleType == VehicleType.WAGON ||
                        vehicleType == VehicleType.CART)
                    {
                        if (distFromPlayer <= MAX_DISTANCE && 
                            (vehicle == null || distFromPlayer < vehicle.rc.dist(player.rc))) 
                        {
                            if(vehicleType == VehicleType.WAGON && isOnVehicle && distFromPlayer <= 3){
                                isOnWagon = true;
                            }
                            vehicle = gob;
                        }
                        continue;
                    }

                    if(distFromPlayer > MAX_DISTANCE){
                        continue;
                    }

                    if((doLiftAnimal && LIFTABLES_KNOCKED.contains(res.name) && gob.getPoses().contains("knock")) ||
                        (doLiftContainer && LIFTABLES_GENERIC.contains(res.name)) ||
                        (doLiftLog && res.name.startsWith("gfx/terobjs/trees/") && res.name.endsWith("log")))
                    {
                        if((target == null || distFromPlayer < target.rc.dist(player.rc))){
                            target = gob;
                        }  
                    }
                    
                }
            }

            if(isOnVehicle && !isOnWagon){
                return;
            }

            if(vehicle == null){
                throw new InterruptedException("No valid vehicle found.");
            }
            
            if (target == null){
                throw new InterruptedException("No liftable found.");
            }

            //Exit Wagon if on any
            if(isOnWagon &&
                TryExitWagonAtAngle(target.rc, 0) && 
                TryExitWagonAtAngle(target.rc, 45) && 
                TryExitWagonAtAngle(target.rc, -45))
            {
                throw new InterruptedException("Exiting Wagon failed, path is blocked or movement cursor is active.");
            }
            
            //Lift the object
            gui.wdgmsg("act", "carry");
            gui.map.wdgmsg("click", Coord.z, target.rc.floor(posres), 1, 0, 0, (int) target.id, target.rc.floor(posres), 0, -1);
            if(waitPose(player, "banzai", true, 30, 3000)){
                throw new InterruptedException("Lifting object took to long");
            }

            //Store in vehicle
            gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
            if(waitPose(player, "banzai", false, 30, 6000)){
                throw new InterruptedException("Storing in vehicle took to long");
            }

            //Enter Wagon if you started on the wagon
            if(isOnWagon){
                FlowerMenu.setNextSelection("Ride");
                gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
            }

            return;
        
        } catch (InterruptedException e) {
            gui.error(e.getMessage());
        }

        if (gui.wagonNearestLiftableThread != null) {
            gui.wagonNearestLiftableThread.interrupt();
            gui.wagonNearestLiftableThread = null;
        }
    }
}
