package com.sergiomartinrubio.springxmppwebsocketsecurity.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WebsocketMessage {
    String from;
    String to;
    String roomId;
    String content;
    MessageType messageType;
}
