package com.gathin.tvhome;

/** Remote focus movement in the six-column library; vertical moves remember the desired column. */
final class LibraryGrid {
    static final int COLUMNS = 6;
    static final int LEFT = -1, RIGHT = 1, UP = -2, DOWN = 2;

    private LibraryGrid() { }

    static int next(int current, int count, int direction, boolean rtl, int preferredColumn) {
        if (count <= 0) return -1;
        current = Math.max(0, Math.min(current, count - 1));
        int row = current / COLUMNS, column = current % COLUMNS;
        if (direction == LEFT || direction == RIGHT) {
            int step = rtl ? -direction : direction;
            int nextColumn = column + step;
            int candidate = current + step;
            return nextColumn < 0 || nextColumn >= COLUMNS || candidate >= count ? current : candidate;
        }
        if (direction == UP || direction == DOWN) {
            int nextRow = row + (direction == UP ? -1 : 1);
            if (nextRow < 0 || nextRow * COLUMNS >= count) return current;
            int desired = preferredColumn >= 0 && preferredColumn < COLUMNS ? preferredColumn : column;
            return Math.min(nextRow * COLUMNS + desired, count - 1);
        }
        return current;
    }
}
