package net.neon.hordes.compat;

import net.kyori.adventure.util.TriState;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Mob;

/**
 * Isolates Paper 26.3-specific APIs so future API adjustments stay in one place.
 */
public final class Paper26Compat {

    private Paper26Compat() {}

    /**
     * Toggles world daylight cycle using Paper 26.3 GameRules.ADVANCE_TIME
     * (verified: https://jd.papermc.io/paper/26.3/org/bukkit/GameRules.html).
     */
    public static void setDaylightCycle(World world, boolean advance) {
        world.setGameRule(GameRules.ADVANCE_TIME, advance);
    }

    public static boolean isDaylightCycleEnabled(World world) {
        Boolean val = world.getGameRuleValue(GameRules.ADVANCE_TIME);
        return val == null || val;
    }

    /**
     * Controls whether a Mob despawns in Peaceful difficulty
     * (verified: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Mob.html).
     */
    public static void setDespawnInPeaceful(Mob mob, boolean allowDespawn) {
        mob.setDespawnInPeacefulOverride(allowDespawn ? TriState.TRUE : TriState.FALSE);
    }
}
