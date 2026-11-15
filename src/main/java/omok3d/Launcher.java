package omok3d;

/**
 * Entry point for running from an IDE (e.g. VSCode) or a fat jar.
 * Running a class that extends Application directly can fail with
 * "JavaFX runtime components are missing", so this separate main is used instead.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        App.main(args);
    }
}
