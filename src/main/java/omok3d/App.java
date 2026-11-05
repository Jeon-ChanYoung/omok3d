package omok3d;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {
    static final int N = Board.N;
    static final double S = 20;        // 3D 한 칸 크기
    static final double MARGIN = S;    // 격자와 바깥 큐브 테두리 사이 여백
    static final double C = 22;        // 2D 평면도 한 칸 크기
    static final double MC = 7;        // 투영 미니맵 한 칸 크기

    // ===== 색상 =====
    static final Color[] STONE = {null, Color.web("#23232b"), Color.web("#fbfbfb")};
    static final Color[] STONE_EDGE = {null, Color.web("#000000"), Color.web("#8a8a99")};
    static final String[] NAME = {"", "흑", "백"};
    static final Color FRAME = Color.web("#b9a2e8");          // 큐브 테두리
    static final Color LATTICE = Color.web("#bcaedb");        // 19x19x19 전체 격자선 (은은하게)
    static final Color SHEET = Color.web("#8e6fe0");          // 현재 층 강조 (점 + 테두리)
    static final Color SHEET_FILL = Color.web("#b9a2e8", 0.07); // 현재 층 판 (거의 투명)
    static final Color HALO = Color.web("#ffd54f", 0.55);     // 승리 돌 강조
    static final Color BOARD2D = Color.web("#fff6e9");
    static final Color LINE2D = Color.web("#cbb8e6");

    static final double INIT_RX = 25, INIT_RY = -30;

    // 시점: {이름, rx, ry}
    static final Object[][] VIEWS = {
        {"앞", 0.0, 0.0}, {"뒤", 0.0, 180.0},
        {"왼쪽", 0.0, -90.0}, {"오른쪽", 0.0, 90.0},
        {"위", 90.0, 0.0}, {"아래", -90.0, 0.0}
    };

    final Board board = new Board();
    Board.Win win = null;
    int turn = 1;
    boolean over = false;
    int layer = 0;
    int hx = -1, hy = -1;            // 마우스 오버 칸 (현재 층 기준)
    int lx = -1, ly = -1, lz = -1;   // 마지막 착수

    final Group world = new Group();
    final Group stones = new Group();
    final Group layerGroup = new Group();
    final Rotate rx = new Rotate(INIT_RX, Rotate.X_AXIS);
    final Rotate ry = new Rotate(INIT_RY, Rotate.Y_AXIS);
    PerspectiveCamera cam;
    Sphere ghost;
    Box pickPlane;                   // 3D에서 클릭 위치를 잡는 거의 투명한 판

    final Canvas canvas = new Canvas(N * C, N * C);
    final Canvas[] minis = new Canvas[3];   // 축별 투영 미니맵 (index = axis)
    final Label status = new Label();
    final Circle statusDot = new Circle(10);
    final Label layerLabel = new Label();
    Slider slider;

    final PhongMaterial[] stoneMat = new PhongMaterial[3];
    final PhongMaterial[] ghostMat = new PhongMaterial[3];
    final PhongMaterial haloMat = new PhongMaterial(HALO);

    double dragX, dragY, pressX, pressY;

    // 격자 좌표 -> 3D 좌표 (x 오른쪽, y 위, z 깊이(앞→뒤))
    static double p(int i) { return (i - (N - 1) / 2.0) * S; }

    @Override
    public void start(Stage stage) {
        for (int i = 1; i <= 2; i++) {
            stoneMat[i] = new PhongMaterial(STONE[i]);
            stoneMat[i].setSpecularColor(Color.rgb(255, 255, 255, 0.8));
            stoneMat[i].setSpecularPower(40);
            Color c = STONE[i];
            ghostMat[i] = new PhongMaterial(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.5));
        }

        buildWorld();

        cam = new PerspectiveCamera(true);
        cam.setTranslateZ(-1100);
        cam.setNearClip(1);
        cam.setFarClip(5000);

        PointLight light = new PointLight(Color.rgb(120, 120, 120));
        light.setTranslateX(-400);
        light.setTranslateY(-600);
        light.setTranslateZ(-900);
        Group root3d = new Group(world, new AmbientLight(Color.rgb(170, 170, 170)), light);

        SubScene sub = new SubScene(root3d, 700, 700, true, SceneAntialiasing.BALANCED);
        sub.setCamera(cam);
        sub.setFill(Color.web("#f3ecfb"));   // 3D 뷰는 단색 필수 (그라데이션/클립은 잔상 발생)

        Pane viewPane = new Pane(sub);
        viewPane.getStyleClass().add("view-pane");
        viewPane.setMinSize(0, 0);
        viewPane.setPrefSize(700, 700);
        sub.widthProperty().bind(viewPane.widthProperty());
        sub.heightProperty().bind(viewPane.heightProperty());

        Label hint = new Label("드래그: 회전   ·   보라색 층 클릭: 착수   ·   휠 / ↑↓: 층 이동   ·   Ctrl+휠: 확대");
        hint.getStyleClass().add("hint");
        hint.setLayoutX(16);
        hint.layoutYProperty().bind(viewPane.heightProperty().subtract(30));
        hint.setMouseTransparent(true);
        viewPane.getChildren().add(hint);

        // ----- 3D 마우스 조작 -----
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
            if (Math.hypot(e.getSceneX() - pressX, e.getSceneY() - pressY) > 5) return;  // 드래그면 착수 아님
            int[] c = pickCell(e);
            if (c != null) place(c[0], c[1], layer);
        });
        sub.setOnMouseMoved(e -> {
            int[] c = pickCell(e);
            if (c == null) setHover(-1, -1);
            else setHover(c[0], c[1]);
        });
        sub.setOnMouseExited(e -> setHover(-1, -1));
        sub.setOnScroll(e -> {
            if (e.isControlDown()) {
                double z = cam.getTranslateZ() + e.getDeltaY() * 1.5;
                cam.setTranslateZ(Math.max(-2200, Math.min(-500, z)));
            } else if (e.getDeltaY() != 0) {
                moveLayer(e.getDeltaY() > 0 ? -1 : 1);
            }
        });

        // ----- 상단: 시점 버튼 + 다시 시작 -----
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 14, 0));
        top.getChildren().add(new Label("시점"));
        for (Object[] v : VIEWS) {
            Button b = new Button((String) v[0]);
            b.setFocusTraversable(false);
            b.setOnAction(e -> animateTo((double) v[1], (double) v[2]));
            top.getChildren().add(b);
        }
        Button free = new Button("기본 시점");
        free.getStyleClass().add("accent");
        free.setFocusTraversable(false);
        free.setOnAction(e -> animateTo(INIT_RX, INIT_RY));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button restart = new Button("다시 시작");
        restart.getStyleClass().add("accent");
        restart.setFocusTraversable(false);
        restart.setOnAction(e -> restart());
        top.getChildren().addAll(free, spacer, restart);

        // ----- 오른쪽 패널 -----
        slider = new Slider(0, N - 1, 0);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setFocusTraversable(false);
        slider.valueProperty().addListener((o, a, b) -> {
            layer = (int) Math.round(b.doubleValue());
            updateLayer();
        });
        Button prev = new Button("◀ 앞");
        prev.setFocusTraversable(false);
        prev.setOnAction(e -> moveLayer(-1));
        Button next = new Button("뒤 ▶");
        next.setFocusTraversable(false);
        next.setOnAction(e -> moveLayer(1));
        HBox sliderRow = new HBox(8, prev, slider, next);
        sliderRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(slider, Priority.ALWAYS);

        // 2D 평면도 (현재 층) 입력
        canvas.setOnMouseClicked(e -> {
            if (over) return;
            place((int) (e.getX() / C), N - 1 - (int) (e.getY() / C), layer);
        });
        canvas.setOnMouseMoved(e -> setHover((int) (e.getX() / C), N - 1 - (int) (e.getY() / C)));
        canvas.setOnMouseExited(e -> setHover(-1, -1));

        status.getStyleClass().add("status-label");
        HBox statusBox = new HBox(10, statusDot, status);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        // 투영 미니맵 3개 (앞·뒤 / 위·아래 / 좌·우)
        String[] miniNames = {"좌·우에서 본 면", "위·아래에서 본 면", "앞·뒤에서 본 면"};
        HBox miniRow = new HBox(10);
        for (int a = 2; a >= 0; a--) {
            minis[a] = new Canvas(N * MC, N * MC);
            Label cap = new Label(miniNames[a]);
            cap.getStyleClass().add("hint");
            miniRow.getChildren().add(new VBox(4, cap, minis[a]));
        }
        Label miniTitle = new Label("투영 보기  —  한 면에서 같은 색 5개가 한 줄이면 승리");
        miniTitle.getStyleClass().add("hint");

        VBox right = new VBox(10, statusBox, canvas, layerLabel, sliderRow, miniTitle, miniRow);
        right.getStyleClass().add("panel");
        right.setMaxHeight(Region.USE_PREF_SIZE);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));
        root.setTop(top);
        root.setCenter(viewPane);
        root.setRight(right);
        BorderPane.setMargin(right, new Insets(0, 0, 0, 18));
        BorderPane.setAlignment(right, Pos.TOP_CENTER);

        updateLayer();
        updateStatus();
        drawAll();

        Scene scene = new Scene(root, 1320, 900);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        // 키보드: ↑ 앞 층, ↓ 뒤 층
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.UP) { moveLayer(-1); e.consume(); }
            else if (e.getCode() == KeyCode.DOWN) { moveLayer(1); e.consume(); }
        });
        stage.setTitle("3D 오목");
        stage.setScene(scene);
        stage.show();
    }

    // ===================== 3D 월드 구성 =====================

    void buildWorld() {
        double half = (N - 1) * S / 2;
        double h = half + MARGIN;

        // 바깥 큐브 테두리 (격자보다 한 칸 넓음)
        for (int sx : new int[]{-1, 1})
            for (int sy : new int[]{-1, 1}) {
                world.getChildren().add(edge(new Point3D(sx * h, sy * h, -h), new Point3D(sx * h, sy * h, h), FRAME, 1.1));
                world.getChildren().add(edge(new Point3D(sx * h, -h, sy * h), new Point3D(sx * h, h, sy * h), FRAME, 1.1));
                world.getChildren().add(edge(new Point3D(-h, sx * h, sy * h), new Point3D(h, sx * h, sy * h), FRAME, 1.1));
            }

        // 19x19x19 전체 격자선 (아주 가는 선, 메시 하나로 합쳐서 가볍게)
        world.getChildren().add(lines(false, 0.05, LATTICE));

        ghost = new Sphere(S * 0.42, 24);
        ghost.setVisible(false);
        ghost.setMouseTransparent(true);

        // 현재 층: 굵은 격자선 + 테두리 + 거의 투명한 판(클릭 위치 계산용)
        double sh = half + S / 2;
        layerGroup.getChildren().add(lines(true, 0.15, SHEET));
        layerGroup.getChildren().add(edge(new Point3D(-sh, -sh, 0), new Point3D(sh, -sh, 0), SHEET, 0.9));
        layerGroup.getChildren().add(edge(new Point3D(-sh, sh, 0), new Point3D(sh, sh, 0), SHEET, 0.9));
        layerGroup.getChildren().add(edge(new Point3D(-sh, -sh, 0), new Point3D(-sh, sh, 0), SHEET, 0.9));
        layerGroup.getChildren().add(edge(new Point3D(sh, -sh, 0), new Point3D(sh, sh, 0), SHEET, 0.9));
        pickPlane = new Box(2 * sh, 2 * sh, 0.5);
        pickPlane.setMaterial(new PhongMaterial(SHEET_FILL));
        layerGroup.getChildren().add(pickPlane);   // 반투명은 마지막에

        // 그리는 순서: 테두리/격자점 → 돌 → 미리보기 → 현재 층
        world.getChildren().addAll(stones, ghost, layerGroup);
        world.getTransforms().addAll(rx, ry);
    }

    /**
     * 격자선을 가는 직육면체로 만들어 하나의 메시로 합침 (선 1,083개도 가볍게).
     * single=false: 19x19x19 전체 격자 (x/y/z 방향 선 각 361개)
     * single=true : z=0 한 층의 19x19 격자 (현재 층 표시용)
     */
    MeshView lines(boolean single, double t, Color color) {
        TriangleMesh m = new TriangleMesh();
        m.getTexCoords().addAll(0, 0);
        double half = (N - 1) * S / 2;
        int[] count = {0};
        for (int a = 0; a < N; a++)
            for (int b = 0; b < N; b++) {
                if (single) {
                    if (b > 0) continue;
                    // z=0 평면: y=a 위치의 가로선, x=a 위치의 세로선
                    addBar(m, count, 0, -p(a), 0, half, t, t);
                    addBar(m, count, p(a), 0, 0, t, half, t);
                } else {
                    addBar(m, count, 0, -p(a), p(b), half, t, t);   // x 방향 선
                    addBar(m, count, p(a), 0, p(b), t, half, t);    // y 방향 선
                    addBar(m, count, p(a), -p(b), 0, t, t, half);   // z 방향 선
                }
            }
        MeshView mv = new MeshView(m);
        mv.setMaterial(new PhongMaterial(color));
        mv.setCullFace(CullFace.NONE);
        mv.setMouseTransparent(true);
        return mv;
    }

    /** 중심 (cx,cy,cz), 반길이 (hx,hy,hz)인 직육면체를 메시에 추가 */
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

    // a -> b 선분 (축 정렬, 방향은 + 쪽으로 줄 것)
    Cylinder edge(Point3D a, Point3D b, Color c, double radius) {
        Point3D diff = b.subtract(a);
        Point3D mid = a.midpoint(b);
        Cylinder cyl = new Cylinder(radius, diff.magnitude(), 8);
        cyl.setMaterial(new PhongMaterial(c));
        cyl.setMouseTransparent(true);
        Point3D yAxis = new Point3D(0, 1, 0);
        double angle = yAxis.angle(diff);
        Point3D axis = yAxis.crossProduct(diff);
        cyl.getTransforms().add(new Translate(mid.getX(), mid.getY(), mid.getZ()));
        if (axis.magnitude() > 1e-9) cyl.getTransforms().add(new Rotate(angle, axis));
        return cyl;
    }

    // ===================== 입력/게임 진행 =====================

    /** 3D에서 마우스가 가리키는 현재 층의 칸 {x, y}. 판 밖이면 null. */
    int[] pickCell(MouseEvent e) {
        PickResult pr = e.getPickResult();
        if (pr == null || pr.getIntersectedNode() != pickPlane) return null;
        Point3D pt = pr.getIntersectedPoint();   // 판(Box)의 로컬 좌표
        int x = (int) Math.round(pt.getX() / S + (N - 1) / 2.0);
        int y = (int) Math.round((N - 1) / 2.0 - pt.getY() / S);
        if (x < 0 || y < 0 || x >= N || y >= N) return null;
        return new int[]{x, y};
    }

    void setHover(int x, int y) {
        if (x < 0 || y < 0 || x >= N || y >= N) { x = -1; y = -1; }
        if (x == hx && y == hy) return;
        hx = x;
        hy = y;
        drawMain();
        updateGhost();
    }

    void moveLayer(int d) {
        slider.setValue(Math.max(0, Math.min(N - 1, layer + d)));
    }

    void animateTo(double ax, double ay) {
        double cur = ry.getAngle();
        double target = cur + (((ay - cur) % 360 + 540) % 360 - 180);  // 가까운 방향으로 회전
        new Timeline(new KeyFrame(Duration.millis(450),
            new KeyValue(rx.angleProperty(), ax, Interpolator.EASE_BOTH),
            new KeyValue(ry.angleProperty(), target, Interpolator.EASE_BOTH))).play();
    }

    void place(int x, int y, int z) {
        if (over || !board.place(x, y, z, turn)) return;

        Sphere s = new Sphere(S * 0.42, 24);
        s.setMaterial(stoneMat[turn]);
        s.setTranslateX(p(x));
        s.setTranslateY(-p(y));   // JavaFX는 y가 아래 방향
        s.setTranslateZ(p(z));
        s.setMouseTransparent(true);
        stones.getChildren().add(s);
        lx = x;
        ly = y;
        lz = z;

        win = board.findWin(turn);
        if (win != null) {
            over = true;
            String[] names = {"좌·우", "위·아래", "앞·뒤"};
            status.setText(NAME[turn] + " 승리!  (" + names[win.axis()] + " 시점 5목)");
            for (int[] st : win.stones()) {
                Sphere halo = new Sphere(S * 0.62, 24);
                halo.setMaterial(haloMat);
                halo.setTranslateX(p(st[0]));
                halo.setTranslateY(-p(st[1]));
                halo.setTranslateZ(p(st[2]));
                halo.setMouseTransparent(true);
                stones.getChildren().add(halo);
            }
            switch (win.axis()) {
                case 0 -> animateTo(0, 90);
                case 1 -> animateTo(90, 0);
                default -> animateTo(0, 0);
            }
        } else {
            turn = 3 - turn;
            updateStatus();
        }
        drawAll();
        updateGhost();
    }

    void restart() {
        board.clear();
        stones.getChildren().clear();
        turn = 1;
        over = false;
        win = null;
        lx = ly = lz = -1;
        updateStatus();
        drawAll();
        updateGhost();
    }

    void updateStatus() {
        status.setText("차례: " + NAME[turn]);
        statusDot.setFill(STONE[turn]);
        statusDot.setStroke(STONE_EDGE[turn]);
    }

    void updateLayer() {
        layerLabel.setText("현재 층 z = " + (layer + 1) + " / " + N + "   (앞 → 뒤)");
        layerGroup.setTranslateZ(p(layer));
        drawMain();
        updateGhost();
    }

    // 3D 안에서 놓일 위치 미리보기
    void updateGhost() {
        boolean ok = !over && hx >= 0 && hy >= 0 && board.get(hx, hy, layer) == 0;
        ghost.setVisible(ok);
        if (!ok) return;
        ghost.setMaterial(ghostMat[turn]);
        ghost.setTranslateX(p(hx));
        ghost.setTranslateY(-p(hy));
        ghost.setTranslateZ(p(layer));
    }

    // ===================== 2D 그리기 =====================

    void drawAll() {
        drawMain();
        drawMinis();
    }

    /** 현재 층 평면도 */
    void drawMain() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setGlobalAlpha(1);
        g.setFill(BOARD2D);
        g.fillRoundRect(0, 0, N * C, N * C, 16, 16);
        g.setStroke(LINE2D);
        g.setLineWidth(1.1);
        for (int i = 0; i < N; i++) {
            double o = C / 2 + i * C;
            g.strokeLine(o, C / 2, o, N * C - C / 2);
            g.strokeLine(C / 2, o, N * C - C / 2, o);
        }
        g.setFill(LINE2D);
        for (int a : new int[]{3, 9, 15})
            for (int b : new int[]{3, 9, 15})
                g.fillOval(C / 2 + a * C - 3, C / 2 + b * C - 3, 6, 6);

        for (int x = 0; x < N; x++)
            for (int y = 0; y < N; y++)
                for (int z = 0; z < N; z++) {
                    int v = board.get(x, y, z);
                    if (v == 0) continue;
                    // 현재 층 돌은 진하게, 다른 층 돌은 흐리게 (참고용)
                    g.setGlobalAlpha(z == layer ? 1.0 : 0.2);
                    drawStone(g, x, y, v);
                }
        g.setGlobalAlpha(1);

        if (lz == layer) {   // 마지막 착수 표시
            g.setStroke(Color.web("#8e6fe0"));
            g.setLineWidth(2.2);
            g.strokeOval(lx * C + 1, (N - 1 - ly) * C + 1, C - 2, C - 2);
        }
        if (!over && hx >= 0 && board.get(hx, hy, layer) == 0) {   // 마우스 오버 미리보기
            g.setGlobalAlpha(0.5);
            drawStone(g, hx, hy, turn);
            g.setGlobalAlpha(1);
        }
    }

    void drawStone(GraphicsContext g, int x, int y, int v) {
        double cx = x * C + 3, cy = (N - 1 - y) * C + 3, d = C - 6;
        g.setFill(STONE[v]);
        g.fillOval(cx, cy, d, d);
        g.setStroke(STONE_EDGE[v]);
        g.setLineWidth(1.3);
        g.strokeOval(cx, cy, d, d);
    }

    /** 3개 방향 투영 미니맵 (흑/백 각각의 투영을 한 칸에 겹쳐 표시) */
    void drawMinis() {
        int[][][] pr = new int[3][N][N];   // [axis][col][row], 비트: 1=흑, 2=백
        for (int x = 0; x < N; x++)
            for (int y = 0; y < N; y++)
                for (int z = 0; z < N; z++) {
                    int v = board.get(x, y, z);
                    if (v == 0) continue;
                    pr[0][z][N - 1 - y] |= v;   // 좌/우에서 봄: 가로=z, 세로=y
                    pr[1][x][N - 1 - z] |= v;   // 위/아래에서 봄: 가로=x, 세로=z
                    pr[2][x][N - 1 - y] |= v;   // 앞/뒤에서 봄: 가로=x, 세로=y
                }
        for (int a = 0; a < 3; a++) drawMini(minis[a], pr[a], a);
    }

    void drawMini(Canvas cv, int[][] m, int axis) {
        GraphicsContext g = cv.getGraphicsContext2D();
        double W = N * MC;
        g.setGlobalAlpha(1);
        g.setFill(BOARD2D);
        g.fillRoundRect(0, 0, W, W, 10, 10);
        g.setStroke(LINE2D);
        g.setLineWidth(0.6);
        for (int i = 0; i < N; i++) {
            double o = MC / 2 + i * MC;
            g.strokeLine(o, MC / 2, o, W - MC / 2);
            g.strokeLine(MC / 2, o, W - MC / 2, o);
        }
        double d = MC - 1.5;
        for (int c = 0; c < N; c++)
            for (int r = 0; r < N; r++) {
                int v = m[c][r];
                if (v == 0) continue;
                double cx = c * MC + 0.75, cy = r * MC + 0.75;
                if (v == 1) {
                    g.setFill(STONE[1]);
                    g.fillOval(cx, cy, d, d);
                } else if (v == 2) {
                    g.setFill(STONE[2]);
                    g.fillOval(cx, cy, d, d);
                    g.setStroke(STONE_EDGE[2]);
                    g.setLineWidth(0.7);
                    g.strokeOval(cx, cy, d, d);
                } else {   // 흑백 겹침: 반반
                    g.setFill(STONE[2]);
                    g.fillArc(cx, cy, d, d, 90, 180, ArcType.ROUND);
                    g.setFill(STONE[1]);
                    g.fillArc(cx, cy, d, d, 270, 180, ArcType.ROUND);
                    g.setStroke(STONE_EDGE[2]);
                    g.setLineWidth(0.7);
                    g.strokeOval(cx, cy, d, d);
                }
            }
        // 승리한 면이면 5목 칸 강조
        if (win != null && win.axis() == axis) {
            g.setStroke(Color.web("#ffb300"));
            g.setLineWidth(2);
            for (int[] cell : win.cells()) {
                int i = cell[0], j = cell[1];
                int col, row;
                if (axis == 0) { col = j; row = N - 1 - i; }
                else if (axis == 1) { col = i; row = N - 1 - j; }
                else { col = i; row = N - 1 - j; }
                g.strokeOval(col * MC - 0.5, row * MC - 0.5, MC + 1, MC + 1);
            }
            g.strokeRoundRect(1, 1, W - 2, W - 2, 10, 10);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
