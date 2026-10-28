package com.gathin.tvhome;

import androidx.recyclerview.widget.DiffUtil;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Component identity survives insertion, removal and movement; dock crossings rebind position-dependent labels. */
final class CatalogueDiff {
    static <T> DiffUtil.DiffResult calculate(List<T> before, List<T> after,
                                            Function<T, String> identity, BiPredicate<T, T> sameContent) {
        return DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return before.size(); }
            @Override public int getNewListSize() { return after.size(); }
            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return identity.apply(before.get(oldPosition)).equals(identity.apply(after.get(newPosition)));
            }
            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return oldPosition == newPosition && sameContent.test(before.get(oldPosition), after.get(newPosition));
            }
        });
    }
    private CatalogueDiff() { }
}
