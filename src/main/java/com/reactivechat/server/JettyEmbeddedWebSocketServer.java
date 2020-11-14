package com.reactivechat.server;

import com.reactivechat.websocket.ChatEndpoint;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

public class JettyEmbeddedWebSocketServer {
    
    public void start() {
    
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.addConnector(connector);
    
        // Setup the basic application "context" for this application at "/"
        // This is also known as the handler tree (in jetty speak)
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
    
        try {
            // Initialize javax.websocket layer
            WebSocketServerContainerInitializer.configure(context,
                (servletContext, wsContainer) ->
                {
                    // This lambda will be called at the appropriate place in the
                    // ServletContext initialization phase where you can initialize
                    // and configure  your websocket container.
                
                    // Configure defaults for container
                    wsContainer.setDefaultMaxTextMessageBufferSize(65535);
                
                    // Add WebSocket endpoint to javax.websocket layer
                    wsContainer.addEndpoint(ChatEndpoint.class);
                });
        
            server.start();
            server.join();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }
        
    }
    
}