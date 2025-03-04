package com.attica.athens.global.handler;

import com.attica.athens.domain.agoraMember.application.AgoraMemberService;
import com.attica.athens.domain.agoraMember.domain.AgoraMember;
import com.attica.athens.domain.agoraMember.exception.NotFoundSessionException;
import com.attica.athens.global.auth.application.AuthService;
import com.attica.athens.global.auth.domain.CustomUserDetails;
import com.attica.athens.global.auth.exception.InvalidAuthorizationHeaderException;
import com.attica.athens.global.decorator.HeartBeatManager;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventHandler {

    private static final Integer AGORA_ID_INDEX = 2;

    private final HeartBeatManager heartBeatManager;
    private final AgoraMemberService agoraMemberService;
    private final AuthService authService;
    private final ClientConnectionRegistry clientRegistry;

    @EventListener
    public void handleWebSocketSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        HeaderInfo authentication = (HeaderInfo) accessor.getUser();
        if (authentication == null) {
            log.warn("Unauthenticated connection attempt");
            return;
        }
        String memberName = authentication.getName();
        String memberRole = getMemberRole(authentication);

        log.info("WebSocket Connect: memberName={}, memberRole={}", memberName, memberRole);
    }

    @EventListener
    public void handleWebSocketSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        HeaderInfo header = getHeader(accessor);
        Long memberId = getUserId(header);

        if (!validateHeaders(header, memberId)) {
            return;
        }

        Long agoraId = header.agoraId();
        String sessionId = getSessionId(accessor);
        String accessToken = header.token();

        try {
            authService.verifyToken(accessToken);
        } catch (Exception e) {
            log.error("Token verification failed: {}", e.getMessage(), e);
            return;
        }

        AgoraMember agoraMember = agoraMemberService.findAgoraMemberByAgoraIdAndMemberId(agoraId, memberId);
        handleConnectionLogic(agoraMember, agoraId, memberId, sessionId);
    }

    @EventListener
    public void handleWebSocketSessionDisconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        try {
            Long agoraId = agoraMemberService.findAgoraIdBySessionId(sessionId);
            StompHeaderAccessor.wrap(event.getMessage()).getUser();
            Long memberId = getUserId((Authentication) Objects.requireNonNull(
                    StompHeaderAccessor.wrap(event.getMessage()).getUser()));

            processDisconnection(sessionId, agoraId, memberId);
            clientRegistry.removeClient(sessionId, agoraId);
        } catch (NotFoundSessionException e) {
            log.info("Session already removed: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Error handling session disconnect: sessionId={}", sessionId, e);
        }
    }

    @EventListener(SessionSubscribeEvent.class)
    public void handleWebSocketSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        HeaderInfo header = getHeader(headerAccessor);
        String destination = headerAccessor.getDestination();

        if (destination != null && destination.matches("/exchange/chat.exchange/agoras\\.\\d+.*")) {
            String sessionId = headerAccessor.getSessionId();
            Long agoraId = extractAgoraIdFromDestination(destination);
            Long memberId = getUserId(header);

            heartBeatManager.handleHeartbeat(sessionId);
            agoraMemberService.sendMetaToActiveMembers(agoraId, memberId);

            clientRegistry.registerSubscription(sessionId, agoraId);
        }
    }

    @EventListener(SessionUnsubscribeEvent.class)
    public void handleWebSocketSessionUnsubscribe(SessionUnsubscribeEvent event) {
        log.info("WebSocket Unsubscribe");
    }

    private void handleConnectionLogic(AgoraMember agoraMember, Long agoraId, Long memberId, String sessionId) {
        if (agoraMember.getSessionId() == null) {
            handleNewConnection(agoraId, memberId, sessionId);
        } else if (!agoraMember.getSessionId().equals(sessionId)) {
            handleExistingConnection(agoraMember, agoraId, memberId, sessionId);
        }
    }

    private void handleNewConnection(Long agoraId, Long userId, String sessionId) {
        log.info("Starting new connection: agoraId={}, userId={}, sessionId={}", agoraId, userId, sessionId);
        try {
            agoraMemberService.updateSessionId(agoraId, userId, sessionId);
        } catch (Exception e) {
            log.error("Error in new connection: agoraId={}, userId={}, sessionId={}, error={}", agoraId, userId,
                    sessionId, e.getMessage(), e);
            throw e;
        }
        log.info("New connection completed: agoraId={}, userId={}, sessionId={}", agoraId, userId, sessionId);
    }

    private void handleExistingConnection(AgoraMember agoraMember, Long agoraId, Long memberId,
                                          String sessionId) {
        if (heartBeatManager.isReconnectValid(agoraMember.getSessionId())) {
            heartBeatManager.removeSession(agoraMember.getSessionId());
            reconnectAgoraMember(agoraMember, agoraId, memberId, sessionId);
        } else {
            processDisconnection(agoraMember.getSessionId(), agoraId, memberId);
            heartBeatManager.removeSession(agoraMember.getSessionId());
        }
    }

    @Transactional
    public void reconnectAgoraMember(AgoraMember agoraMember, Long agoraId, Long memberId, String sessionId) {
        agoraMember.updateDisconnectType(false);
        handleNewConnection(agoraId, memberId, sessionId);
        heartBeatManager.handleHeartbeat(sessionId);
    }

    @Transactional
    public void processDisconnection(String sessionId, Long agoraId, Long memberId) {
        try {
            agoraMemberService.removeSessionId(sessionId);
            agoraMemberService.sendMetaToActiveMembers(agoraId, memberId);
            log.info("WebSocket Disconnected: agoraId={}, userId={}", agoraId, memberId);
        } catch (Exception e) {
            log.error("Error during disconnection: agoraId={}, userId={}, error={}", agoraId, memberId, e.getMessage(),
                    e);
            throw e;
        }
    }

    private String getMemberRole(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElseThrow(() -> new IllegalArgumentException("Member role not found."));
    }

    private Long getUserId(Authentication authentication) {
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        return customUserDetails.getUserId();
    }

    private HeaderInfo getHeader(StompHeaderAccessor accessor) {
        return (HeaderInfo) accessor.getUser();
    }

    private String getSessionId(StompHeaderAccessor accessor) {
        return accessor.getSessionId();
    }

    private boolean validateHeaders(HeaderInfo header, Long memberId) {
        if (memberId == null) {
            log.warn("User is not present in headers");
            return false;
        }

        if (header.agoraId() == null) {
            log.warn("AgoraId is not present in headers");
            return false;
        }
        return true;
    }

    private Long extractAgoraIdFromDestination(String destination) {
        String[] parts = destination.split("\\.");
        return Long.parseLong(parts[AGORA_ID_INDEX]);
    }
}
