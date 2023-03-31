package com.feiwin.openfirewebsocketserver.service;

import com.feiwin.openfirewebsocketserver.config.OpenfireProperties;
import com.feiwin.openfirewebsocketserver.exception.OpenfireGenericException;
import com.feiwin.openfirewebsocketserver.utils.ResponseHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.PresenceBuilder;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.xdata.form.FillableForm;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import jakarta.annotation.PostConstruct;
import jakarta.websocket.Session;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(OpenfireProperties.class)
public class OpenfireService {

    private final OpenfireProperties openfireProperties;
    private XMPPTCPConnection adminXmppConnection;

    @PostConstruct
    private void loginAdminXmppAccount() {
        adminXmppConnection = connect(openfireProperties.getAdminUsername(), openfireProperties.getAdminPassword());
        login(adminXmppConnection);
    }

    public XMPPTCPConnection connect(String username) {
        return connect(username, openfireProperties.getUserPassword());
    }

    public XMPPTCPConnection connect(String username, String password) {
        XMPPTCPConnection connection;
        try {
            EntityBareJid entityBareJid = JidCreate.entityBareFrom(username + "@" + openfireProperties.getDomain());
            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                    .setHost(openfireProperties.getHost())
                    .setPort(openfireProperties.getPort())
                    .setXmppDomain(openfireProperties.getDomain())
                    .setUsernameAndPassword(entityBareJid.getLocalpart(), password)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                    .setResource(entityBareJid.getResourceOrEmpty())
                    .setSendPresence(true)
                    .build();

            connection = new XMPPTCPConnection(config);
            connection.connect();
        } catch (SmackException | IOException | XMPPException | InterruptedException e) {
            log.info("Could not connect to XMPP server.", e);
            return null;
        }
        return connection;
    }

    public void login(XMPPTCPConnection connection) {
        try {
            connection.login();
        } catch (XMPPException | SmackException | IOException | InterruptedException e) {
            log.error("Login to XMPP server with user {} failed.", connection.getUser(), e);

            EntityFullJid user = connection.getUser();
            throw new OpenfireGenericException(user == null ? "unknown" : user.toString(), e);
        }
        log.info("User '{}' logged in.", connection.getUser());
    }

    public void createAccount(String username) {
        createAccount(username, openfireProperties.getUserPassword());
    }

    public void createAccount(String username, String password) {
        AccountManager accountManager = AccountManager.getInstance(adminXmppConnection);
        accountManager.sensitiveOperationOverInsecureConnection(true);
        try {
            accountManager.createAccount(Localpart.from(username), password);
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException |
                InterruptedException |
                XmppStringprepException e) {
            throw new OpenfireGenericException(adminXmppConnection.getUser().toString(), e);
        }

        log.info("Account for user '{}' created.", username);
    }

    public void addIncomingMessageListener(XMPPTCPConnection connection, Session webSocketSession) {
        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener((from, message, chat) -> ResponseHelper
                .sendPersonalResponse(message, webSocketSession));
        log.info("Incoming message listener for user '{}' added.", connection.getUser());
    }

    public void joinRoom(XMPPTCPConnection connection, Session webSocketSession, String roomId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(roomId + "@" + openfireProperties.getRoomDomain()));
            multiUserChat.join(connection.getUser().getResourcepart());
            multiUserChat.addMessageListener( message -> ResponseHelper
                    .sendGroupResponse(message, webSocketSession) );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createRoom(XMPPTCPConnection connection, Session webSocketSession, String roomId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(roomId + "@" + openfireProperties.getRoomDomain()));
            multiUserChat.create(connection.getUser().getResourcepart());
            FillableForm form = multiUserChat.getConfigurationForm().getFillableForm();
//            form.setAnswer("muc#roomconfig_persistentroom", true);
            form.setAnswer("muc#roomconfig_maxusers", 100);
            multiUserChat.sendConfigurationForm(form);
            multiUserChat.addMessageListener( message -> ResponseHelper
                    .sendGroupResponse(message, webSocketSession) );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(XMPPTCPConnection connection, String message, String to) {
        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        try {
            Chat chat = chatManager.chatWith(JidCreate.entityBareFrom(to + "@" + openfireProperties.getDomain()));
            chat.send(message);
            log.info("Message sent to user '{}' from user '{}'.", to, connection.getUser());
        } catch (XmppStringprepException | SmackException.NotConnectedException | InterruptedException e) {
            throw new OpenfireGenericException(connection.getUser().toString(), e);
        }
    }

    public void sendRoomMessage(XMPPTCPConnection connection, String message, String roomId) {
        try {
            EntityBareJid groupJid = JidCreate.entityBareFrom(roomId + "@" + openfireProperties.getRoomDomain());
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(groupJid);
            multiUserChat.sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void leaveRoom(XMPPTCPConnection connection, String roomId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(roomId + "@" + openfireProperties.getRoomDomain()));
            multiUserChat.leave();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void destroyRoom(XMPPTCPConnection connection, String roomId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(roomId + '@' + openfireProperties.getRoomDomain()));
            multiUserChat.destroy("The owner left", null);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect(XMPPTCPConnection connection) {
        Presence presence = PresenceBuilder.buildPresence()
                .ofType(Presence.Type.unavailable)
                .build();
        try {
            connection.sendStanza(presence);
            connection.disconnect();
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            log.error("XMPP error.", e);

        }
        connection.disconnect();
        log.info("Connection closed for user '{}'.", connection.getUser());
    }

    public void sendStanza(XMPPTCPConnection connection, Presence.Type type) {
        Presence presence = PresenceBuilder.buildPresence()
                .ofType(type)
                .build();
        try {
            connection.sendStanza(presence);
            log.info("Status {} sent for user '{}'.", type, connection.getUser());
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            log.error("XMPP error.", e);
            throw new OpenfireGenericException(connection.getUser().toString(), e);
        }
    }
}
