package com.fenrissoftwerks.loki.gameserver;

import com.fenrissoftwerks.loki.Command;
import com.fenrissoftwerks.loki.GameEngine;
import com.fenrissoftwerks.loki.gameserver.channelhandler.GameServerHandler;
import com.fenrissoftwerks.loki.util.LokiExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * GameServer - The GameServer class encapsulates all of the server-side logic for a game.  This includes all
 * communications with clients, as well as all the game mechanics, scoring, etc.  Game servers should subclass
 * this class to implement specific game functionality.
 *
 * GameServer is a Singleton.  The first time the instance is requested, it will look for its game.properties
 * configuration file in the classpath.  Using the configuration file, it will create an instance of the game's
 * GameEngine subclass for handling all the game logic.
 *
 * Messages get passed in and out of the GameServer as JSON-serialized Command objects.  See Command for details.
 * When the GameServer receives a Command, it deserializes it and passes it in to the GameEngine via the
 * processCommand() method.  Similarly, the GameEngine can pass outbound Commands to the GameServer via the
 * sendCommand() method.
 *
 * The GameServer stores a mapping of game objects to lists of clients watching them in a local hash.  This
 * facilitates multicasting of Commands.  It also stores the converse map - client to list of game object they
 * are watching.  This lets us quickly clean up when a client disconnects.
 *
 * The GameServer can accept both raw messages and WebSocket frames.  To listen for raw messages on a port, use the
 * startServer(port) method.  Use startWebSocketServer(port) to listen for WebSocket frames on the port.  One or more
 * of each can be active simultaneously, as well.
 */
public class GameServer {

    private HashMap<Object, List<Channel>> objectWatchers = new HashMap<Object, List<Channel>>();
    private HashMap<Channel, List<Object>> clientsWatchingObjects = new HashMap<Channel, List<Object>>();
    private List<ServerBootstrap> activeServerBootstraps = new ArrayList<ServerBootstrap>();
    private GameEngine engine;
    private static Logger logger = Logger.getLogger(GameServer.class);
    public static final String GAME_PROPERTIES_FILENAME = "game.properties";
    public static final String KEY_GAME_ENGINE_CLASS = "game.engine.class";
    private static final Character COMMAND_DELIMITER = 0x01;
    private static Gson gson = new GsonBuilder().setExclusionStrategies(new LokiExclusionStrategy()).create();

    private int wsRunningOnPort = 0;
    private String gameEngineClassName = null;

    static final ChannelGroup allChannels = new DefaultChannelGroup("lokiserver");

    private GameServer() {}

    private static class SingletonHolder {
        private static final GameServer INSTANCE = new GameServer();
    }

    public static void setGameEngineClassName(String gameEngineClassName) {
        SingletonHolder.INSTANCE.gameEngineClassName = gameEngineClassName;
    }

    public static GameServer getInstance() {
        GameServer instance = SingletonHolder.INSTANCE;
        if(instance.engine == null) {
            // Initialize the game engine.  If the static gameEngineClassName has been set, just use that.
            if(instance.gameEngineClassName != null) {
                try {
                    Class gameEngineClass = Class.forName(instance.gameEngineClassName);
                    instance.engine = (GameEngine)gameEngineClass.newInstance();
                } catch (ClassNotFoundException e) {
                    logger.fatal("Class not found for programatically configured GameEngine instance: ", e);
                } catch (InstantiationException e) {
                    logger.fatal("InstantiationException for programatically configured GameEngine instance: ", e);
                } catch (IllegalAccessException e) {
                    logger.fatal("IllegalAccessException for programatically configured GameEngine instance: ", e);
                }
            } else {
                ClassLoader cl = GameServer.class.getClassLoader();
                InputStream propertiesStream = cl.getResourceAsStream(GAME_PROPERTIES_FILENAME);
                try {
                    Properties props = new Properties();
                    props.load(propertiesStream);
                    String gameEngineClassString = props.getProperty(KEY_GAME_ENGINE_CLASS);
                    logger.info("Creating instance of gameEngineClass: " + gameEngineClassString);
                    Class gameEngineClass = Class.forName(gameEngineClassString);
                    instance.engine = (GameEngine)gameEngineClass.newInstance();
                } catch (Exception e) {
                    logger.fatal("Could not instantiate a GameEngine instance", e);
                }
            }
        }
        return SingletonHolder.INSTANCE;
    }

    public static void addChannelToServerChannelGroup(Channel channel) {
        allChannels.add(channel);
    }

    // Set up a client as a watcher of a game object
    public void addWatcherForObject(Object gameObject, Channel clientChannel) {
        // Add to objectWatchers
        if(!objectWatchers.containsKey(gameObject)) {
            List<Channel> watchers = new ArrayList<Channel>();
            objectWatchers.put(gameObject, watchers);
        }
        List<Channel> watchers = objectWatchers.get(gameObject);
        if(!watchers.contains(clientChannel)) {
            watchers.add(clientChannel);
        }
        // Add to clientsWatchingObjects
        if(!clientsWatchingObjects.containsKey(clientChannel)) {
            List<Object> gameObjects = new ArrayList<Object>();
            clientsWatchingObjects.put(clientChannel, gameObjects);
        }
        List<Object> gameObjects = clientsWatchingObjects.get(clientChannel);
        if(!gameObjects.contains(gameObject)) {
            gameObjects.add(gameObject);
        }
    }

    public List<Channel> getWatchersForObject(Object gameObject) {
        return objectWatchers.get(gameObject);
    }

    public List<Object> getObjectsWatchedBy(Channel clientChannel) {
        return clientsWatchingObjects.get(clientChannel);
    }

    public void removeWatcherForObject(Object gameObject, Channel clientChannel) {
        if(objectWatchers.containsKey(gameObject)) {
            objectWatchers.get(gameObject).remove(clientChannel);
        }
        if(clientsWatchingObjects.containsKey(clientChannel)) {
            clientsWatchingObjects.get(clientChannel).remove(gameObject);
        }
    }

    // Send a Command (e.g. UpdateBoard or something) to all watchers of an object
    public void sendCommandToWatchers(Command command, Object gameObject) {
        // Serialize the Command for writing to the wire
        String commandAsJSON = null;
        try {
            commandAsJSON = gson.toJson(command);
        } catch (Exception e) {
            logger.error("Caught an exception while trying to serialize command: " + command.getCommandName(), e);
            return;
        }
        logger.debug("Outbound command looks like: " + commandAsJSON);
        // Get list of clients to send to
        List<Channel> watchers = objectWatchers.get(gameObject);

        // For each client in the target list, send the Command
        for(Channel watcher : watchers) {
            sendCommandToClient(command, watcher);
        }
    }

    // Send a Command to a single client
    public void sendCommandToClient(Command command, Channel clientChannel) {
        String commandAsJSON = gson.toJson(command) + COMMAND_DELIMITER;
        logger.debug("Outbound command looks like: " + commandAsJSON);

        // Check the port the client connected to.  If it is the WebSocket port, send a TextWebSocketFrame.
        // Otherwise send the raw message.
        if(((InetSocketAddress)clientChannel.getLocalAddress()).getPort() == wsRunningOnPort) {
            clientChannel.write(new TextWebSocketFrame(commandAsJSON));
        } else {
            clientChannel.write(commandAsJSON);
        }
    }

    public void startServer() throws Exception {
        startServer(5000);
    }
    
    // Function for starting up a server on a specific port
    public void startServer(int port) throws Exception {
        // Configure the server.
        final GameServer server = this;
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                        new DelimiterBasedFrameDecoder(8192, Delimiters.nulDelimiter()),
                        new StringDecoder(),
                        new StringEncoder(),
                        new GameServerHandler(server, engine, gson));
            }
        });
        activeServerBootstraps.add(bootstrap);

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));
    }

    public void startWebSocketServer() throws Exception {
        startWebSocketServer(5001);
    }

    // Function for starting up a server on a specific port
    public void startWebSocketServer(int port) throws Exception {
        // Configure the server.
        final GameServer server = this;
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                        new HttpRequestDecoder(),
                        new HttpChunkAggregator(65536),
                        new HttpResponseEncoder(),
                        new WebSocketServerProtocolHandler("/websocket"),
                        new GameServerHandler(server, engine, gson));
            }
        });
        activeServerBootstraps.add(bootstrap);

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));
        wsRunningOnPort = port;
    }

    public void addCommandHandlerForCommandName(String commandName, String commandHandlerClassName) {
        engine.addCommandHandlerToCommandMap(commandName, commandHandlerClassName);
    }

    public void stopServer() {
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();
        for(ServerBootstrap bs : activeServerBootstraps) {
            bs.releaseExternalResources();
        }
    }

    // Implementations can set their own custom Gson objects to handle custom serializations
    public static Gson getGson() {
        return gson;
    }

    public static void setGson(Gson gson) {
        GameServer.gson = gson;
    }
}
