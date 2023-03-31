package com.feiwin.openfirewebsocketserver.controller;

import com.feiwin.openfirewebsocketserver.config.SpringContext;
import com.feiwin.openfirewebsocketserver.exception.OpenfireGenericException;
import com.feiwin.openfirewebsocketserver.model.WebsocketMessage;
import com.feiwin.openfirewebsocketserver.utils.MessageDecoder;
import com.feiwin.openfirewebsocketserver.utils.MessageEncoder;
import com.feiwin.openfirewebsocketserver.utils.ResponseHelper;
import com.feiwin.openfirewebsocketserver.service.OpenfireService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.feiwin.openfirewebsocketserver.model.MessageType.FAIL;
import static com.feiwin.openfirewebsocketserver.model.MessageType.SUCCESS;

@Slf4j
@ServerEndpoint(value = "/im/{username}", decoders = MessageDecoder.class, encoders = MessageEncoder.class)
public class ImWebSocketController {

    private static final Map<Session, XMPPTCPConnection> CONNECTIONS = new HashMap<>();
    private final OpenfireService openfireService = SpringContext.getBean(OpenfireService.class);

    @OnOpen
    public void open(Session session, @PathParam("username") String username) {
        log.info("Starting XMPP session '{}'.", session.getId());

        XMPPTCPConnection connection = openfireService.connect(username);

        if (connection == null) {
            log.info("XMPP connection was not established. Closing websocket session...");
            ResponseHelper.send(session, WebsocketMessage.builder().messageType(FAIL).build());
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
                openfireService.login(connection);
            } catch (OpenfireGenericException ex) {
                log.info("The user does not exist. Creating a new account.");
                openfireService.createAccount(username);
                openfireService.login(connection);
            }
        } catch(Exception e) {
            sendFailAndDisconnect(session, connection, e);
            return;
        }

        CONNECTIONS.put(session, connection);
        log.info("Session was stored.");

        openfireService.addIncomingMessageListener(connection, session);

        ResponseHelper.send(session, WebsocketMessage.builder().to(username).messageType(SUCCESS).build());
    }

    @OnMessage
    public void handleMessage(WebsocketMessage message, Session session) {
        log.info("Sending message for session '{}'.", session.getId());
        XMPPTCPConnection connection = CONNECTIONS.get(session);

        if (connection == null) {
            return;
        }

        try {
            switch (message.getMessageType()) {
                case PERSONAL_MESSAGE -> {
                    openfireService.sendMessage(connection, message.getContent(), message.getTo());
                }
                case ROOM_MESSAGE -> {
                    openfireService.sendRoomMessage(connection, message.getContent(), message.getRoomId());
                }
                case CREATE_ROOM -> {
                    openfireService.createRoom(connection, session, message.getRoomId());
                }
                case JOIN_ROOM -> {
                    openfireService.joinRoom(connection, session, message.getRoomId());
                }
                case LEAVE_ROOM -> {
                    openfireService.leaveRoom(connection, message.getRoomId());
                }
                case DESTROY_ROOM -> {
                    openfireService.destroyRoom(connection, message.getRoomId());
                }
                case LOGOUT -> {
                    close(session);
                }
                default -> log.warn("Message type not implemented.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("Message sent for session '{}'.", session.getId());
    }

    @OnClose
    public void close(Session session) {
        XMPPTCPConnection connection = CONNECTIONS.get(session);

        if (connection == null) {
            return;
        }

        try {
            openfireService.sendStanza(connection, Presence.Type.unavailable);
        } catch (OpenfireGenericException e) {
            log.error("XMPP error.", e);
            ResponseHelper.send(session, WebsocketMessage.builder().messageType(FAIL).build());
        }

        openfireService.disconnect(connection);
        CONNECTIONS.remove(session);
        log.info("Disconnected");
    }

    @OnError
    public void onError(Throwable e, Session session) {
        log.warn("Something went wrong.", e);
        close(session);
    }

    private void sendFailAndDisconnect(Session session, XMPPTCPConnection connection, Exception e) {
        log.error("XMPP error. Disconnecting and removing session...", e);
        openfireService.disconnect(connection);
        ResponseHelper.send(session, WebsocketMessage.builder().messageType(FAIL).build());
        CONNECTIONS.remove(session);
    }
}
