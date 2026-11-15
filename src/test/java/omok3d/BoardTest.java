package omok3d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardTest {
    Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    /** Stacks `height` white stones in column (x, z) to build a support. */
    void fill(int x, int z, int height) {
        for (int i = 0; i < height; i++) board.place(x, z, 2);
    }

    // ---------- gravity ----------

    @Test
    void stonesStackFromTheBottomLayer() {
        assertEquals(0, board.place(3, 4, 1));
        assertEquals(1, board.place(3, 4, 2));
        assertEquals(2, board.place(3, 4, 1));
        assertEquals(1, board.get(3, 0, 4));
        assertEquals(2, board.get(3, 1, 4));
        assertEquals(1, board.get(3, 2, 4));
        assertEquals(0, board.get(3, 3, 4));
    }

    @Test
    void otherColumnsAreUnaffected() {
        board.place(3, 4, 1);
        assertEquals(0, board.dropY(4, 4));
        assertEquals(1, board.dropY(3, 4));
    }

    @Test
    void cannotPlaceOutOfRangeOrInFullColumn() {
        assertEquals(-1, board.place(-1, 0, 1));
        assertEquals(-1, board.place(0, Board.N, 1));
        for (int i = 0; i < Board.N; i++) assertEquals(i, board.place(0, 0, i % 2 + 1));
        assertEquals(-1, board.dropY(0, 0));
        assertEquals(-1, board.place(0, 0, 1));
    }

    @Test
    void removeTopUndoesTheLastStoneOfAColumn() {
        assertEquals(-1, board.removeTop(2, 2));
        board.place(2, 2, 1);
        board.place(2, 2, 2);
        assertEquals(1, board.removeTop(2, 2));
        assertEquals(0, board.get(2, 1, 2));
        assertEquals(1, board.get(2, 0, 2));
        assertEquals(1, board.dropY(2, 2));
    }

    @Test
    void removeTopWorksOnAFullColumn() {
        for (int i = 0; i < Board.N; i++) board.place(0, 0, 1);
        assertEquals(Board.N - 1, board.removeTop(0, 0));
        assertEquals(Board.N - 1, board.dropY(0, 0));
    }

    // ---------- winning ----------

    @Test
    void noStonesMeansNoWin() {
        assertNull(board.findWin(1));
        assertNull(board.findWin(2));
    }

    @Test
    void fiveInARowOnTheFloorWins() {
        for (int x = 0; x < 5; x++) board.place(x, 7, 1);
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertEquals(5, win.stones().size());
        assertArrayEquals(new int[]{1, 0, 0}, win.dir());
    }

    @Test
    void fiveInARowAlongDepthWins() {
        for (int z = 3; z < 8; z++) board.place(5, z, 1);
        assertNotNull(board.findWin(1));
    }

    @Test
    void fiveStackedInOneColumnWins() {
        for (int i = 0; i < 5; i++) board.place(9, 9, 1);
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertArrayEquals(new int[]{0, 1, 0}, win.dir());
    }

    @Test
    void floorDiagonalWins() {
        for (int i = 0; i < 5; i++) board.place(i, i, 1);
        assertNotNull(board.findWin(1));
    }

    @Test
    void verticalPlaneDiagonalWins() {
        for (int i = 0; i < 5; i++) {
            fill(i, 0, i);
            board.place(i, 0, 1);
        }
        assertNull(board.findWin(2));
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertArrayEquals(new int[]{1, 1, 0}, win.dir());
    }

    @Test
    void spaceDiagonalWins() {
        for (int i = 0; i < 5; i++) {
            fill(i, i, i);
            board.place(i, i, 1);
        }
        assertNull(board.findWin(2));
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertArrayEquals(new int[]{1, 1, 1}, win.dir());
    }

    @Test
    void projectionWithoutConnectionIsNotAWin() {
        // x is consecutive but heights are scattered: looks like a row from the front, but not connected
        int[] heights = {0, 3, 1, 4, 2};
        for (int i = 0; i < 5; i++) {
            fill(i, 0, heights[i]);
            board.place(i, 0, 1);
        }
        assertNull(board.findWin(1));
    }

    @Test
    void gapOrOpponentStoneBreaksTheLine() {
        board.place(0, 0, 1);
        board.place(1, 0, 1);
        board.place(2, 0, 2);
        board.place(3, 0, 1);
        board.place(4, 0, 1);
        assertNull(board.findWin(1));

        board.clear();
        for (int x : new int[]{0, 1, 3, 4, 5}) board.place(x, 0, 1);
        assertNull(board.findWin(1));
    }

    @Test
    void fourIsNotAWin() {
        for (int x = 0; x < 4; x++) board.place(x, 0, 1);
        assertNull(board.findWin(1));
    }

    @Test
    void lineDoesNotWrapAroundTheEdge() {
        for (int x : new int[]{17, 18, 0, 1, 2}) board.place(x, 0, 1);
        assertNull(board.findWin(1));
    }

    @Test
    void sixOrMoreStillWinsAndReturnsTheWholeLine() {
        for (int x = 0; x < 6; x++) board.place(x, 0, 1);
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertEquals(6, win.stones().size());
    }

    @Test
    void thereAreThirteenDirections() {
        assertEquals(13, Board.DIRS.length);
    }

    @Test
    void clearResetsTheBoard() {
        for (int x = 0; x < 5; x++) board.place(x, 0, 1);
        board.clear();
        assertNull(board.findWin(1));
        assertEquals(0, board.place(0, 0, 1));
    }
}
