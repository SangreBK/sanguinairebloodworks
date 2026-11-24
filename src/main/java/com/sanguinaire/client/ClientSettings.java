package com.sanguinaire.client;

/** The world's {@code /sanguinaire} rules as last sent by the server (see {@code SanguinaireSettings}). */
public final class ClientSettings {
    private static boolean firstPersonAllowed;
    private static boolean transformationItemsAllowed;

    private ClientSettings() {
    }

    public static boolean isFirstPersonAllowed() {
        return firstPersonAllowed;
    }

    public static boolean areTransformationItemsAllowed() {
        return transformationItemsAllowed;
    }

    static void set(boolean firstPerson, boolean items) {
        firstPersonAllowed = firstPerson;
        transformationItemsAllowed = items;
    }

    /** Back to the defaults when leaving a world. */
    static void reset() {
        set(false, false);
    }
}
