package live.socialchat.chat.message;

import live.socialchat.chat.broadcast.BroadcasterService;
import live.socialchat.chat.contact.Contact;
import live.socialchat.chat.group.GroupRepository;
import live.socialchat.chat.group.model.Group;
import live.socialchat.chat.message.message.ChatHistoryRequest;
import live.socialchat.chat.message.message.ChatHistoryResponse;
import live.socialchat.chat.message.message.ChatMessage;
import live.socialchat.chat.message.message.MessageType;
import live.socialchat.chat.message.message.ResponseMessage;
import live.socialchat.chat.session.session.ChatSession;
import live.socialchat.chat.user.UserRepository;
import live.socialchat.chat.user.model.User;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

    private final ExecutorService executorService;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final BroadcasterService broadcasterService;
    
    public ChatMessageServiceImpl(final ExecutorService executorService,
                                  final UserRepository userRepository,
                                  final GroupRepository groupRepository,
                                  final MessageRepository messageRepository,
                                  final BroadcasterService broadcasterService) {
        
        this.executorService = executorService;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.broadcasterService = broadcasterService;
    }
    
    @Override
    public void handleChatMessage(final ChatSession chatSession,
                                  final ChatMessage receivedMessage) {
    
        Mono
            .fromRunnable(() -> {
    
                LOGGER.info("handling chat message");
                
                final String userId = chatSession.getUserAuthenticationDetails().getUserId();
    
                final ChatMessage chatMessage = ChatMessage.builder()
                    .objectId(new ObjectId())
                    .from(userId)
                    .date(OffsetDateTime.now().toString())
                    .destinationId(receivedMessage.getDestinationId())
                    .destinationType(receivedMessage.getDestinationType())
                    .content(receivedMessage.getContent())
                    .mimeType(receivedMessage.getMimeType())
                    .build();
    
                ResponseMessage<ChatMessage> responseMessage = new ResponseMessage<>(MessageType.USER_MESSAGE, chatMessage);
                
                messageRepository.insert(chatMessage);
                broadcasterService.broadcastChatMessage(chatSession, responseMessage);
                
            })
            .publishOn(Schedulers.fromExecutorService(executorService))
            .subscribe();

    }
    
    @Override
    public void handleContactsMessage(final ChatSession chatSession) {
    
        final String userId = chatSession.getUserAuthenticationDetails().getUserId();
        final Flux<User> userContacts = userRepository.findContacts(userId);
        final Flux<Group> groupContacts = groupRepository.findGroups(userId);
        
        Flux.concat(userContacts, groupContacts)
            .collectList()
            .subscribe(contacts -> {

                ResponseMessage<Object> responseMessage = ResponseMessage
                    .builder()
                    .type(MessageType.CONTACTS_LIST)
                    .payload(contacts)
                    .build();
    
                broadcasterService.broadcastToSession(chatSession, responseMessage);
                
            });

    }
    
    @Override
    public void handleNewContact(final Contact contact, final ChatSession chatSession) {
    
        ResponseMessage<Object> responseMessage = ResponseMessage
            .builder()
            .type(MessageType.NEW_CONTACT_REGISTERED)
            .payload(Collections.singletonList(contact))
            .build();
    
        broadcasterService.broadcastToAllExceptSession(chatSession, responseMessage);
        
    }
    
    @Override
    public void handleChatHistory(final ChatSession chatSession,
                                  final ChatHistoryRequest chatHistoryRequest) {
        
        final String senderId = chatSession.getUserAuthenticationDetails().getUserId();
        
        userRepository.findDestinationType(chatHistoryRequest.getDestinationId())
            .switchIfEmpty(groupRepository.findDestinationType(chatHistoryRequest.getDestinationId()))
            .flatMapMany(destinationType -> messageRepository.findMessages(senderId, destinationType, chatHistoryRequest))
            .collectList()
            .subscribe(chatHistory -> {
    
                final ResponseMessage<Object> responseMessage = ResponseMessage
                    .builder()
                    .type(MessageType.CHAT_HISTORY)
                    .payload(ChatHistoryResponse.builder()
                        .destinationId(chatHistoryRequest.getDestinationId())
                        .chatHistory(chatHistory)
                        .build())
                    .build();
    
                broadcasterService.broadcastToSession(chatSession, responseMessage);
    
            });
        
    }
    
}
