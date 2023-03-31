package com.sergiomartinrubio.springxmppwebsocketsecurity.bridge;

import com.sergiomartinrubio.springxmppwebsocketsecurity.exception.XmppGenericException;
import com.sergiomartinrubio.springxmppwebsocketsecurity.model.WebsocketMessage;
import com.sergiomartinrubio.springxmppwebsocketsecurity.websocket.utils.WebSocketTextMessageHelper;
import com.sergiomartinrubio.springxmppwebsocketsecurity.xmpp.XmppClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.sergiomartinrubio.springxmppwebsocketsecurity.model.MessageType.ERROR;
import static com.sergiomartinrubio.springxmppwebsocketsecurity.model.MessageType.LOGGED_IN;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketToXmppBridge {

    private static final Map<Session, XMPPTCPConnection> CONNECTIONS = new HashMap<>();
    private final WebSocketTextMessageHelper webSocketTextMessageHelper;
    private final XmppClient xmppClient;

    public void startSession(Session session, String username) {
        // TODO: Save user session to avoid having to login again when the websocket connection is closed
        //      1. Generate token
        //      2. Save username and token in Redis
        //      3. Return token to client and store it in a cookie or local storage
        //      4. When starting a websocket session check if the token is still valid and bypass XMPP authentication

        XMPPTCPConnection connection = xmppClient.connect(username, "Ciretose@206");

        if (connection == null) {
            log.info("XMPP connection was not established. Closing websocket session...");
            webSocketTextMessageHelper.send(session, WebsocketMessage.builder().messageType(ERROR).build());
            try {
                session.close();
                return;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            try {
                log.info("Login into account.");
                xmppClient.login(connection);
            } catch (XmppGenericException ex) {
                //TODO: check if user with username really exists
                log.info("The user does not exist. Creating a new account.");
                xmppClient.createAccount( username, "Ciretose@206" );
                xmppClient.login(connection);
            }
        } catch(Exception e) {
            handleXMPPGenericException(session, connection, e);
            return;
        }

        CONNECTIONS.put(session, connection);
        log.info("Session was stored.");

        xmppClient.addIncomingMessageListener(connection, session);

        webSocketTextMessageHelper.send(session, WebsocketMessage.builder().to(username).messageType(LOGGED_IN).build());
    }

    public void handleMessage(WebsocketMessage message, Session session) {
        XMPPTCPConnection connection = CONNECTIONS.get(session);

        if (connection == null) {
            return;
        }

        try {
            // TODO: save message for both users in DB
            switch (message.getMessageType()) {
                case PERSONAL_MESSAGE -> {
                    xmppClient.sendMessage(connection, message.getContent(), message.getTo());
                }
                case ROOM_MESSAGE -> {
                    xmppClient.sendRoomMessage(connection, message.getContent(), message.getRoomId());
                }
                case CREATE_ROOM -> {
                    xmppClient.createRoom(connection, session, message.getRoomId());
                }
                case JOIN_ROOM -> {
                    xmppClient.joinRoom(connection, session, message.getRoomId());
                }
                case LEAVE_ROOM -> {
                    xmppClient.leaveRoom(connection, message.getRoomId());
                }
                case DESTROY_ROOM -> {
                    xmppClient.destroyRoom(connection, message.getRoomId());
                }
                case LOGOUT -> {
                    disconnect(session);
                }
                default -> log.warn("Message type not implemented.");
            }
        } catch (Exception e) {
//            handleXMPPGenericException(session, connection, e);
        }
    }

    public void disconnect(Session session) {
        XMPPTCPConnection connection = CONNECTIONS.get(session);

        if (connection == null) {
            return;
        }

        try {
            xmppClient.sendStanza(connection, Presence.Type.unavailable);
        } catch (XmppGenericException e) {
            log.error("XMPP error.", e);
            webSocketTextMessageHelper.send(session, WebsocketMessage.builder().messageType(ERROR).build());
        }

        xmppClient.disconnect(connection);
        CONNECTIONS.remove(session);
        log.info("Disconnected");
    }

    private void handleXMPPGenericException(Session session, XMPPTCPConnection connection, Exception e) {
        log.error("XMPP error. Disconnecting and removing session...", e);
        xmppClient.disconnect(connection);
        webSocketTextMessageHelper.send(session, WebsocketMessage.builder().messageType(ERROR).build());
        CONNECTIONS.remove(session);
    }
}
