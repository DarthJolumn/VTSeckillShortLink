package com.jolumn.vtslwebsocket.push;

import com.jolumn.vtslwebsocket.manager.WsSessionManager;
import com.jolumn.vtslwebsocket.model.WsSession;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WsPushServiceImplTest {

    @Mock
    private WsSessionManager sessionManager;

    private WsPushServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WsPushServiceImpl(sessionManager);
    }

    // ── push ──

    @Test
    void push_userFound_shouldSend() {
        WsSession ws = mockSession("s1", 1L, 100L);
        when(sessionManager.findByUserId(100L)).thenReturn(List.of(ws));

        boolean result = service.push(100L, "hello");

        assert result;
        verify(sessionManager).findByUserId(100L);
        // sendRaw 内调了 getAsyncRemote().sendText()，验证 session 被使用
        verify(ws.getSession().getAsyncRemote()).sendText("hello");
    }

    @Test
    void push_userNotFound_returnsFalse() {
        when(sessionManager.findByUserId(999L)).thenReturn(List.of());

        boolean result = service.push(999L, "hello");

        assert !result;
    }

    // ── pushToRoom ──

    @Test
    void pushToRoom_emptyRoom_returnsFalse() {
        when(sessionManager.getRoomSessions(1L)).thenReturn(Set.of());

        boolean result = service.pushToRoom(1L, "broadcast");

        assert !result;
    }

    @Test
    void pushToRoom_withSessions_returnsTrue() throws InterruptedException {
        WsSession ws1 = mockSession("s1", 1L, 100L);
        WsSession ws2 = mockSession("s2", 1L, 200L);
        when(sessionManager.getRoomSessions(1L)).thenReturn(List.of(ws1, ws2));

        boolean result = service.pushToRoom(1L, "broadcast");

        assert result;
        // VT 异步发送，等待一小段时间让 VT 完成
        Thread.sleep(200);
        verify(ws1.getSession().getAsyncRemote()).sendText("broadcast");
        verify(ws2.getSession().getAsyncRemote()).sendText("broadcast");
    }

    // ── kickUser ──

    @Test
    void kickUser_userFound_shouldSendKickMessage() {
        WsSession ws = mockSession("s1", 1L, 100L);
        when(sessionManager.findByUserId(100L)).thenReturn(List.of(ws));

        boolean result = service.kickUser(100L, "违规操作");

        assert result;
        // KICK 消息通过 sendRaw 发送
        verify(ws.getSession().getAsyncRemote()).sendText(anyString());
    }

    @Test
    void kickUser_userNotFound_returnsFalse() {
        when(sessionManager.findByUserId(999L)).thenReturn(List.of());

        boolean result = service.kickUser(999L, "reason");

        assert !result;
    }

    // ── kickDevice ──

    @Test
    void kickDevice_kicksAllSessionsForDevice() {
        WsSession ws = mockSession("s1", 1L, 100L);
        when(sessionManager.findByUserIdAndDeviceId(100L, "device-1")).thenReturn(List.of(ws));

        boolean result = service.kickDevice(100L, "device-1");

        assert result;
        verify(ws.getSession().getAsyncRemote()).sendText(anyString());
    }

    // ── helpers ──

    private static WsSession mockSession(String sessionId, Long roomId, Long userId) {
        Session raw = mock(Session.class, withSettings().lenient());
        when(raw.getId()).thenReturn(sessionId);
        when(raw.isOpen()).thenReturn(true);

        RemoteEndpoint.Async async = mock(RemoteEndpoint.Async.class, withSettings().lenient());
        when(async.sendText(anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(raw.getAsyncRemote()).thenReturn(async);

        return new WsSession(raw, roomId, userId, userId != null ? 1 : null, "test-device");
    }
}
