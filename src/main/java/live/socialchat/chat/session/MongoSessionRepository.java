package live.socialchat.chat.session;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import live.socialchat.chat.session.session.ChatSession;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

@Repository
public class MongoSessionRepository implements SessionRepository {

    private static final String SESSIONS_COLLECTION = "user_session";
    private static final String CONNECTION_ID = "connectionId";
    private static final String SERVER_DETAILS = "serverDetails";
    private static final String USER_AUTHENTICATION_DETAILS = "userAuthenticationDetails";
    private static final String USER_ID = USER_AUTHENTICATION_DETAILS + ".userId";
    private static final String SESSION_STATUS = "status";
    private static final String SESSION_TYPE = "type";
    private static final String EXPIRY_DATE = "expiryDate";
    private static final String AUTHENTICATED_STATUS = "AUTHENTICATED";
    private static final Bson SERVER_REQUIRED_FIELDS =
        fields(include("id", CONNECTION_ID, SERVER_DETAILS, USER_AUTHENTICATION_DETAILS, SESSION_STATUS, SESSION_TYPE));
    
    private final MongoCollection<ChatSession> mongoCollection;
    private final Map<String, ChatSession> chatSessionsMap;
    
    @Autowired
    public MongoSessionRepository(final MongoDatabase mongoDatabase) {
        this.mongoCollection = mongoDatabase.getCollection(SESSIONS_COLLECTION, ChatSession.class);
        this.chatSessionsMap = new HashMap<>();
    }
    
    @Override
    public Mono<Boolean> createSession(final ChatSession chatSession) {
        return Mono.just(
                chatSessionsMap
                    .put(chatSession.getSessionId(), chatSession) == null
            );
    }
    
    @Override
    public Mono<Void> deleteSession(final ChatSession chatSession) {
        return Mono.just(
                chatSessionsMap
                    .remove(chatSession.getSessionId())
            )
            .then();
    }

    @Override
    public Flux<ChatSession> findAllActiveSessions() {
    
        final Bson activeSessionsFilter = and(
            eq(SESSION_STATUS, AUTHENTICATED_STATUS),
            gte(EXPIRY_DATE, OffsetDateTime.now().toString())
        );
    
        final List<ChatSession> localChatSessions = new ArrayList<>(chatSessionsMap.values());
        final Flux<ChatSession> remoteChatSessions = findRemoteChatSessions(activeSessionsFilter, localChatSessions);
    
        return Flux.concat(
            Flux.fromIterable(localChatSessions),
            remoteChatSessions
        );
    }
    
    @Override
    public Flux<ChatSession> findAllActiveSessionsByUser(final String userId) {
    
        final Bson userActiveSessionsFilter = and(
            eq(USER_ID, userId),
            eq(SESSION_STATUS, AUTHENTICATED_STATUS),
            gte(EXPIRY_DATE, OffsetDateTime.now().toString())
        );
        
        final List<ChatSession> userLocalChatSessions = new ArrayList<>(chatSessionsMap.values())
            .stream()
            .filter(chatSession -> userId.equals(chatSession.getUserAuthenticationDetails().getUserId()))
            .collect(Collectors.toList());

        final Flux<ChatSession> userRemoteChatSessions =
            findRemoteChatSessions(userActiveSessionsFilter, userLocalChatSessions);
    
        return Flux.concat(
            Flux.fromIterable(userLocalChatSessions),
            userRemoteChatSessions
        );
    }
    
    private Flux<ChatSession> findRemoteChatSessions(final Bson filters, final List<ChatSession> localChatSessions) {
        return Flux.from(
                mongoCollection.find(filters)
                    .projection(SERVER_REQUIRED_FIELDS)
            )
            .filter(session -> localChatSessions
                .stream()
                .noneMatch(localSession ->
                    session.getId().equals(localSession.getId()) &&
                        session.getUserAuthenticationDetails().getUserId()
                            .equals(localSession.getUserAuthenticationDetails().getUserId())
                )
            );
    }

}
