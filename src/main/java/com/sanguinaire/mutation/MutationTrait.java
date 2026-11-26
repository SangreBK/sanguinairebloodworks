package com.sanguinaire.mutation;

/**
 * Passive behaviours a transformation can have. Each constant is implemented in
 * {@link TransformationManager} (per-tick and start/end effects) or {@link TransformationEvents} (event hooks).
 */
public enum MutationTrait {
    /** Hostile mobs ({@code Enemy}) never target you. */
    COUNTS_AS_MONSTER,
    /** Ghasts, piglins and zombified piglins never target you. */
    IGNORED_BY_NETHER_MOBS,
    /** Guardians and Elder Guardians never target you. */
    IGNORED_BY_GUARDIANS,
    /** Iron Golems and villagers never target you. */
    IGNORED_BY_VILLAGE_DEFENDERS,
    /** Skeletons and sheep run away from you. */
    FEARED_BY_SKELETONS_AND_SHEEP,
    /** Normal melee attacks are impossible. */
    CANNOT_ATTACK,
    /** Automatically step up full blocks. */
    STEP_UP,
    /** A monstrous form: villagers flee in terror and golems attack on sight. */
    MONSTROUS,
    /** Wardens never anger at, target or darken you. */
    IGNORED_BY_WARDENS,
    /** Illagers, witches and ravagers (every raider) treat the player as one of their own. */
    IGNORED_BY_ILLAGERS,
    /** Fall damage is halved. */
    HALF_FALL_DAMAGE,
    /** You never take fall damage. */
    FALL_DAMAGE_IMMUNE,
    /** Killing something restores 4 hunger points. */
    HUNGER_ON_KILL,
    /** Blocking your hits with a shield disables that shield for 10 seconds. */
    DISABLES_SHIELDS,
    /** Every other creature within 8 blocks has Nausea I and Hunger I for as long as it stays in range. */
    SICKENING_AURA,
    /** Projectiles bounce off you and deal no damage. */
    PROJECTILE_IMMUNE,
    /** You cannot jump. */
    CANNOT_JUMP,
    /** Immune to Poison. */
    POISON_IMMUNE,
    /** Immune to Slowness. */
    SLOWNESS_IMMUNE,
    /** Taking damage turns you invisible for 4 seconds and blinds the attacker for 8 seconds. */
    FADES_WHEN_HIT,
    /** Your melee damage ignores the target's armor. */
    ARMOR_PIERCING,
    /** Jump one block higher (two blocks in all). */
    HIGH_JUMP,
    /** Melee reach +1.5 blocks. */
    LONG_REACH,
    /** Walking is as fast as a player's sprint, and sprinting faster still (+30% movement speed). */
    SWIFT,
    /** At night you are faster and gain Jump Boost. */
    NIGHT_HUNTER,
    /** You burn in sunlight, like undead mobs. */
    BURNS_IN_SUNLIGHT,
    /** You can breathe underwater indefinitely. */
    WATER_BREATHING,
    /** Too heavy to swim: sinks in water and can't rise or float (Rotting Abomination). */
    SINKS,
    /** Immune to fire and lava. */
    FIRE_IMMUNE,
    /** Your hits inflict Hunger II for 3 seconds. */
    HUNGER_ON_HIT,
    /** Your hits inflict Poison I for 8 seconds. */
    POISON_ON_HIT,
    /** Villagers you kill become Zombie Villagers. */
    ZOMBIFIES_VILLAGERS,
    /** Water and rain damage you. */
    HURT_BY_WATER,
    /** You take damage when outside water. */
    HURT_OUTSIDE_WATER,
    /** Nearby hostile mobs periodically receive Mining Fatigue III. */
    FATIGUE_AURA,
    /** You don't trigger vibrations (Sculk Sensors, Wardens, Shriekers). */
    NO_VIBRATIONS,
    /** You can fly freely. */
    FLIGHT,
    /** You climb walls you walk into, like a spider. */
    WALL_CLIMBING,
    /** Cobwebs do not slow you down. */
    COBWEB_WALKING,
    /** You swim much faster (Guardian / Elder Guardian speed). */
    SWIFT_SWIMMER,
    /** Creatures touching you are thrown back and take 2 damage (Ender Dragon). */
    DRAGON_REPULSION,
    /** The nearest End Crystal within 32 blocks heals you, as it does the Ender Dragon. */
    CRYSTAL_HEALING
}
