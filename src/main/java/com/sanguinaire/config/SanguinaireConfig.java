package com.sanguinaire.config;

import com.sanguinaire.mutation.MutationType;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * {@code config/sanguinaire-common.toml}: statistics of the three original transformations (Goatman, Rotting
 * Abomination, Fade Hound). Forge generates the file with these defaults on first launch; edits are picked up
 * the next time someone transforms (cooldowns: the next time the ability is used). Passives are not configurable.
 */
public final class SanguinaireConfig {
    public static final ForgeConfigSpec SPEC;
    public static final CreatureStats GOATMAN;
    public static final CreatureStats ROTTING_ABOMINATION;
    public static final CreatureStats FADEHOUND;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Statistics of the original transformations. Passive traits cannot be changed here.")
                .push("transformations");
        GOATMAN = new CreatureStats(builder, "goatman", "Goatman", 100, 40, 0, 20);
        ROTTING_ABOMINATION = new CreatureStats(builder, "rotting_abomination", "Rotting Abomination", 300, 80, 0, 10);
        FADEHOUND = new CreatureStats(builder, "fadehound", "Fade Hound", 17, 10, 0, 4);
        builder.pop();
        SPEC = builder.build();
    }

    private SanguinaireConfig() {
    }

    /** The configured statistics for a transformation, or {@code null} if it is not configurable. */
    public static CreatureStats of(MutationType type) {
        return switch (type) {
            case GOATMAN -> GOATMAN;
            case ROTTING_ABOMINATION -> ROTTING_ABOMINATION;
            case FADEHOUND -> FADEHOUND;
            default -> null;
        };
    }

    public static final class CreatureStats {
        public final ForgeConfigSpec.DoubleValue health;
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.DoubleValue armor;
        public final ForgeConfigSpec.IntValue abilityCooldownSeconds;

        CreatureStats(ForgeConfigSpec.Builder builder, String key, String name, double health, double damage, double armor,
                      int cooldownSeconds) {
            builder.comment(name).push(key);
            this.health = builder.comment("Maximum health in half hearts (20 = a normal player's 10 hearts).")
                    .defineInRange("health", health, 1.0D, 1024.0D);
            this.damage = builder.comment("Damage of an empty-handed hit, in half hearts.")
                    .defineInRange("damage", damage, 0.0D, 2048.0D);
            this.armor = builder.comment("Armor points (each is half an armor icon; vanilla caps armor at 30).")
                    .defineInRange("armor", armor, 0.0D, 30.0D);
            this.abilityCooldownSeconds = builder.comment("Seconds before the active ability can be used again.")
                    .defineInRange("abilityCooldownSeconds", cooldownSeconds, 0, 3600);
            builder.pop();
        }

        public int abilityCooldownTicks() {
            return abilityCooldownSeconds.get() * 20;
        }
    }
}
