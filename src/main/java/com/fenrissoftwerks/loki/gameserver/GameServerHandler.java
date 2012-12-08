package com.fenrissoftwerks.loki.gameserver;

import com.fenrissoftwerks.loki.Command;
import com.fenrissoftwerks.loki.GameEngine;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

/**
 * GameServerHandler - A class for handling incoming and outgoing messages to/from the game server and clients.
 * This class implements the SimpleChannelUpstreamHandler class in netty.  See that class in the netty docs
 * for more information.
 */
public class GameServerHandler extends SimpleChannelUpstreamHandler {

    private static final Logger logger = Logger.getLogger(GameServerHandler.class);

    private GameServer server;
    private GameEngine engine;
    private Gson gson;

    public GameServerHandler(GameServer server, GameEngine engine, Gson gson) {
        this.server = server;
        this.engine = engine;
        this.gson = gson;
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent &&((ChannelStateEvent) e).getState() != ChannelState.INTEREST_OPS) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    /**
     * Handle an incoming MessageEvent from the client identified by a ChannelHandlerContext
     * Takes the incoming Message and checks to see if it is a JSON-serialized Command.  If it is, it passes the
     * Command in to the GameEngine for processing.  If it is not, we send our policy.xml file as the response.
     * @param ctx The ChannelHandlerContext containing the client info
     * @param e The MessageEvent that needs execution
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        logger.debug("in messageReceived Handler, message looks like: ");
        logger.debug(e.getMessage().toString());
        // Try to deserialize the incoming message as a Command object
        try {
            Command command = gson.fromJson((String)e.getMessage(), Command.class);
            engine.processCommand(command, ctx);
        } catch (JsonParseException e1) {
            // Are they looking for the policy file?
            if(e.getMessage().toString().startsWith("<policy-file-request/>")) {
                logger.debug("Detected policy file request");
                // Get the file as a resource, then as a string
                InputStream policyStream = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("policy.xml");
                StringWriter writer = new StringWriter();
                try {
                    IOUtils.copy(policyStream, writer, "UTF8");
                } catch (IOException e2) {
                    logger.error("Could not copy policy file to string");
                }
                writer.append('\0');
                String policy = writer.toString();
                logger.debug("Policy string is: " + policy);
                ctx.getChannel().write(policy);
            } else {
                throw e1;
            }
        }
    }

    /**
     * Handle ExceptionEvents from clients
     * We trap any exceptions and use it to close the channel on which the Exception happened.
     * @param ctx The ChannelHandlerContext identifying the client with the Exception
     * @param e The ExceptionEvent that was thrown
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.warn("Unexpected exception from downstream.", e.getCause());
        e.getChannel().close();
    }

    /**
     * Handle the client disconnecting event
     * When a client disconnects from the server, this method catches the event and cleans up the game server.  This
     * amounts to removing the client as a watcher on any objects they were watching.  This method also notifies the
     * associated GameEngine of the disconnection. 
     * @param ctx
     * @param e
     */
    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        // Get the list of objects being watched by the disconnected client
        Channel clientChannel = ctx.getChannel();
        List<Object> watchedObjects = server.getObjectsWatchedBy(clientChannel);
        for(Object watchedObject : watchedObjects) {
            server.removeWatcherForObject(watchedObject, clientChannel);
        }

        // Notify the game engine
        engine.handleClientDisconnect(ctx);
    }
}
