package com.pitchmap.spot.domain;

/**
 * 지도 화면 영역을 묶음 칸으로 나눌 때의 열(가로) 수와 행(세로) 수.
 *
 * <p>화면에는 묶음을 지름 {@value #MIN_CELL_SIZE_PX}px 원으로 그린다. 칸이 이보다 작으면 이웃한 묶음 표시가 서로 겹친다. 그래서 칸 한 변이
 * {@value #MIN_CELL_SIZE_PX}px 이상이 되도록 화면 크기(px)로 열 수와 행 수를 따로 정한다. 전체 칸 수는 {@value #MAX_CELLS}개를 넘지 않는다. 묶음
 * 하나가 칸 하나라서, 한 응답에 담는 마커와 묶음의 상한을 넘지 않게 하려는 값이다.
 */
public record ClusterGridShape(int columns, int rows) {

    public static final int MAX_CELLS = 400;
    public static final int MIN_CELL_SIZE_PX = 44;

    public ClusterGridShape {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("열 수와 행 수는 1 이상이어야 합니다. columns=" + columns + ", rows=" + rows);
        }
        if ((long) columns * rows > MAX_CELLS) {
            throw new IllegalArgumentException(
                    "전체 칸 수는 " + MAX_CELLS + "개를 넘을 수 없습니다. columns=" + columns + ", rows=" + rows);
        }
    }

    /**
     * 호출하면 가로 widthPx, 세로 heightPx(px) 크기의 화면에 맞는 열 수와 행 수를 돌려준다.
     *
     * <p>열 수는 ⌊widthPx ÷ {@value #MIN_CELL_SIZE_PX}⌋, 행 수는 ⌊heightPx ÷ {@value #MIN_CELL_SIZE_PX}⌋이고 둘 다 최소 1이다. 곱이
     * {@value #MAX_CELLS}을 넘는 큰 화면에서는 칸이 정사각형에 가깝도록 {@value #MAX_CELLS}칸 안에서 다시 나눈다. 이때는 칸이
     * {@value #MIN_CELL_SIZE_PX}px보다 커진다.
     *
     * @param widthPx 화면 가로 길이(px). 1 이상
     * @param heightPx 화면 세로 길이(px). 1 이상
     */
    public static ClusterGridShape forScreen(int widthPx, int heightPx) {
        if (widthPx < 1 || heightPx < 1) {
            throw new IllegalArgumentException("화면 크기는 1px 이상이어야 합니다. widthPx=" + widthPx + ", heightPx=" + heightPx);
        }
        int columns = Math.max(1, widthPx / MIN_CELL_SIZE_PX);
        int rows = Math.max(1, heightPx / MIN_CELL_SIZE_PX);
        if ((long) columns * rows <= MAX_CELLS) {
            return new ClusterGridShape(columns, rows);
        }
        return fitToMaxCells((double) widthPx / heightPx);
    }

    // 칸이 정사각형이고 전체 칸이 MAX_CELLS개라면 열 수는 √(MAX_CELLS × 비율), 행 수는 √(MAX_CELLS ÷ 비율)이다.
    // 칸 수는 정수여야 해서 둘 다 내림하면 곱이 상한을 넘지 않는다. 비율이 아주 크거나 작아서 한쪽이 1칸으로 올라가면,
    // 곱이 상한을 넘지 않도록 반대쪽을 줄인다.
    private static ClusterGridShape fitToMaxCells(double aspectRatio) {
        int columns = clamp(Math.floor(Math.sqrt(MAX_CELLS * aspectRatio)), MAX_CELLS);
        int rows = clamp(Math.floor(Math.sqrt(MAX_CELLS / aspectRatio)), MAX_CELLS / columns);
        return new ClusterGridShape(columns, rows);
    }

    private static int clamp(double cells, int max) {
        return (int) Math.max(1, Math.min(max, cells));
    }
}
