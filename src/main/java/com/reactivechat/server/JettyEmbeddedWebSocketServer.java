package com.reactivechat.server;

import com.reactivechat.websocket.ChatEndpoint;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Builder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

public class JettyEmbeddedWebSocketServer {
    
    private static final int DEFAULT_SERVER_PORT = 8080;
    
    private final ServerEndpointConfigurator serverEndpointConfigurator;
    
    public JettyEmbeddedWebSocketServer(final ServerEndpointConfigurator serverEndpointConfigurator) {
        this.serverEndpointConfigurator = serverEndpointConfigurator;
    }
    
    public void start() {
    
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(getServerPort());
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
    
        FilterHolder cors = context.addFilter(CrossOriginFilter.class,"/*", EnumSet.of(DispatcherType.REQUEST));
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
        
        try {
            
            WebSocketServerContainerInitializer.configure(context,
                (servletContext, wsContainer) -> {

                    wsContainer.setDefaultMaxTextMessageBufferSize(65535);
                    
                    ServerEndpointConfig serverEndpointConfig = Builder
                        .create(ChatEndpoint.class, "/chat")
                        .configurator(serverEndpointConfigurator)
                        .build();
                    
                    wsContainer.addEndpoint(serverEndpointConfig);
                });
    
            server.start();
            server.join();
            
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }
        
    }
    
    private int getServerPort() {
        String portEnv = System.getenv("PORT");
        return (portEnv != null && !portEnv.isEmpty())
            ? Integer.parseInt(portEnv)
            : DEFAULT_SERVER_PORT;
    }
    
}