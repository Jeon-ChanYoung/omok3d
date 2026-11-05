package omok3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardTest {
    Board b;

    @BeforeEach
    void setUp() {
        b = new Board();
    }

    @Test
    void 이미_돌이_있는_칸과_범위_밖에는_둘_수_없다() {
        assertTrue(b.place(1, 1, 1, 1));
        assertFalse(b.place(1, 1, 1, 2));
        assertFalse(b.place(-1, 0, 0, 1));
        assertFalse(b.place(0, 0, Board.N, 1));
    }

    @Test
    void 돌이_없으면_승리가_아니다() {
        assertNull(b.findWin(1));
        assertEquals(-1, b.checkWin(2));
    }

    @Test
    void 한_층에서_가로_5목이면_승리() {
        for (int x = 0; x < 5; x++) b.place(x, 4, 7, 1);
        Board.Win w = b.findWin(1);
        assertNotNull(w);
        assertEquals(5, w.cells().size());
    }

    @Test
    void 깊이가_달라도_앞에서_볼때_5목이면_승리() {
        // x는 연속, y는 같고, z는 제각각 -> z축으로 투영하면 가로 5목
        for (int i = 0; i < 5; i++) b.place(3 + i, 4, i * 3, 1);
        Board.Win w = b.findWin(1);
        assertNotNull(w);
        assertEquals(2, w.axis());
        assertEquals(5, w.stones().size());
    }

    @Test
    void 대각선_투영도_승리() {
        for (int i = 0; i < 5; i++) b.place(i, i, i, 1);
        assertNotNull(b.findWin(1));
    }

    @Test
    void 네_개는_승리가_아니다() {
        for (int x = 0; x < 4; x++) b.place(x, 0, 0, 1);
        assertNull(b.findWin(1));
    }

    @Test
    void 상대_돌은_투영에서_무시된다() {
        b.place(0, 0, 0, 1);
        b.place(1, 0, 0, 1);
        b.place(2, 0, 0, 2);   // 상대 돌이 중간에 있어도
        b.place(2, 0, 5, 1);   // 다른 층의 내 돌로 이어짐
        b.place(3, 0, 0, 1);
        b.place(4, 0, 0, 1);
        assertNotNull(b.findWin(1));
        assertNull(b.findWin(2));
    }

    @Test
    void 판_가장자리를_넘어_이어지는_줄은_승리가_아니다() {
        b.place(17, 0, 0, 1);
        b.place(18, 0, 0, 1);
        b.place(0, 0, 0, 1);
        b.place(1, 0, 0, 1);
        b.place(2, 0, 0, 1);
        assertNull(b.findWin(1));
    }

    @Test
    void clear하면_초기화된다() {
        for (int x = 0; x < 5; x++) b.place(x, 0, 0, 1);
        b.clear();
        assertNull(b.findWin(1));
        assertTrue(b.place(0, 0, 0, 1));
    }
}
