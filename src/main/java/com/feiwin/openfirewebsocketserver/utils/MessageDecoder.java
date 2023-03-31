package com.feiwin.openfirewebsocketserver.utils;

import com.google.gson.Gson;
import com.feiwin.openfirewebsocketserver.model.WebsocketMessage;

import jakarta.websocket.Decoder;
import jakarta.websocket.EndpointConfig;

public class MessageDecoder implements Decoder.Text<WebsocketMessage> {

    private final static Gson gson = new Gson();

    @Override
    public WebsocketMessage decode(String message) {
        return gson.fromJson(message, WebsocketMessage.class);
    }

    @Override
    public boolean willDecode(String message) {
        return (message != null);
    }

    @Override
    public void init(EndpointConfig config) {

    }

    @Override
    public void destroy() {

    }
}
