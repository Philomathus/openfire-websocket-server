package com.sergiomartinrubio.springxmppwebsocketsecurity.xmpp;

import com.sergiomartinrubio.springxmppwebsocketsecurity.model.MessageType;
import com.sergiomartinrubio.springxmppwebsocketsecurity.model.WebsocketMessage;
import com.sergiomartinrubio.springxmppwebsocketsecurity.websocket.utils.WebSocketTextMessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jivesoftware.smack.packet.Message;
import org.springframework.stereotype.Component;

import javax.websocket.Session;

import static com.sergiomartinrubio.springxmppwebsocketsecurity.model.MessageType.GROUP_MESSAGE;
import static com.sergiomartinrubio.springxmppwebsocketsecurity.model.MessageType.PERSONAL_MESSAGE;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmppToWebSocketTransmitter {

    private final WebSocketTextMessageHelper webSocketTextMessageHelper;

    public void sendPersonalResponse(Message message, Session session) {
        sendResponse(message, session, PERSONAL_MESSAGE);
    }

    public void sendGroupResponse(Message message, Session session) {
        sendResponse(message, session, GROUP_MESSAGE);
    }

    private void sendResponse(Message message, Session session, MessageType messageType) {
        log.info("New message from '{}' to '{}': {}", message.getFrom(), message.getTo(), message.getBody());
        String messageFrom = message.getFrom().getLocalpartOrNull().toString();
        String to = message.getTo().getLocalpartOrNull().toString();
        String content = message.getBody();
        webSocketTextMessageHelper.send(
                session,
                WebsocketMessage.builder()
                        .from(messageFrom)
                        .to(to)
                        .content(content)
                        .messageType(messageType).build()
        );
    }
}
