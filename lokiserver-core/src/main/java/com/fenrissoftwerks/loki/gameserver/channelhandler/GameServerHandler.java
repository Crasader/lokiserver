package com.fenrissoftwerks.loki.gameserver.channelhandler;

import com.fenrissoftwerks.loki.Command;
import com.fenrissoftwerks.loki.GameEngine;
import com.fenrissoftwerks.loki.gameserver.GameServer;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * GameServerHandler - A class for handling incoming and outgoing messages to/from the game server and clients.
 * This class implements the SimpleChannelUpstreamHandler class in netty.  See that class in the netty docs
 * for more information.
 */
public class GameServerHandler extends SimpleChannelHandler {

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
            logger.info("Event: " + e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        GameServer.addChannelToServerChannelGroup(e.getChannel());
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
            String message;
            if(e.getMessage() instanceof TextWebSocketFrame) {
                logger.debug("It's a TextWebSocketFrame");
                message = ((TextWebSocketFrame)e.getMessage()).getText();
            } else {
                logger.debug("It's a raw message");
                message = (String)e.getMessage();
            }
            logger.info("Command as text is: " + message);
            Command command = gson.fromJson(message, Command.class);
            logger.info("Got a command, name is: " + command.getCommandName());
            engine.processCommand(command, ctx);
        } catch (Exception e1) {
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
                logger.info("Could not deserialize JSON Command: ", e1);
//                throw e1;
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
        logger.debug("Getting channel for client");
        Channel clientChannel = ctx.getChannel();
        logger.debug("Getting watched objects for this client from the GameServer");
        List<Object> watchedObjects = server.getObjectsWatchedBy(clientChannel);
        List<Object> toRemove = new ArrayList<Object>();
        if(watchedObjects != null && watchedObjects.size() > 0) {
            logger.debug("Walking list of watched objects, size is: " + watchedObjects.size());
            for(Object watchedObject : watchedObjects) {
                toRemove.add(watchedObject);
            }
            for(Object watchedObject : toRemove) {
                logger.debug("Removing Object: " + watchedObject);
                server.removeWatcherForObject(watchedObject, clientChannel);
                logger.debug("Removed!");
            }
            logger.debug("Done removing watched objects");
        }

        // Notify the game engine
        engine.handleClientDisconnect(ctx);
    }
}
