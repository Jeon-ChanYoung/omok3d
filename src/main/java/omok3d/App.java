package omok3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Box;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/** 3D Omok: stones are dropped onto a thin wooden board and stack upward. Single free-camera 3D view. */
public class App extends Application {
    // ===== Board geometry =====
    static final int N = Board.N;
    static final double S = 22;                // size of one grid cell
    static final double R = S * 0.48;          // stone radius (stacked stones almost touch)
    static final double MARGIN = S * 0.95;     // grid edge to wooden top edge
    static final double THICK = 9;             // board thickness (keep it thin)
    static final double RIM = 8;               // width of the outer rim
    static final double TOP = 1.2;             // how far the wooden top rises above the rim
    static final double SURFACE = TOP + 0.4;   // height where stones rest (top of the grid lines)

    // ===== Behavior =====
    static final double DROP_HEIGHT = S * 9;   // height a stone falls from
    static final boolean START_CLOCK_ON_FIRST_MOVE = true;   // false: the clock runs from the start
    static final double INIT_RX = 36, INIT_RY = -18;         // default camera angles

    // ===== Colors =====
    static final Color WOOD_TOP = Color.web("#f3cf88");
    static final Color WOOD_RIM = Color.web("#e6ad55");
    static final Color LINE = Color.web("#7a5a35");
    static final Color HALO = Color.web("#ffd54f", 0.55);

    /** Camera presets: {label, rx, ry}. */
    static final Object[][] VIEWS = {
        {Strings.VIEW_FRONT, 0.0, 0.0}, {Strings.VIEW_BACK, 0.0, 180.0},
        {Strings.VIEW_LEFT, 0.0, -90.0}, {Strings.VIEW_RIGHT, 0.0, 90.0},
        {Strings.VIEW_TOP, 90.0, 0.0}, {Strings.VIEW_BOTTOM, -90.0, 0.0}
    };

    /** All user-visible text lives here so it is easy to change or translate. */
    static final class Strings {
        static final String TIP_SETTINGS = "시점 설정";
        static final String TIP_UNDO = "되돌리기";
        static final String TIP_REDO = "다시 실행";
        static final String TIP_STATS = "통계";
        static final String TIP_RESTART = "새 대국";
        static final String TIP_PICK_FIRST = "선공 선택 (첫 수 전에만)";
        static final String HINT = "드래그: 회전   ·   클릭: 착수 (아래층부터 쌓임)   ·   휠: 확대/축소";
        static final String VIEW_FRONT = "앞", VIEW_BACK = "뒤", VIEW_LEFT = "왼쪽",
            VIEW_RIGHT = "오른쪽", VIEW_TOP = "위", VIEW_BOTTOM = "아래", VIEW_DEFAULT = "기본";
        static final String FULL = "가득 참";

        static String hover(int x, int z, int floor) {
            return String.format(Locale.ROOT, "%d, %d  ·  %d층", x + 1, z + 1, floor + 1);
        }

        static String winner(int player, int count) {
            return (player == 1 ? "흑" : "백") + " 승리!  " + count + "목";
        }
    }

    /** One placed stone (kept for undo/redo). */
    record Move(int x, int y, int z, int player) {}

    // ===== Game state =====
    final Board board = new Board();
    final List<Move> history = new ArrayList<>();
    final List<Sphere> stoneNodes = new ArrayList<>();
    final Deque<Move> redoStack = new ArrayDeque<>();
    Board.Win win = null;
    int turn = 1;
    boolean over = false;
    int animating = 0;               // number of stones currently falling
    int hx = -1, hz = -1;            // column under the mouse
    int seconds = 0;

    // ===== 3D =====
    final Group world = new Group();
    final Group stones = new Group();
    final Group halos = new Group();
    final Rotate rx = new Rotate(INIT_RX, Rotate.X_AXIS);
    final Rotate ry = new Rotate(INIT_RY, Rotate.Y_AXIS);
    PerspectiveCamera cam;
    SubScene sub3d;
    double zoom = 1.0;                 // user zoom (mouse wheel) on top of the auto-fit distance
    double fitDist = 1000, fitX = 0, fitY = -60;   // camera placement that keeps the whole board in view
    double uiTop = 260;                // vertical space taken by the top bar (px), kept free of the board
    Sphere ghost;
    Box wood, rim;
    final PhongMaterial[] stoneMat = new PhongMaterial[3];
    final PhongMaterial[] ghostMat = new PhongMaterial[3];
    final PhongMaterial haloMat = new PhongMaterial(HALO);

    // ===== UI =====
    final Circle turnDot = new Circle(20);
    final Label infoLabel = new Label();
    final Label movesLabel = new Label("0");
    final Label timeLabel = new Label("00:00");
    final List<StackPane> toggleSlots = new ArrayList<>();
    Button undoButton, redoButton;
    final Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
        seconds++;
        updateTimeLabel();
    }));
    String css;

    double dragX, dragY, pressX, pressY;

    // Grid index -> 3D coordinate (x to the right, z into the screen)
    static double p(int i) { return (i - (N - 1) / 2.0) * S; }

    /** Vertical center of a stone on layer y (0 = bottom). JavaFX y points down, so up is negative. */
    static double stoneY(int y) { return -(SURFACE + R + y * S); }

    @Override
    public void start(Stage stage) {
        css = getClass().getResource("/style.css").toExternalForm();
        loadFonts();
        createMaterials();
        clock.setCycleCount(Animation.INDEFINITE);

        SubScene scene3d = buildScene3d();

        Label hint = new Label(Strings.HINT);
        hint.getStyleClass().add("hint");
        hint.setMouseTransparent(true);
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        hint.setMaxWidth(900);
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 0, 16, 0));

        StackPane root = new StackPane(buildBackground(), scene3d, buildOverlay(), hint);
        scene3d.widthProperty().bind(root.widthProperty());
        scene3d.heightProperty().bind(root.heightProperty());

        refreshUi();
        updateTimeLabel();
        if (!START_CLOCK_ON_FIRST_MOVE) clock.play();

        // Never open larger than the usable screen area (taskbar excluded), so nothing is cut off
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1240, vb.getWidth() * 0.92);
        double h = Math.min(900, vb.getHeight() * 0.88);
        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().add(css);
        stage.setMinWidth(Math.min(720, vb.getWidth()));
        stage.setMinHeight(Math.min(560, vb.getHeight()));
        stage.setTitle("3D Omok");
        stage.setScene(scene);
        stage.show();
        fitCamera();
    }

    /**
     * Loads rounded fonts from resources/fonts/ if present (Jua-Regular.ttf, Gaegu-Bold.ttf, ...).
     * Without them, style.css falls back to fonts installed on the system.
     */
    void loadFonts() {
        for (String f : new String[]{"Jua-Regular.ttf", "Gaegu-Bold.ttf", "Gaegu-Regular.ttf"}) {
            try (java.io.InputStream in = App.class.getResourceAsStream("/fonts/" + f)) {
                if (in != null) Font.loadFont(in, 12);
            } catch (java.io.IOException ignored) {
                // font is optional
            }
        }
    }

    /** Creates stone/ghost materials; uses images/stone_black.png and stone_white.png if present. */
    void createMaterials() {
        String[] files = {null, Assets.STONE_BLACK, Assets.STONE_WHITE};
        Color[] colors = {null, Color.web("#23232b"), Color.web("#fbfbfb")};
        for (int i = 1; i <= 2; i++) {
            stoneMat[i] = new PhongMaterial(colors[i]);
            stoneMat[i].setSpecularColor(Color.rgb(255, 255, 255, 0.8));
            stoneMat[i].setSpecularPower(40);
            Image tex = Assets.image(files[i]);
            if (tex != null) {
                stoneMat[i].setDiffuseColor(Color.WHITE);
                stoneMat[i].setDiffuseMap(tex);
            }
            Color c = colors[i];
            ghostMat[i] = new PhongMaterial(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.5));
        }
    }

    // ===================== 3D scene =====================

    /** Builds the transparent 3D sub-scene (board, stones, camera, lights) and its mouse handlers. */
    SubScene buildScene3d() {
        buildWorld();

        cam = new PerspectiveCamera(true);
        cam.setTranslateZ(-fitDist);   // distance/offset are recomputed by fitCamera() to fit the window
        cam.setTranslateY(fitY);
        cam.setNearClip(1);
        cam.setFarClip(5000);

        PointLight light = new PointLight(Color.rgb(85, 85, 85));
        light.setTranslateX(-400);
        light.setTranslateY(-800);
        light.setTranslateZ(-700);
        Group root3d = new Group(world, new AmbientLight(Color.rgb(180, 180, 180)), light);

        SubScene sub = new SubScene(root3d, 1240, 900, true, SceneAntialiasing.BALANCED);
        sub.setCamera(cam);
        sub.setFill(Color.TRANSPARENT);   // the background layer shows through
        sub3d = sub;
        sub.widthProperty().addListener((o, a, b) -> fitCamera());
        sub.heightProperty().addListener((o, a, b) -> fitCamera());

        sub.setOnMousePressed(e -> {
            dragX = pressX = e.getSceneX();
            dragY = pressY = e.getSceneY();
        });
        sub.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - dragX, dy = e.getSceneY() - dragY;
            dragX = e.getSceneX();
            dragY = e.getSceneY();
            ry.setAngle(ry.getAngle() - dx * 0.4);
            rx.setAngle(Math.max(-90, Math.min(90, rx.getAngle() + dy * 0.4)));
            setHover(-1, -1);
        });
        sub.setOnMouseReleased(e -> {
            if (over || e.getButton() != MouseButton.PRIMARY) return;
            if (Math.hypot(e.getSceneX() - pressX, e.getSceneY() - pressY) > 5) return;   // it was a drag
            int[] c = pickColumn(e);
            if (c != null) place(c[0], c[1]);
        });
        sub.setOnMouseMoved(e -> {
            int[] c = pickColumn(e);
            if (c == null) setHover(-1, -1);
            else setHover(c[0], c[1]);
        });
        sub.setOnMouseExited(e -> setHover(-1, -1));
        sub.setOnScroll(e -> {
            zoom = Math.max(0.4, Math.min(2.0, zoom * Math.exp(-e.getDeltaY() * 0.0015)));
            applyCamera();
        });
        return sub;
    }

    /** Screen-space bounding box {minX, maxX, minY, maxY} of the board for a camera at (cx, cy, -d). */
    double[] projectBoard(double d, double cx, double cy, double w, double h) {
        double f = (h / 2) / Math.tan(Math.toRadians(15));   // default vertical FOV of PerspectiveCamera is 30 deg
        double half = (N - 1) * S / 2 + MARGIN + RIM;
        double a = Math.toRadians(rx.getAngle()), b = Math.toRadians(ry.getAngle());
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2)
                for (double y : new double[]{-TOP, THICK}) {
                    double x = sx * half, z = sz * half;
                    double x2 = x * Math.cos(b) + z * Math.sin(b);
                    double z2 = -x * Math.sin(b) + z * Math.cos(b);
                    double y3 = y * Math.cos(a) - z2 * Math.sin(a);
                    double z3 = y * Math.sin(a) + z2 * Math.cos(a);
                    double depth = z3 + d;
                    double px = w / 2 + f * (x2 - cx) / depth;
                    double py = h / 2 + f * (y3 - cy) / depth;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                }
        return new double[]{minX, maxX, minY, maxY};
    }

    /** Picks the camera distance/offset so the whole board fits below the top bar for the current window size. */
    void fitCamera() {
        if (sub3d == null) return;
        double w = sub3d.getWidth(), h = sub3d.getHeight();
        if (w < 50 || h < 50) return;
        double side = 24, bottom = 44;
        double availW = Math.max(100, w - 2 * side);
        double availH = Math.max(100, h - uiTop - bottom);
        double lo = 300, hi = 6000;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            double[] bb = projectBoard(mid, 0, 0, w, h);
            if (bb[1] - bb[0] <= availW && bb[3] - bb[2] <= availH) hi = mid;
            else lo = mid;
        }
        double d = hi, cx = 0, cy = 0;
        double f = (h / 2) / Math.tan(Math.toRadians(15));
        for (int i = 0; i < 6; i++) {   // center the board in the free area
            double[] bb = projectBoard(d, cx, cy, w, h);
            cx -= (w / 2 - (bb[0] + bb[1]) / 2) * d / f;
            cy -= (uiTop + availH / 2 - (bb[2] + bb[3]) / 2) * d / f;
        }
        fitDist = d;
        fitX = cx;
        fitY = cy;
        applyCamera();
    }

    void applyCamera() {
        cam.setTranslateZ(-fitDist * zoom);
        cam.setTranslateX(fitX * zoom);
        cam.setTranslateY(fitY * zoom);
    }

    /**
     * Builds the board: rim, wooden top, grid lines, star points.
     * Optional textures: images/board_top.png (top face) and images/board_side.png (rim).
     */
    void buildWorld() {
        double half = (N - 1) * S / 2;
        double w = 2 * (half + MARGIN);
        double boardW = w + 2 * RIM;

        // Rim (slightly wider and lower than the top)
        rim = new Box(boardW, THICK, boardW);
        PhongMaterial rimMat = new PhongMaterial(WOOD_RIM);
        Image sideTex = Assets.image(Assets.BOARD_SIDE);
        if (sideTex != null) {
            rimMat.setDiffuseColor(Color.WHITE);
            rimMat.setDiffuseMap(sideTex);
        }
        rim.setMaterial(rimMat);
        rim.setTranslateY(THICK / 2);
        world.getChildren().add(rim);

        // Wooden top
        double woodH = THICK + TOP - 1;
        wood = new Box(w, woodH, w);
        PhongMaterial woodMat = new PhongMaterial(Color.WHITE);
        Image topTex = Assets.image(Assets.BOARD_TOP);
        woodMat.setDiffuseMap(topTex != null ? topTex : Assets.woodTexture(WOOD_TOP, 512));
        woodMat.setSpecularColor(Color.rgb(255, 240, 210, 0.2));
        woodMat.setSpecularPower(14);
        wood.setMaterial(woodMat);
        wood.setTranslateY(-TOP + woodH / 2);
        world.getChildren().add(wood);

        world.getChildren().add(grid());
        for (int a : new int[]{3, 9, 15})
            for (int b : new int[]{3, 9, 15}) {
                Cylinder star = new Cylinder(2.2, 0.6, 16);
                star.setMaterial(new PhongMaterial(LINE));
                star.setTranslateX(p(a));
                star.setTranslateZ(p(b));
                star.setTranslateY(-(TOP + 0.3));
                star.setMouseTransparent(true);
                world.getChildren().add(star);
            }

        ghost = new Sphere(R, 24);
        ghost.setVisible(false);
        ghost.setMouseTransparent(true);

        halos.setMouseTransparent(true);
        // Draw order: board, stones, win highlight, then the translucent preview last
        world.getChildren().addAll(stones, halos, ghost);
        world.getTransforms().addAll(rx, ry);
    }

    /** Merges the 19x19 grid lines (plus a thin outer frame line) into a single mesh. */
    MeshView grid() {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);
        double half = (N - 1) * S / 2;
        double cy = -(TOP + 0.2);
        int[] count = {0};
        for (int i = 0; i < N; i++) {
            double t = (i == 0 || i == N - 1) ? 0.42 : 0.22;   // outer lines are slightly thicker
            addBar(mesh, count, 0, cy, p(i), half + t, 0.2, t);   // line along x
            addBar(mesh, count, p(i), cy, 0, t, 0.2, half + t);   // line along z
        }
        double f = half + MARGIN * 0.55;   // thin frame line near the board edge
        addBar(mesh, count, 0, cy, -f, f, 0.2, 0.2);
        addBar(mesh, count, 0, cy, f, f, 0.2, 0.2);
        addBar(mesh, count, -f, cy, 0, 0.2, 0.2, f);
        addBar(mesh, count, f, cy, 0, 0.2, 0.2, f);

        MeshView view = new MeshView(mesh);
        view.setMaterial(new PhongMaterial(LINE));
        view.setCullFace(CullFace.NONE);
        view.setMouseTransparent(true);
        return view;
    }

    /** Adds a box with center (cx, cy, cz) and half sizes (hx, hy, hz) to the mesh. */
    void addBar(TriangleMesh m, int[] count, double cx, double cy, double cz, double hx, double hy, double hz) {
        for (int ix = 0; ix < 2; ix++)
            for (int iy = 0; iy < 2; iy++)
                for (int iz = 0; iz < 2; iz++)
                    m.getPoints().addAll(
                        (float) (cx + (ix == 0 ? -hx : hx)),
                        (float) (cy + (iy == 0 ? -hy : hy)),
                        (float) (cz + (iz == 0 ? -hz : hz)));
        int o = count[0] * 8;
        int[][] quads = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] q : quads) {
            m.getFaces().addAll(o + q[0], 0, o + q[1], 0, o + q[2], 0);
            m.getFaces().addAll(o + q[0], 0, o + q[2], 0, o + q[3], 0);
        }
        count[0]++;
    }

    // ===================== Background =====================

    /**
     * Background layer. Uses images/background.png if present; otherwise draws a pastel
     * gradient with soft waves, leaves and pebbles.
     */
    Node buildBackground() {
        Region base = new Region();
        String bg = Assets.url(Assets.BACKGROUND);
        if (bg != null) {
            base.setStyle("-fx-background-image: url('" + bg + "'); -fx-background-size: cover;"
                + " -fx-background-position: center center; -fx-background-repeat: no-repeat;");
            base.setMouseTransparent(true);
            return base;
        }
        base.setStyle("-fx-background-color: linear-gradient(to bottom right, #fdf4e6, #f6f0ff);");

        StackPane stack = new StackPane(base);
        stack.setMouseTransparent(true);
        stack.setMinSize(0, 0);   // oversized decorations must not enlarge the window

        decorate(stack, wave(640, 260, "#e4dcfa", 0.55), Pos.BOTTOM_RIGHT, 240, 150);
        decorate(stack, wave(600, 230, "#ffe3cc", 0.75), Pos.TOP_LEFT, -230, -130);
        decorate(stack, wave(720, 200, "#ece4ff", 0.55), Pos.BOTTOM_LEFT, -140, 190);
        decorate(stack, wave(420, 160, "#fff0dc", 0.8), Pos.TOP_RIGHT, 120, -60);

        Group leavesBottomLeft = new Group(leaf(170, -35, 0, 0), leaf(140, -70, 30, -20), leaf(120, -5, 20, 25), leaf(100, -100, 70, -40));
        decorate(stack, leavesBottomLeft, Pos.BOTTOM_LEFT, 10, -20);
        Group leavesTopRight = new Group(leaf(130, 160, 0, 0), leaf(105, 195, 30, 30), leaf(90, 130, -20, 40));
        decorate(stack, leavesTopRight, Pos.TOP_RIGHT, -20, 190);
        Group leavesBottomRight = new Group(leaf(90, 200, 0, 0), leaf(75, 160, 25, -20));
        decorate(stack, leavesBottomRight, Pos.BOTTOM_RIGHT, -330, -20);

        decorate(stack, pebbles(true), Pos.TOP_LEFT, 6, 235);
        decorate(stack, pebbles(false), Pos.BOTTOM_RIGHT, -20, -30);
        return stack;
    }

    void decorate(StackPane parent, Node node, Pos pos, double dx, double dy) {
        node.setMouseTransparent(true);
        node.setTranslateX(dx);
        node.setTranslateY(dy);
        StackPane.setAlignment(node, pos);
        parent.getChildren().add(node);
    }

    /** A large soft translucent ellipse used as a background wave. */
    Ellipse wave(double radiusX, double radiusY, String color, double alpha) {
        Ellipse e = new Ellipse(radiusX, radiusY);
        e.setFill(Color.web(color, alpha));
        return e;
    }

    SVGPath leaf(double length, double angle, double x, double y) {
        SVGPath leaf = new SVGPath();
        leaf.setContent("M0,0 C18,-26 62,-34 100,-16 C84,18 36,34 0,0 Z");
        leaf.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#c3deac")), new Stop(1, Color.web("#7fae72"))));
        leaf.setScaleX(length / 100);
        leaf.setScaleY(length / 100);
        leaf.setRotate(angle);
        leaf.setLayoutX(x);
        leaf.setLayoutY(y);
        leaf.setOpacity(0.92);
        return leaf;
    }

    /** A pair of decorative pebbles: one gray-lavender and one white. */
    Group pebbles(boolean small) {
        double k = small ? 0.8 : 1.0;
        Ellipse gray = new Ellipse(70 * k, 44 * k);
        gray.setFill(new RadialGradient(0, 0, 0.35, 0.3, 0.9, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#cfcadf")), new Stop(1, Color.web("#8f8aa8"))));
        Ellipse white = new Ellipse(48 * k, 30 * k);
        white.setFill(new RadialGradient(0, 0, 0.35, 0.3, 0.9, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.WHITE), new Stop(1, Color.web("#d9d6e6"))));
        white.setLayoutX(80 * k);
        white.setLayoutY(28 * k);
        return new Group(gray, white);
    }

    // ===================== Overlay UI =====================

    /** Top bar: settings, first-player toggle, undo/redo, stats, restart; second row: turn, move count, clock. */
    Node buildOverlay() {
        Button settings = iconButton(Icons.SETTINGS, Strings.TIP_SETTINGS);
        settings.setOnAction(e -> showCard(settings, buildViewPresets()));

        undoButton = iconButton(Icons.UNDO, Strings.TIP_UNDO);
        undoButton.setOnAction(e -> undo());
        redoButton = iconButton(Icons.REDO, Strings.TIP_REDO);
        redoButton.setOnAction(e -> redo());

        Button stats = iconButton(Icons.STATS, Strings.TIP_STATS);
        stats.setOnAction(e -> showCard(stats, buildStats()));

        Button restart = iconButton(Icons.REFRESH, Strings.TIP_RESTART);
        restart.setOnAction(e -> restart());

        HBox row1 = new HBox(14, settings, buildToggle(), undoButton, redoButton, stats, restart);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPickOnBounds(false);

        // Turn indicator
        infoLabel.getStyleClass().add("pill-text");
        HBox turnPill = pill("", turnDot, Icons.create(Icons.PLAY, 18), infoLabel);
        turnPill.setPrefWidth(340);
        turnPill.setMinWidth(340);

        movesLabel.getStyleClass().add("pill-number");
        HBox movesPill = pill("", Icons.create(Icons.HASH, 34), movesLabel);
        movesPill.setPrefWidth(270);
        movesPill.setMinWidth(270);

        timeLabel.getStyleClass().add("pill-number");
        HBox timePill = pill("pill-pink", Icons.create(Icons.CLOCK, 34), timeLabel);
        timePill.setPrefWidth(290);
        timePill.setMinWidth(290);

        HBox row2 = new HBox(36, turnPill, movesPill, timePill);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(0, 0, 0, 290));
        row2.setPickOnBounds(false);

        VBox overlay = new VBox(16, row1, row2);
        overlay.setPadding(new Insets(26, 40, 0, 40));
        overlay.setAlignment(Pos.TOP_LEFT);
        overlay.setPickOnBounds(false);   // clicks outside the pills pass through to the 3D view

        // Shrink the whole top bar when the window is narrower than the bar, so nothing is cut off
        Pane host = new Pane(overlay);
        host.setPickOnBounds(false);
        Scale scale = new Scale(1, 1, 0, 0);
        overlay.getTransforms().add(scale);
        Runnable relayout = () -> {
            double pw = overlay.prefWidth(-1), ph = overlay.prefHeight(-1);
            overlay.resize(pw, ph);
            double k = host.getWidth() > 0 ? Math.min(1.0, host.getWidth() / pw) : 1.0;
            scale.setX(k);
            scale.setY(k);
            double top = ph * k + 8;
            if (Math.abs(top - uiTop) > 0.5) {
                uiTop = top;
                fitCamera();
            }
        };
        host.widthProperty().addListener((o, a, b) -> relayout.run());
        host.heightProperty().addListener((o, a, b) -> relayout.run());
        Platform.runLater(relayout);
        return host;
    }

    /** Rounded lavender pill containing the given nodes. */
    HBox pill(String extraStyleClass, Node... nodes) {
        HBox box = new HBox(16, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("pill");
        if (!extraStyleClass.isEmpty()) box.getStyleClass().add(extraStyleClass);
        return box;
    }

    Button iconButton(String path, String tooltip) {
        Button b = new Button();
        b.setGraphic(Icons.create(path, 24));
        b.getStyleClass().addAll("pill", "icon-button");
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    /** Black/white toggle: shows whose turn it is; before the first move it also picks who starts. */
    HBox buildToggle() {
        HBox box = new HBox(4);
        box.getStyleClass().addAll("pill", "toggle-pill");
        box.setAlignment(Pos.CENTER);
        for (int player = 1; player <= 2; player++) {
            final int who = player;
            StackPane slot = new StackPane(stoneDot(who, 15));
            slot.getStyleClass().add("toggle-option");
            slot.setOnMouseClicked(e -> {
                if (history.isEmpty()) {
                    turn = who;
                    refreshUi();
                }
            });
            Tooltip.install(slot, new Tooltip(Strings.TIP_PICK_FIRST));
            toggleSlots.add(slot);
            box.getChildren().add(slot);
        }
        return box;
    }

    Circle stoneDot(int player, double radius) {
        Circle c = new Circle(radius);
        c.setFill(stoneFill(player));
        c.setStroke(player == 1 ? Color.web("#0d0d12") : Color.web("#b9b9cc"));
        c.setStrokeWidth(1);
        return c;
    }

    Paint stoneFill(int player) {
        if (player == 1)
            return new RadialGradient(0, 0, 0.35, 0.3, 0.9, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#7a7a8c")), new Stop(1, Color.web("#16161d")));
        return new RadialGradient(0, 0, 0.35, 0.3, 0.9, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.WHITE), new Stop(1, Color.web("#d6d6e4")));
    }

    /** Camera preset buttons shown in the settings popup. */
    Node buildViewPresets() {
        HBox row = new HBox(8);
        for (Object[] v : VIEWS) {
            Button b = new Button((String) v[0]);
            b.getStyleClass().add("chip");
            b.setFocusTraversable(false);
            b.setOnAction(e -> animateTo((double) v[1], (double) v[2]));
            row.getChildren().add(b);
        }
        Button def = new Button(Strings.VIEW_DEFAULT);
        def.getStyleClass().addAll("chip", "chip-accent");
        def.setFocusTraversable(false);
        def.setOnAction(e -> { zoom = 1.0; animateTo(INIT_RX, INIT_RY); applyCamera(); });
        row.getChildren().add(def);
        return row;
    }

    /** Simple statistics shown in the stats popup. */
    Node buildStats() {
        int black = 0, white = 0, tallest = 0;
        for (int x = 0; x < N; x++)
            for (int z = 0; z < N; z++) {
                int h = 0;
                for (int y = 0; y < N; y++) {
                    int v = board.get(x, y, z);
                    if (v == 1) black++;
                    if (v == 2) white++;
                    if (v != 0) h = y + 1;
                }
                tallest = Math.max(tallest, h);
            }
        VBox box = new VBox(8,
            statRow(stoneDot(1, 10), String.valueOf(black)),
            statRow(stoneDot(2, 10), String.valueOf(white)),
            statRow(Icons.create(Icons.STATS, 20), tallest + "F"));
        return box;
    }

    HBox statRow(Node icon, String value) {
        Label label = new Label(value);
        label.getStyleClass().add("pill-text");
        HBox row = new HBox(12, icon, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Shows a small rounded card below the anchor node; it closes when clicking elsewhere. */
    void showCard(Node anchor, Node content) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        VBox card = new VBox(content);
        card.getStyleClass().add("popup-card");
        card.getStylesheets().add(css);
        popup.getContent().add(card);
        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, b.getMinX(), b.getMaxY() + 10);
    }

    // ===================== Input and game flow =====================

    /**
     * Column {x, z} under the mouse: the column of a stone if one is hit,
     * otherwise the nearest grid point on the board top. Null if outside the board.
     */
    int[] pickColumn(MouseEvent e) {
        PickResult pr = e.getPickResult();
        if (pr == null) return null;
        Node n = pr.getIntersectedNode();
        if (n == null) return null;

        if (n.getProperties().get("col") instanceof int[] c) return c;

        if (n != wood && n != rim) return null;
        Point3D pt = pr.getIntersectedPoint();   // local coordinates of the box
        Box box = (Box) n;
        if (pt.getY() > -box.getHeight() / 2 + 0.05) return null;   // top face only
        int x = (int) Math.round(pt.getX() / S + (N - 1) / 2.0);
        int z = (int) Math.round(pt.getZ() / S + (N - 1) / 2.0);
        if (x < 0 || z < 0 || x >= N || z >= N) return null;
        return new int[]{x, z};
    }

    void setHover(int x, int z) {
        if (x < 0 || z < 0 || x >= N || z >= N) { x = -1; z = -1; }
        if (x == hx && z == hz) return;
        hx = x;
        hz = z;
        updateGhost();
        updateInfo();
    }

    /** Rotates the camera to the given angles along the shortest way. */
    void animateTo(double ax, double ay) {
        double cur = ry.getAngle();
        double target = cur + (((ay - cur) % 360 + 540) % 360 - 180);
        new Timeline(new KeyFrame(Duration.millis(450),
            new KeyValue(rx.angleProperty(), ax, Interpolator.EASE_BOTH),
            new KeyValue(ry.angleProperty(), target, Interpolator.EASE_BOTH))).play();
    }

    /** Player move: drops the current player's stone into column (x, z). */
    void place(int x, int z) {
        if (over) return;
        redoStack.clear();
        applyMove(x, z, turn);
    }

    /** Places a stone for the given player, starts the drop animation and checks for a win. */
    void applyMove(int x, int z, int player) {
        int y = board.place(x, z, player);
        if (y < 0) return;

        Sphere sphere = createStone(x, z, player);
        stones.getChildren().add(sphere);
        stoneNodes.add(sphere);
        history.add(new Move(x, y, z, player));
        if (history.size() == 1 && START_CLOCK_ON_FIRST_MOVE) clock.play();

        win = board.findWin(player);
        if (win != null) {
            over = true;
            clock.pause();
        } else {
            turn = 3 - player;
        }

        final Board.Win placedWin = win;
        animateDrop(sphere, stoneY(y), () -> {
            if (placedWin != null && win == placedWin) showWinHalos();   // highlight after landing
            refreshUi();
        });
        refreshUi();
        updateGhost();
    }

    Sphere createStone(int x, int z, int player) {
        Sphere s = new Sphere(R, 24);
        s.setMaterial(stoneMat[player]);
        s.setTranslateX(p(x));
        s.setTranslateZ(p(z));
        s.getProperties().put("col", new int[]{x, z});   // clicking a stone targets its column
        return s;
    }

    /**
     * Drop animation: the stone falls with gravity (ease-in), then bounces twice
     * with decreasing height before settling on its final position.
     */
    void animateDrop(Sphere stone, double targetY, Runnable onFinished) {
        double startY = targetY - DROP_HEIGHT;
        double bounce1 = S * 0.55, bounce2 = S * 0.2;
        stone.setTranslateY(startY);

        Timeline t = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(stone.translateYProperty(), startY)),
            new KeyFrame(Duration.millis(340), new KeyValue(stone.translateYProperty(), targetY, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(530), new KeyValue(stone.translateYProperty(), targetY - bounce1, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(720), new KeyValue(stone.translateYProperty(), targetY, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(830), new KeyValue(stone.translateYProperty(), targetY - bounce2, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(940), new KeyValue(stone.translateYProperty(), targetY, Interpolator.EASE_IN)));
        t.setOnFinished(e -> {
            animating--;
            onFinished.run();
        });
        animating++;
        t.play();
    }

    void showWinHalos() {
        halos.getChildren().clear();
        for (int[] st : win.stones()) {
            Sphere halo = new Sphere(R * 1.3, 24);
            halo.setMaterial(haloMat);
            halo.setTranslateX(p(st[0]));
            halo.setTranslateY(stoneY(st[1]));
            halo.setTranslateZ(p(st[2]));
            halo.setMouseTransparent(true);
            halos.getChildren().add(halo);
        }
    }

    /** Takes back the last move (ignored while a stone is still falling). */
    void undo() {
        if (animating > 0 || history.isEmpty()) return;
        Move m = history.remove(history.size() - 1);
        board.removeTop(m.x(), m.z());
        stones.getChildren().remove(stoneNodes.remove(stoneNodes.size() - 1));
        redoStack.push(m);

        win = null;
        over = false;
        halos.getChildren().clear();
        turn = m.player();
        if (history.isEmpty()) {
            if (START_CLOCK_ON_FIRST_MOVE) {
                clock.stop();
                seconds = 0;
                updateTimeLabel();
            }
        } else if (clock.getStatus() != Animation.Status.RUNNING) {
            clock.play();
        }
        refreshUi();
        updateGhost();
    }

    /** Replays the last undone move. */
    void redo() {
        if (animating > 0 || redoStack.isEmpty()) return;
        Move m = redoStack.pop();
        applyMove(m.x(), m.z(), m.player());
    }

    /** Starts a new game. */
    void restart() {
        board.clear();
        stones.getChildren().clear();
        halos.getChildren().clear();
        stoneNodes.clear();
        history.clear();
        redoStack.clear();
        turn = 1;
        over = false;
        win = null;
        clock.stop();
        seconds = 0;
        updateTimeLabel();
        if (!START_CLOCK_ON_FIRST_MOVE) clock.play();
        refreshUi();
        updateGhost();
    }

    // ===================== UI refresh =====================

    /** Syncs all UI elements with the game state. */
    void refreshUi() {
        turnDot.setFill(stoneFill(turn));
        turnDot.setStroke(turn == 1 ? Color.web("#0d0d12") : Color.web("#b9b9cc"));
        for (int i = 0; i < toggleSlots.size(); i++)
            toggleSlots.get(i).pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), turn == i + 1);
        movesLabel.setText(String.valueOf(history.size()));
        if (undoButton != null) undoButton.setDisable(history.isEmpty() || animating > 0);
        if (redoButton != null) redoButton.setDisable(redoStack.isEmpty() || animating > 0);
        updateInfo();
    }

    void updateTimeLabel() {
        timeLabel.setText(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
    }

    /** Text inside the turn pill: win message, or where the stone under the mouse would land. */
    void updateInfo() {
        String text = "";
        if (over && win != null) {
            int[] s = win.stones().get(0);
            text = Strings.winner(board.get(s[0], s[1], s[2]), win.stones().size());
        } else if (hx >= 0) {
            int y = board.dropY(hx, hz);
            text = y >= 0 ? Strings.hover(hx, hz, y) : Strings.FULL;
        }
        infoLabel.setText(text);
    }

    /** Translucent preview stone at the spot where the next stone would land. */
    void updateGhost() {
        int y = (!over && hx >= 0) ? board.dropY(hx, hz) : -1;
        ghost.setVisible(y >= 0);
        if (y < 0) return;
        ghost.setMaterial(ghostMat[turn]);
        ghost.setTranslateX(p(hx));
        ghost.setTranslateY(stoneY(y));
        ghost.setTranslateZ(p(hz));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
