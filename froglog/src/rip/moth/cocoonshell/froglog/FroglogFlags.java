package rip.moth.cocoonshell.froglog;

/**
 * Build-time switches. {@link #SKIP_SETUP} is true only on test APKs so the
 * home screen can be reached on BlueStacks without the onboarding wizard.
 * Android 13 production builds leave it false.
 */
public final class FroglogFlags {
    public static final boolean SKIP_SETUP = false;

    private FroglogFlags() {}
}
