package omok3d;

/**
 * IDE(VSCode 등)나 fat-jar 실행용 진입점.
 * Application을 상속한 클래스를 직접 실행하면 "JavaFX runtime components are missing"
 * 오류가 날 수 있어서, 상속하지 않는 별도 main을 둔다.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        App.main(args);
    }
}
