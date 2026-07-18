package com.jolumn.livemallwebsocket.manager;

import com.jolumn.livemallwebsocket.model.WsSession;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WsSessionManagerTest {

    private WsSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new WsSessionManager();
    }

    // ── add / remove ──

    @Test
    void add_shouldTrackSession() {
        WsSession ws = mockSession(1L, 100L);
        manager.add(ws);
        assertThat(manager.get(ws.getSessionId())).isSameAs(ws);
        assertThat(manager.totalOnline()).isEqualTo(1);
    }

    @Test
    void add_shouldTrackRoomSessions() {
        WsSession ws1 = mockSession(1L, 100L);
        WsSession ws2 = mockSession(1L, 200L);
        manager.add(ws1);
        manager.add(ws2);

        assertThat(manager.getRoomOnline(1L)).isEqualTo(2);
        Collection<WsSession> room = manager.getRoomSessions(1L);
        assertThat(room).containsExactlyInAnyOrder(ws1, ws2);
    }

    @Test
    void remove_shouldCleanupBothMaps() {
        WsSession ws = mockSession(1L, 100L);
        manager.add(ws);
        String id = ws.getSessionId();

        WsSession removed = manager.remove(id);
        assertThat(removed).isSameAs(ws);
        assertThat(manager.get(id)).isNull();
        assertThat(manager.totalOnline()).isEqualTo(0);
        assertThat(manager.getRoomOnline(1L)).isEqualTo(0);
    }

    @Test
    void remove_shouldCleanupEmptyRoom() {
        WsSession ws = mockSession(1L, 100L);
        manager.add(ws);

        manager.remove(ws.getSessionId());
        assertThat(manager.getRoomSessions(1L)).isEmpty();
    }

    @Test
    void remove_nonExistent_returnsNull() {
        assertThat(manager.remove("ghost")).isNull();
    }

    // ── getRoomSessions ──

    @Test
    void getRoomSessions_nonExistentRoom_returnsEmpty() {
        assertThat(manager.getRoomSessions(99L)).isEmpty();
    }

    @Test
    void getRoomOnline_shouldCountCorrectly() {
        manager.add(mockSession(1L, null));
        manager.add(mockSession(1L, null));
        manager.add(mockSession(2L, null));

        assertThat(manager.getRoomOnline(1L)).isEqualTo(2);
        assertThat(manager.getRoomOnline(2L)).isEqualTo(1);
        assertThat(manager.getRoomOnline(99L)).isEqualTo(0);
    }

    // ── findByUserId ──

    @Test
    void findByUserId_shouldFind() {
        WsSession ws = mockSession(1L, 100L);
        manager.add(ws);

        assertThat(manager.findByUserId(100L)).isSameAs(ws);
    }

    @Test
    void findByUserId_notFound_returnsNull() {
        assertThat(manager.findByUserId(999L)).isNull();
    }

    @Test
    void findByUserId_anonymousSkipped() {
        WsSession anon = mockSession(1L, null);
        manager.add(anon);

        assertThat(manager.findByUserId(100L)).isNull();
    }

    // ── totalOnline ──

    @Test
    void totalOnline_shouldTrackChanges() {
        assertThat(manager.totalOnline()).isEqualTo(0);

        WsSession ws1 = mockSession(1L, 100L);
        WsSession ws2 = mockSession(1L, 200L);
        manager.add(ws1);
        manager.add(ws2);
        assertThat(manager.totalOnline()).isEqualTo(2);

        manager.remove(ws1.getSessionId());
        assertThat(manager.totalOnline()).isEqualTo(1);
    }

    // ── 并发安全 ──

    @Test
    void concurrentAdd_shouldNotLoseSessions() throws InterruptedException {
        int threads = 10;
        int perThread = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger counter = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            int base = t * perThread;
            Thread.startVirtualThread(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        int id = base + i;
                        manager.add(mockSession((long) (id % 5), (long) id));
                        counter.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertThat(manager.totalOnline()).isEqualTo(counter.get());
    }

    @Test
    void concurrentRemove_shouldNotCorrupt() throws InterruptedException {
        int count = 100;
        List<WsSession> sessions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WsSession ws = mockSession(1L, (long) i);
            sessions.add(ws);
            manager.add(ws);
        }
        assertThat(manager.totalOnline()).isEqualTo(count);

        Collections.shuffle(sessions);
        CountDownLatch latch = new CountDownLatch(count);
        for (WsSession ws : sessions) {
            String sid = ws.getSessionId();
            Thread.startVirtualThread(() -> {
                try {
                    manager.remove(sid);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertThat(manager.totalOnline()).isEqualTo(0);
        assertThat(manager.getRoomOnline(1L)).isEqualTo(0);
    }

    // ── helpers ──

    private static WsSession mockSession(Long roomId, Long userId) {
        Session raw = mock(Session.class, withSettings().lenient());
        when(raw.isOpen()).thenReturn(true);

        RemoteEndpoint.Async async = mock(RemoteEndpoint.Async.class, withSettings().lenient());
        when(async.sendText(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(raw.getAsyncRemote()).thenReturn(async);

        return new WsSession(raw, roomId, userId, userId != null ? 1 : null);
    }
}
