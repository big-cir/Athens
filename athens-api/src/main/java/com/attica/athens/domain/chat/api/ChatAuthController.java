package com.attica.athens.domain.chat.api;

import com.attica.athens.domain.chat.application.ChatCommandService;
import com.attica.athens.domain.chat.domain.ChatType;
import com.attica.athens.domain.chat.dto.request.SendChatRequest;
import com.attica.athens.domain.chat.dto.request.SendReactionRequest;
import com.attica.athens.domain.chat.dto.response.BadWordResponse;
import com.attica.athens.domain.chat.dto.response.SendChatResponse;
import com.attica.athens.domain.chat.dto.response.SendReactionResponse;
import com.attica.athens.domain.common.ApiResponse;
import com.attica.athens.domain.common.ApiUtil;
import com.attica.athens.global.auth.domain.CustomUserDetails;
import com.attica.athens.global.handler.ClientConnectionRegistry;
import com.rabbitmq.client.Channel;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class ChatAuthController {

    private final ChatCommandService chatCommandService;
    private final RabbitTemplate rabbitTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ClientConnectionRegistry clientConnectionRegistry;

    private static final Logger logger = LoggerFactory.getLogger(ChatAuthController.class);

    @Transactional
    @MessageMapping("/agoras.{agoraId}.chats")
    public void sendChat(
            @DestinationVariable("agoraId") Long agoraId,
            @Payload @Valid SendChatRequest sendChatRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        headerAccessor.getSessionAttributes().put("AgoraId", agoraId);
        SendChatResponse response = chatCommandService.sendChat(userDetails, agoraId, sendChatRequest);
        rabbitTemplate.convertAndSend("chat.exchange", "agoras." + agoraId + ".chats", response);
    }

    @RabbitListener(
            queues = "chat.queue",
            ackMode = "MANUAL",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleChatMessage(
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Payload SendChatResponse response
    ) throws IOException {
        if (response.type().equals(ChatType.META)) {
            channel.basicAck(tag, false);
            return;
        }

        try {
            if (clientConnectionRegistry.hasSubscribedClientsForAgora(response.agoraId())) {
                logger.info("Message delivered and acknowledged: {}", tag);
                messagingTemplate.convertAndSend("/topic/agoras." + response.agoraId() + ".chats", response);
                channel.basicAck(tag, false);
            } else {
                channel.basicNack(tag, true, false);
                logger.info("No clients connected for agoraId: {}. queue listener: {}", response.agoraId(), tag);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage(), e);
            channel.basicNack(tag, true, false);
        }
    }

    @RabbitListener(
            queues = "chat.dlq",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlqMessage(
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Payload SendChatResponse response
    ) throws IOException {
        if (response.type().equals(ChatType.META)) {
            channel.basicAck(tag, false);
        } else if (!clientConnectionRegistry.hasSubscribedClientsForAgora(response.agoraId())) {
            logger.info("No clients connected for agoraId: {}. Will retry...", response.agoraId());
            throw new AmqpRejectAndDontRequeueException("No clients connected, triggering retry");
        } else {
            logger.info("DLQ message delivered and acknowledged: {}", tag);
            messagingTemplate.convertAndSend("/topic/agoras." + response.agoraId() + ".chats", response);
            rabbitTemplate.convertAndSend("chat.exchange", "agoras." + response.agoraId() + ".chats", response);
            channel.basicAck(tag, false);
        }
    }

    @MessageMapping("/agoras/{agoraId}/chats/{chatId}/reactions")
    @SendTo(value = "/topic/agoras/{agoraId}/reactions")
    public SendReactionResponse sendReaction(
            @DestinationVariable("agoraId") Long agoraId,
            @DestinationVariable("chatId") Long chatId,
            @Payload @Valid SendReactionRequest sendReactionRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return chatCommandService.sendReaction(userDetails, agoraId, chatId, sendReactionRequest);
    }

    @PostMapping("/agoras/{agoraId}/chats/filter")
    public ResponseEntity<ApiResponse<BadWordResponse>> filterChat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("agoraId") Long agoraId,
            @RequestBody SendChatRequest sendChatRequest
    ) {
        BadWordResponse response = chatCommandService.checkBadWord(userDetails, agoraId, sendChatRequest);
        return ResponseEntity.ok(ApiUtil.success(response));
    }
}
