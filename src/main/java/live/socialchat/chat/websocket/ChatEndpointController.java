package live.socialchat.chat.websocket;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import live.socialchat.chat.message.ChatMessageService;
import live.socialchat.chat.message.message.ChatHistoryRequest;
import live.socialchat.chat.message.message.ChatMessage;
import live.socialchat.chat.message.message.MessageType;
import live.socialchat.chat.message.message.RequestMessage;
import live.socialchat.chat.server.ServerMessageService;
import live.socialchat.chat.server.ServerMessageServiceImpl;
import live.socialchat.chat.session.session.ChatSession;
import live.socialchat.chat.websocket.decoder.RequestMessageDecoder;
import live.socialchat.chat.websocket.decoder.ResponseMessageDecoder;
import live.socialchat.chat.websocket.encoder.RequestMessageEncoder;
import live.socialchat.chat.websocket.encoder.ResponseMessageEncoder;
import live.socialchat.chat.websocket.filter.AccessTokenFilter.LoggedInUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static live.socialchat.chat.websocket.encoder.PayloadEncoder.decodePayload;

@Component
@ServerEndpoint(
    value = "/chat",
    decoders = {RequestMessageDecoder.class, ResponseMessageDecoder.class},
    encoders = {RequestMessageEncoder.class, ResponseMessageEncoder.class}
)
public class ChatEndpointController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatEndpointController.class);
    
    private final ChatMessageService chatMessageService;
    private final ServerMessageService serverMessageService;
    
    @Autowired
    public ChatEndpointController(final ChatMessageService chatMessageService,
                                  final ServerMessageServiceImpl clientServerMessageController) {
        this.chatMessageService = chatMessageService;
        this.serverMessageService = clientServerMessageController;
    }
    
    @OnOpen
    public void onOpen(final Session session) {
        serverMessageService.handleConnected(buildChatSession(session));
    }

    @OnMessage
    public void onMessage(final Session session, final RequestMessage<?> requestMessage) {
        if (validRequestMessage(requestMessage)) {
            handleMessages(buildChatSession(session), requestMessage, requestMessage.getType());
        } else {
            serverMessageService.handleInvalidRequest(buildChatSession(session));
        }
    }

    @OnClose
    public void onClose(final Session session) {
        serverMessageService.handleDisconnected(buildChatSession(session));
    }
    
    @OnError
    public void onError(final Session session, final Throwable throwable) {
        LOGGER.error("Error occurred during connection {}. Reason {}", session.getId(), throwable.getMessage());
    }
    
    private void handleMessages(final ChatSession chatSession,
                                final RequestMessage<?> requestMessage,
                                final MessageType messageType) {

        switch (messageType) {
            case USER_MESSAGE:
                chatMessageService
                    .handleChatMessage(chatSession, decodePayload(requestMessage.getPayload(), ChatMessage.class));
                break;
            case CHAT_HISTORY:
                chatMessageService
                    .handleChatHistory(chatSession, decodePayload(requestMessage.getPayload(), ChatHistoryRequest.class));
                break;
            case CONTACTS_LIST:
                chatMessageService
                    .handleContactsMessage(chatSession);
                break;
            case PING:
                serverMessageService.handlePing(chatSession);
                break;
            default:
                LOGGER.error("Unable to handle message of type {}", messageType.name());
        }
        
    }
    
    public ChatSession buildChatSession(final Session session) {
        
        final LoggedInUser userPrincipal = (LoggedInUser) session.getUserPrincipal();
        
        return ChatSession.builder()
            .id(userPrincipal.getSessionId())
            .userAuthenticationDetails(userPrincipal.getUserAuthenticationDetails())
            .webSocketSession(session)
            .connectionId(session.getId())
            .build();
    }
    
    private boolean validRequestMessage(final RequestMessage<?> requestMessage) {
        return requestMessage != null && requestMessage.getType() != null;
    }

}