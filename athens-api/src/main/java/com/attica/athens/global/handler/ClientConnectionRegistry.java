package com.attica.athens.global.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClientConnectionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ClientConnectionRegistry.class);

    private final Map<Long, Integer> clientSubscriptions = new ConcurrentHashMap<>();

    public int getCount(Long agoraId) {
        return clientSubscriptions.get(agoraId);
    }

    // 클라이언트가 특정 agora에 구독할 때 호출
    public void registerSubscription(String sessionId, Long agoraId) {
        logger.info("Client {} subscribing to agora {}", sessionId, agoraId);

        // 세션별 구독 agoraId 추가
        clientSubscriptions.put(agoraId, clientSubscriptions.getOrDefault(agoraId, 0) + 1);

        logger.info("Current subscribers for agora {}: {}", agoraId, clientSubscriptions.get(agoraId));
    }

    public void removeClient(String sessionId, Long agoraId) {
        logger.info("Client disconnected: {}", sessionId);

        if (clientSubscriptions.containsKey(agoraId) && clientSubscriptions.get(agoraId) > 0) {
            clientSubscriptions.put(agoraId, clientSubscriptions.get(agoraId) - 1);
            logger.info("Client {} not subscribed to agora {}", sessionId, agoraId);
            logger.info("Remaining clients for agora {}: {}", agoraId, clientSubscriptions.get(agoraId));
        }
    }

    public boolean hasSubscribedClientsForAgora(Long agoraId) {
        return clientSubscriptions.containsKey(agoraId) && clientSubscriptions.get(agoraId) > 0;
    }
}
