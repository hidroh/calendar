package io.github.hidroh.calendar.widget;

import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.CalendarContract;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.util.ActivityController;

import io.github.hidroh.calendar.CalendarUtils;
import io.github.hidroh.calendar.R;
import io.github.hidroh.calendar.test.shadows.ShadowLinearLayoutManager;
import io.github.hidroh.calendar.test.shadows.ShadowRecyclerView;

import static org.assertj.android.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Config(shadows = {ShadowRecyclerView.class, ShadowLinearLayoutManager.class})
@SuppressWarnings({"unchecked", "ConstantConditions"})
@RunWith(RobolectricGradleTestRunner.class)
public class AgendaViewTest {
    private ActivityController<TestActivity> controller;
    private TestActivity activity;
    private AgendaView agendaView;
    private AgendaAdapter adapter;
    private final long todayMillis = CalendarUtils.today();
    private final long lastDayMillis = todayMillis +
            DateUtils.DAY_IN_MILLIS * (AgendaAdapter.MONTH_SIZE - 1);
    private LinearLayoutManager layoutManager;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(TestActivity.class);
        activity = controller.create().start().resume().visible().get();
        agendaView = (AgendaView) activity.findViewById(R.id.agenda_view);
        adapter = (AgendaAdapter) agendaView.getAdapter();
        layoutManager = (LinearLayoutManager) agendaView.getLayoutManager();
    }

    @Test
    public void testInitialLayout() {
        // initial layout should have 1 block of 31 days (each with group + placeholder)
        assertThat(adapter.getItemCount()).isEqualTo(AgendaAdapter.MONTH_SIZE * 2);
        // first visible item should be today by default
        assertHasDate(createBindViewHolder(0), todayMillis);
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasText(R.string.no_event);
    }

    @Test
    public void testPrepend() {
        assertHasDate(createBindViewHolder(0), todayMillis);
        agendaView.smoothScrollToPosition(0);
        assertHasDate(createBindViewHolder(0), todayMillis -
                DateUtils.DAY_IN_MILLIS * AgendaAdapter.MONTH_SIZE);
    }

    @Test
    public void testAppend() {
        int lastPosition = adapter.getItemCount() - 1;
        createBindViewHolder(lastPosition);
        assertHasDate(createBindViewHolder(lastPosition - 1), lastDayMillis);
        ((ShadowRecyclerView) ShadowExtractor.extract(agendaView))
                .scrollToLastPosition();
        lastPosition = adapter.getItemCount() - 1;
        assertHasDate(createBindViewHolder(lastPosition - 1),
                lastDayMillis + DateUtils.DAY_IN_MILLIS * AgendaAdapter.MONTH_SIZE);
    }

    @Test
    public void testPruneUponPrepending() {
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
        agendaView.smoothScrollToPosition(0);
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
        agendaView.smoothScrollToPosition(0);
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
        agendaView.smoothScrollToPosition(0);
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
    }

    @Test
    public void testPruneUponAppending() {
        ShadowRecyclerView shadowAgendaView =
                (ShadowRecyclerView) ShadowExtractor.extract(agendaView);
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
        shadowAgendaView.scrollToLastPosition();
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
        shadowAgendaView.scrollToLastPosition();
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
        shadowAgendaView.scrollToLastPosition();
        assertThat(adapter.getItemCount()).isLessThanOrEqualTo(AgendaAdapter.MAX_SIZE * 2);
    }

    @Test
    public void testChangeSelectedDay() {
        long tomorrowMillis = todayMillis + DateUtils.DAY_IN_MILLIS;
        assertHasDate(createBindViewHolder(0), todayMillis);
        agendaView.setSelectedDay(tomorrowMillis);
        assertHasDate(createBindViewHolder(layoutManager.findFirstVisibleItemPosition()),
                tomorrowMillis);
    }

    @Test
    public void testPrependSelectedDay() {
        long beforeFirstDayMillis = todayMillis - DateUtils.DAY_IN_MILLIS;
        assertHasDate(createBindViewHolder(0), todayMillis);
        agendaView.setSelectedDay(beforeFirstDayMillis);
        assertHasDate(createBindViewHolder(layoutManager.findFirstVisibleItemPosition()),
                beforeFirstDayMillis);
        assertHasDate(createBindViewHolder(0), todayMillis -
                DateUtils.DAY_IN_MILLIS * AgendaAdapter.MONTH_SIZE);
    }

    @Test
    public void testAppendSelectedDay() {
        long afterLastDayMillis = lastDayMillis + DateUtils.DAY_IN_MILLIS;
        assertHasDate(createBindViewHolder(0), todayMillis);
        agendaView.setSelectedDay(afterLastDayMillis);
        assertHasDate(createBindViewHolder(layoutManager.findFirstVisibleItemPosition()),
                afterLastDayMillis);
        assertHasDate(createBindViewHolder(adapter.getItemCount() - 2),
                lastDayMillis + DateUtils.DAY_IN_MILLIS * AgendaAdapter.MONTH_SIZE);
    }

    @Test
    public void testDayChangeListener() {
        AgendaView.OnDateChangeListener listener = mock(AgendaView.OnDateChangeListener.class);
        agendaView.setOnDateChangeListener(listener);

        // set day programmatically should not trigger listener
        agendaView.setSelectedDay(todayMillis + DateUtils.DAY_IN_MILLIS);
        verify(listener, never()).onSelectedDayChange(anyLong());

        // set day via scrolling should trigger listener
        agendaView.smoothScrollToPosition(1);
        verify(listener).onSelectedDayChange(anyLong());

        // scroll to an item of same last selected date should not trigger listener
        agendaView.smoothScrollToPosition(1);
        verify(listener).onSelectedDayChange(anyLong());
    }

    @Test
    public void testBindEmptyCursor() {
        // initial state
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString(R.string.no_event);

        // bind empty cursor should not replace placeholder
        TestCursor cursor = new TestCursor();
        adapter.bindEvents(todayMillis, cursor);
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString(R.string.no_event);
    }

    @Test
    public void testBindCursor() {
        // initial state
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString(R.string.no_event);

        // trigger cursor loading and binding
        TestCursor cursor = new TestCursor();
        cursor.addRow(new Object[]{"Event 1", todayMillis + 1000});
        cursor.addRow(new Object[]{"Event 2", todayMillis + 2000});
        activity.cursors.put(todayMillis, cursor);
        createBindViewHolder(0);

        // non empty cursor should replace placeholder and add extra item
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString("Event 1");
        assertThat((TextView) createBindViewHolder(2).itemView)
                .hasTextString("Event 2");

        // deactivate adapter should close cursor
        assertThat(cursor).isNotClosed();
        adapter.deactivate();
        assertThat(cursor).isClosed();
    }

    @Test
    public void testCursorContentChange() {
        // initial state
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString(R.string.no_event);

        // trigger cursor loading and binding
        TestCursor noEventCursor = new TestCursor();
        activity.cursors.put(todayMillis, noEventCursor);
        createBindViewHolder(0);

        // bind empty cursor should not replace placeholder
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString(R.string.no_event);

        // trigger content change notification
        TestCursor multiEventCursor = new TestCursor();
        multiEventCursor.addRow(new Object[]{"Event 1", todayMillis + 1000});
        multiEventCursor.addRow(new Object[]{"Event 2", todayMillis + 2000});
        activity.cursors.put(todayMillis, multiEventCursor);
        noEventCursor.notifyContentChange(false);

        // content change should deactivate prev cursor, update placeholder and add extra item
        assertThat(noEventCursor).isClosed();
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString("Event 1");
        assertThat((TextView) createBindViewHolder(2).itemView)
                .hasTextString("Event 2");

        // trigger content change notification
        TestCursor singleEventCursor = new TestCursor();
        singleEventCursor.addRow(new Object[]{"Event 3", todayMillis + 3000});
        activity.cursors.put(todayMillis, singleEventCursor);
        multiEventCursor.notifyContentChange(false);

        // content change should deactivate prev cursor, update existing item, remove deleted item
        assertThat(multiEventCursor).isClosed();
        assertThat((TextView) createBindViewHolder(1).itemView)
                .hasTextString("Event 3");
        assertHasDate(createBindViewHolder(2), todayMillis + DateUtils.DAY_IN_MILLIS);
    }

    @Test
    public void testStateRestoration() {
        agendaView.smoothScrollToPosition(0);
        int expected = AgendaAdapter.MONTH_SIZE * 2 * 2; // prepended
        assertThat(adapter.getItemCount()).isEqualTo(expected);
        Parcelable savedState = agendaView.onSaveInstanceState();
        agendaView.onRestoreInstanceState(savedState);
        AgendaAdapter newAdapter = new AgendaAdapter(activity) { };
        agendaView.setAdapter(newAdapter);
        assertThat(newAdapter.getItemCount()).isEqualTo(expected);
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    private void assertHasDate(RecyclerView.ViewHolder viewHolder, long timeMillis) {
        assertThat((TextView) viewHolder.itemView)
                .hasTextString(CalendarUtils.toDayString(RuntimeEnvironment.application, timeMillis));
    }

    private RecyclerView.ViewHolder createBindViewHolder(int position) {
        RecyclerView.ViewHolder viewHolder = adapter.createViewHolder(agendaView,
                adapter.getItemViewType(position));
        adapter.bindViewHolder((AgendaAdapter.RowViewHolder) viewHolder, position);
        return viewHolder;
    }

    static class TestActivity extends AppCompatActivity {
        LongSparseArray<Cursor> cursors = new LongSparseArray<>();

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            AgendaView agendaView = new AgendaView(this);
            agendaView.setId(R.id.agenda_view);
            agendaView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setContentView(agendaView);
            agendaView.setAdapter(new AgendaAdapter(this) {
                @Override
                protected void loadEvents(long timeMillis) {
                    bindEvents(timeMillis, cursors.get(timeMillis) != null ?
                            cursors.get(timeMillis) : new TestCursor());
                }
            });
        }
    }

    static class TestCursor extends MatrixCursor {
        private ContentObserver contentObserver;

        public TestCursor() {
            super(new String[]{
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART});
        }

        @Override
        public void registerContentObserver(ContentObserver observer) {
            contentObserver = observer;
        }

        @Override
        public void unregisterContentObserver(ContentObserver observer) {
            contentObserver = null;
        }

        public void notifyContentChange(boolean selfChange) {
            if (contentObserver != null) {
                contentObserver.onChange(selfChange);
            }
        }
    }
}
