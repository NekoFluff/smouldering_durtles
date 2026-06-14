package com.smouldering_durtles.wk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.smouldering_durtles.wk.db.model.Subject;
import com.smouldering_durtles.wk.db.model.SubjectEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the picker queue management logic in Session.
 *
 * Uses reflection to access the private pendingPickerQueue field so we can
 * drive state without needing a database (startNextPickerBatch itself needs
 * the DB, but hasPendingPickerQueue and the slice-and-clear pattern do not).
 *
 * Subject is a final class so we construct real instances via SubjectEntity
 * rather than using mocks.
 */
@RunWith(RobolectricTestRunner.class)
public class SessionPickerQueueTest {

    private Session session;
    private List<Subject> queue;

    private static Subject makeSubject() {
        return new Subject(new SubjectEntity());
    }

    @Before
    public void setUp() throws Exception {
        session = Session.getInstance();
        Field f = Session.class.getDeclaredField("pendingPickerQueue");
        f.setAccessible(true);
        //noinspection unchecked
        queue = (List<Subject>) f.get(session);
        queue.clear();
    }

    @Test
    public void hasPendingPickerQueue_emptyQueue_returnsFalse() {
        assertFalse(session.hasPendingPickerQueue());
    }

    @Test
    public void hasPendingPickerQueue_nonEmptyQueue_returnsTrue() {
        queue.add(makeSubject());
        assertTrue(session.hasPendingPickerQueue());
    }

    @Test
    public void batchSlice_removesFirstN_leavesRemainder() {
        queue.add(makeSubject());
        queue.add(makeSubject());
        queue.add(makeSubject());

        int batchSize = 2;
        int end = Math.min(batchSize, queue.size());
        List<Subject> batch = new ArrayList<>(queue.subList(0, end));
        queue.subList(0, end).clear();

        assertEquals(2, batch.size());
        assertEquals(1, queue.size());
        assertTrue(session.hasPendingPickerQueue());
    }

    @Test
    public void batchSlice_drainsFully_queueBecomesEmpty() {
        queue.add(makeSubject());

        queue.subList(0, 1).clear();

        assertFalse(session.hasPendingPickerQueue());
    }

    @Test
    public void batchSlice_batchLargerThanQueue_takesAll() {
        // If fewer items remain than the batch limit, all are taken without crash
        queue.add(makeSubject());
        queue.add(makeSubject());

        int batchSize = 5;
        int end = Math.min(batchSize, queue.size());
        List<Subject> batch = new ArrayList<>(queue.subList(0, end));
        queue.subList(0, end).clear();

        assertEquals(2, batch.size());
        assertFalse(session.hasPendingPickerQueue());
    }

    @Test
    public void multipleSlices_correctlyDrainsInBatches() {
        // Simulate three rounds of startNextPickerBatch with batchSize=2 on 5 items
        for (int i = 0; i < 5; i++) queue.add(makeSubject());

        int batchSize = 2;
        int[] expectedBatchSizes = {2, 2, 1};

        for (int expected : expectedBatchSizes) {
            assertTrue(session.hasPendingPickerQueue());
            int end = Math.min(batchSize, queue.size());
            List<Subject> batch = new ArrayList<>(queue.subList(0, end));
            queue.subList(0, end).clear();
            assertEquals(expected, batch.size());
        }

        assertFalse(session.hasPendingPickerQueue());
    }
}
