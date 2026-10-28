package com.gathin.tvhome;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CatalogueDiffTest {
    private DiffUtil.DiffResult diff(List<String> a, List<String> b) {
        return CatalogueDiff.calculate(a, b, s -> s.split(":")[0], String::equals);
    }
    @Test public void insertAndRemoveRetainFocusedComponentMapping() {
        DiffUtil.DiffResult change = diff(Arrays.asList("netflix:a", "prime:a", "tv:a"),
                Arrays.asList("new:a", "prime:a", "tv:a"));
        assertEquals(DiffUtil.DiffResult.NO_POSITION, change.convertOldPositionToNew(0));
        assertEquals(1, change.convertOldPositionToNew(1));
        assertEquals(2, change.convertOldPositionToNew(2));
    }
    @Test public void reorderRetainsIdentityButRebindsDockCrossings() {
        DiffUtil.DiffResult change = diff(Arrays.asList("a:label", "b:label"), Arrays.asList("b:label", "a:label"));
        assertEquals(1, change.convertOldPositionToNew(0));
        assertEquals(0, change.convertOldPositionToNew(1));
        Changes events = new Changes();
        change.dispatchUpdatesTo(events);
        assertTrue(events.changed > 0);
        assertTrue(events.moves > 0);
    }
    @Test public void equivalentSnapshotDoesNotRefreshAnyView() {
        Changes events = new Changes();
        diff(Arrays.asList("a:label", "b:label"), Arrays.asList("a:label", "b:label")).dispatchUpdatesTo(events);
        assertEquals(0, events.changed + events.moves + events.inserted + events.removed);
    }
    @Test public void labelOrModuleCaptionChangeRebindsOnlyAffectedItem() {
        Changes events = new Changes();
        diff(Arrays.asList("a:old", "b:label"), Arrays.asList("a:new", "b:label")).dispatchUpdatesTo(events);
        assertEquals(1, events.changed);
        assertEquals(0, events.moves + events.inserted + events.removed);
    }
    private static final class Changes implements ListUpdateCallback {
        int changed, moves, inserted, removed;
        @Override public void onInserted(int position, int count) { inserted += count; }
        @Override public void onRemoved(int position, int count) { removed += count; }
        @Override public void onMoved(int from, int to) { moves++; }
        @Override public void onChanged(int position, int count, Object payload) { changed += count; }
    }
}
