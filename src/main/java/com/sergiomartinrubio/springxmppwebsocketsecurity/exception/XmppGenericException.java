package com.sergiomartinrubio.springxmppwebsocketsecurity.exception;

public class XmppGenericException extends RuntimeException {

    private static final String MESSAGE = "Something went wrong when connecting to the XMPP server with username '%s'.";
    public XmppGenericException(String username, Throwable e) {
        super(String.format(MESSAGE, username), e);
    }
}
