package com.feiwin.openfirewebsocketserver.utils;

import com.feiwin.openfirewebsocketserver.model.MessageType;
import com.feiwin.openfirewebsocketserver.model.WebsocketMessage;
import lombok.extern.slf4j.Slf4j;

import jakarta.websocket.EncodeException;
import jakarta.websocket.Session;
import org.jivesoftware.smack.packet.Message;

import java.io.IOException;

import static com.feiwin.openfirewebsocketserver.model.MessageType.PERSONAL_MESSAGE;
import static com.feiwin.openfirewebsocketserver.model.MessageType.ROOM_MESSAGE;

@Slf4j
public class ResponseHelper {

    private ResponseHelper() {
        throw new UnsupportedOperationException("This is a static utility class!");
    }

    public static void send(Session session, WebsocketMessage websocketMessage) {
        try {
            log.info("Sending message of type '{}'.", websocketMessage.getMessageType());
            session.getBasicRemote().sendObject(websocketMessage);
        } catch (IOException | EncodeException e) {
            log.error("WebSocket error, message {} was not sent.", websocketMessage, e);
        }
    }

    private static void sendResponse(Message message, Session session, MessageType messageType) {
        log.info("New message from '{}' to '{}': {}", message.getFrom(), message.getTo(), message.getBody());
        String messageFrom = message.getFrom().getLocalpartOrNull().toString();
        String to = message.getTo().getLocalpartOrNull().toString();
        String content = message.getBody();
        send(
                session,
                WebsocketMessage.builder()
                        .from(messageFrom)
                        .to(to)
                        .content(content)
                        .messageType(messageType).build()
        );
    }

    public static void sendPersonalResponse(Message message, Session session) {
        sendResponse(message, session, PERSONAL_MESSAGE);
    }

    public static void sendGroupResponse(Message message, Session session) {
        sendResponse(message, session, ROOM_MESSAGE);
    }
}
