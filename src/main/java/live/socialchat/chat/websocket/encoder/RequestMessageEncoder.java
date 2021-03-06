package live.socialchat.chat.websocket.encoder;

import com.google.gson.Gson;
import live.socialchat.chat.message.message.RequestMessage;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

public class RequestMessageEncoder implements Encoder.Text<RequestMessage> {

    private static final Gson GSON = new Gson();

    @Override
    public String encode(RequestMessage message) {
        return GSON.toJson(message);
    }

    @Override
    public void init(EndpointConfig endpointConfig) {
        // Custom initialization logic
    }

    @Override
    public void destroy() {
        // Close resources
    }
    
}
