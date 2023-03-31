package com.feiwin.openfirewebsocketserver.exception;

public class OpenfireGenericException extends RuntimeException {

    private static final String MESSAGE = "Something went wrong when connecting to the XMPP server with username '%s'.";
    public OpenfireGenericException(String username, Throwable e) {
        super(String.format(MESSAGE, username), e);
    }
}
