package com.feiwin.openfirewebsocketserver.utils;

import com.google.gson.Gson;
import com.feiwin.openfirewebsocketserver.model.WebsocketMessage;

import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;

public class MessageEncoder implements Encoder.Text<WebsocketMessage> {

    private final static Gson gson = new Gson();

    @Override
    public String encode(WebsocketMessage message) {
        return gson.toJson(message);
    }

    @Override
    public void init(EndpointConfig config) {

    }

    @Override
    public void destroy() {

    }
}
