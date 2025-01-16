package hurricane.nords;

import haven.Coord;
import haven.WItem;

public interface DTarget2 {
    boolean drop(WItem target, Coord cc, Coord ul);

    boolean iteminteract(WItem target, Coord cc, Coord ul);
}
