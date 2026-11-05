package omok3d;

import java.util.ArrayList;
import java.util.List;

/** 19x19x19 보드와 투영 기반 승리 판정. 좌표는 [x][y][z], 0=빈칸, 1=흑, 2=백. */
public class Board {
    public static final int N = 19;
    public static final int WIN = 5;

    private final int[][][] g = new int[N][N][N];

    /**
     * 승리 정보.
     * axis  : 0=좌/우 시점(x축을 따라 봄), 1=위/아래 시점(y축), 2=앞/뒤 시점(z축)
     * cells : 투영면에서 5목을 이룬 칸 {i, j}  (axis0: i=y,j=z / axis1: i=x,j=z / axis2: i=x,j=y)
     * stones: 그 칸들에 투영되는 실제 돌의 3D 좌표 {x, y, z}
     */
    public record Win(int axis, List<int[]> cells, List<int[]> stones) {}

    public int get(int x, int y, int z) {
        return g[x][y][z];
    }

    public boolean place(int x, int y, int z, int player) {
        if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) return false;
        if (g[x][y][z] != 0) return false;
        g[x][y][z] = player;
        return true;
    }

    public void clear() {
        for (int[][] a : g) for (int[] b : a) java.util.Arrays.fill(b, 0);
    }

    /** 3개 축 투영 중 5목이 있으면 그 정보를, 없으면 null. 상대 돌은 무시. */
    public Win findWin(int player) {
        for (int axis = 0; axis < 3; axis++) {
            int[] r = find5(project(player, axis));
            if (r == null) continue;

            List<int[]> cells = new ArrayList<>();
            List<int[]> stones = new ArrayList<>();
            for (int k = 0; k < WIN; k++) {
                int i = r[0] + r[2] * k, j = r[1] + r[3] * k;
                cells.add(new int[]{i, j});
                for (int c = 0; c < N; c++) {
                    int x, y, z;
                    switch (axis) {
                        case 0 -> { x = c; y = i; z = j; }
                        case 1 -> { x = i; y = c; z = j; }
                        default -> { x = i; y = j; z = c; }
                    }
                    if (g[x][y][z] == player) stones.add(new int[]{x, y, z});
                }
            }
            return new Win(axis, cells, stones);
        }
        return null;
    }

    public int checkWin(int player) {
        Win w = findWin(player);
        return w == null ? -1 : w.axis();
    }

    private boolean[][] project(int player, int axis) {
        boolean[][] m = new boolean[N][N];
        for (int x = 0; x < N; x++)
            for (int y = 0; y < N; y++)
                for (int z = 0; z < N; z++) {
                    if (g[x][y][z] != player) continue;
                    switch (axis) {
                        case 0 -> m[y][z] = true;
                        case 1 -> m[x][z] = true;
                        default -> m[x][y] = true;
                    }
                }
        return m;
    }

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    /** @return {시작 i, 시작 j, di, dj} 또는 null */
    private int[] find5(boolean[][] m) {
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                if (!m[i][j]) continue;
                for (int[] d : DIRS) {
                    int k = 1;
                    while (k < WIN) {
                        int a = i + d[0] * k, b = j + d[1] * k;
                        if (a < 0 || b < 0 || a >= N || b >= N || !m[a][b]) break;
                        k++;
                    }
                    if (k == WIN) return new int[]{i, j, d[0], d[1]};
                }
            }
        return null;
    }
}
