package com.pitchmap.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClusterGridShapeTest {

    private static final int[] SAMPLE_LENGTHS = {1, 43, 44, 100, 360, 740, 880, 1080, 1920, 4000, 10000};

    @ParameterizedTest(name = "{0}x{1}px -> {2}열 x {3}행")
    @CsvSource({
        "360, 740, 8, 16",
        "880, 820, 20, 18",
        "880, 880, 20, 20",
        "88, 132, 2, 3",
        "87, 131, 1, 2",
        "10000, 44, 227, 1",
        "44, 10000, 1, 227"
    })
    @DisplayName("[F-03] 화면을 44px로 나눈 몫이 열 수와 행 수이고, 칸은 44px 이상이다")
    void dividesScreenByMinimumCellSize(int widthPx, int heightPx, int columns, int rows) {
        // given, when
        ClusterGridShape shape = ClusterGridShape.forScreen(widthPx, heightPx);

        // then
        assertThat(shape.columns()).isEqualTo(columns);
        assertThat(shape.rows()).isEqualTo(rows);
    }

    @ParameterizedTest(name = "{0}x{1}px -> {2}열 x {3}행")
    @CsvSource({"44, 44, 1, 1", "43, 43, 1, 1", "1, 1, 1, 1", "10, 5000, 1, 113"})
    @DisplayName("[F-03] 화면이 44px보다 작아도 열과 행은 최소 1칸이다")
    void keepsAtLeastOneColumnAndRow(int widthPx, int heightPx, int columns, int rows) {
        // given, when
        ClusterGridShape shape = ClusterGridShape.forScreen(widthPx, heightPx);

        // then
        assertThat(shape.columns()).isEqualTo(columns);
        assertThat(shape.rows()).isEqualTo(rows);
    }

    @ParameterizedTest(name = "{0}x{1}px -> {2}열 x {3}행")
    @CsvSource({
        "880, 880, 20, 20",
        "924, 880, 20, 19",
        "1920, 1080, 26, 15",
        "1280, 720, 26, 15",
        "10000, 10000, 20, 20",
        "10000, 100, 200, 2",
        "100, 10000, 2, 200"
    })
    @DisplayName("[F-03] 화면을 44px로 나눈 칸이 400개를 넘으면 400칸 안에서 칸이 정사각형에 가깝게 다시 나눈다")
    void refitsToMaxCellsWhenScreenIsLarge(int widthPx, int heightPx, int columns, int rows) {
        // given, when
        ClusterGridShape shape = ClusterGridShape.forScreen(widthPx, heightPx);

        // then
        assertThat(shape.columns()).isEqualTo(columns);
        assertThat(shape.rows()).isEqualTo(rows);
    }

    @Test
    @DisplayName("[F-03] 어떤 화면 크기여도 전체 칸은 400개를 넘지 않고, 화면이 44px 이상이면 칸 한 변도 44px 이상이다")
    void neverExceedsMaxCellsAndKeepsCellsAtLeastMinimumSize() {
        // given, when, then
        for (int widthPx : SAMPLE_LENGTHS) {
            for (int heightPx : SAMPLE_LENGTHS) {
                ClusterGridShape shape = ClusterGridShape.forScreen(widthPx, heightPx);
                String screen = widthPx + "x" + heightPx;

                assertThat(shape.columns() * shape.rows()).as(screen).isLessThanOrEqualTo(ClusterGridShape.MAX_CELLS);
                if (widthPx >= ClusterGridShape.MIN_CELL_SIZE_PX) {
                    assertThat((double) widthPx / shape.columns())
                            .as(screen + " 칸 폭")
                            .isGreaterThanOrEqualTo(ClusterGridShape.MIN_CELL_SIZE_PX);
                }
                if (heightPx >= ClusterGridShape.MIN_CELL_SIZE_PX) {
                    assertThat((double) heightPx / shape.rows())
                            .as(screen + " 칸 높이")
                            .isGreaterThanOrEqualTo(ClusterGridShape.MIN_CELL_SIZE_PX);
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}x{1}px")
    @CsvSource({"0, 100", "100, 0", "-1, 100", "100, -1"})
    @DisplayName("화면 크기가 1px보다 작으면 IllegalArgumentException이다")
    void rejectsScreenSmallerThanOnePixel(int widthPx, int heightPx) {
        // given, when, then
        assertThatThrownBy(() -> ClusterGridShape.forScreen(widthPx, heightPx))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}열 x {1}행")
    @CsvSource({"0, 5", "5, 0", "21, 20", "401, 1"})
    @DisplayName("열이나 행이 1칸보다 적거나 전체가 400칸을 넘으면 IllegalArgumentException이다")
    void rejectsInvalidShape(int columns, int rows) {
        // given, when, then
        assertThatThrownBy(() -> new ClusterGridShape(columns, rows)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-03] 전체 칸이 정확히 400개인 모양은 허용한다")
    void acceptsShapeWithExactlyMaxCells() {
        // given, when
        ClusterGridShape shape = new ClusterGridShape(20, 20);

        // then
        assertThat(shape.columns() * shape.rows()).isEqualTo(ClusterGridShape.MAX_CELLS);
    }
}
