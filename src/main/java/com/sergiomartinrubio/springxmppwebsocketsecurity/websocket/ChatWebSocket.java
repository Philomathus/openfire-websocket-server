package com.sergiomartinrubio.springxmppwebsocketsecurity.websocket;

import com.sergiomartinrubio.springxmppwebsocketsecurity.config.SpringContext;
import com.sergiomartinrubio.springxmppwebsocketsecurity.bridge.WebSocketToXmppBridge;
import com.sergiomartinrubio.springxmppwebsocketsecurity.model.WebsocketMessage;
import com.sergiomartinrubio.springxmppwebsocketsecurity.websocket.utils.MessageDecoder;
import com.sergiomartinrubio.springxmppwebsocketsecurity.websocket.utils.MessageEncoder;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

@Slf4j
@ServerEndpoint(value = "/chat/{username}", decoders = MessageDecoder.class, encoders = MessageEncoder.class)
public class ChatWebSocket {

    private final WebSocketToXmppBridge webSocketToXmppBridge;

    public ChatWebSocket() {
        this.webSocketToXmppBridge = (WebSocketToXmppBridge) SpringContext.getApplicationContext().getBean("XMPPFacade");
    }

    @OnOpen
    public void open(Session session, @PathParam("username") String username) {
        log.info("Starting XMPP session '{}'.", session.getId());
        webSocketToXmppBridge.startSession(session, username);
    }

    @OnMessage
    public void handleMessage(WebsocketMessage message, Session session) {
        log.info("Sending message for session '{}'.", session.getId());
        webSocketToXmppBridge.handleMessage(message, session);
        log.info("Message sent for session '{}'.", session.getId());
    }

    @OnClose
    public void close(Session session) {
        webSocketToXmppBridge.disconnect(session);
    }

    @OnError
    public void onError(Throwable e, Session session) {
        log.warn("Something went wrong.", e);
        webSocketToXmppBridge.disconnect(session);
    }
}
