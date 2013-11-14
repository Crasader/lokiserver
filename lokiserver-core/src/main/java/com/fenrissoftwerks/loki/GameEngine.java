package com.fenrissoftwerks.loki;

import com.fenrissoftwerks.loki.commandhandler.AbstractCommandHandler;
import com.fenrissoftwerks.loki.gameserver.GameServer;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * GameEngine - GameEngine is a basic Command processing engine, subclasses of which implement all of the game
 * specific logic/mechanics for a game.  At classload time, this class fetches the environment variables
 * and uses them to set up a hash of Command names to their associated AbstractCommandHandler implementations.  This is
 * used by the default processCommand method to instantiate the correct Handler (via reflection) and call its
 * executeCommand method.  If reflection is deemed too slow for a given game, the processCommand method can of course
 * be overridden to provide a faster implementation.
 */
public abstract class GameEngine {

    protected GameServer server;

    protected static Map<String, String> commandMap = new HashMap<String, String>();
    private static Logger logger = Logger.getLogger(GameEngine.class);
    private static final String LS_COMMAND_IMPLEMENTOR_PREFIX_KEY = "LOKISERVER.COMMAND.IMPLEMENTOR.";

    static {
        Map<String, String> envvars = System.getenv();
        for(String key : envvars.keySet()) {
            logger.info("Inspecting " + key);
            if(key.startsWith(LS_COMMAND_IMPLEMENTOR_PREFIX_KEY)) {
                String commandName = key.substring(LS_COMMAND_IMPLEMENTOR_PREFIX_KEY.length() + 1);
                logger.info("Adding " + commandName + ":" + envvars.get(key));
                commandMap.put(key, envvars.get(key));
            }
        }
    }

    /**
     * Process and execute the incoming Command from the client identified by the ChannelHandlerContext
     * In this implementation, the Command's name is used to look up the appropriate CommandHandler class name from
     * the map.  Using reflection, that class is then instantiated, and its executeCommand method is called.
     * @param command The incoming Command to process
     * @param ctx The ChannelHandlerContext identifying the client issuing the Command
     */
    public void processCommand(Command command, ChannelHandlerContext ctx) {
        String commandName = command.getCommandName();

        if(commandName == null) {
            logger.error("Got a null command name.  Ignoring.");
            return;
        }
        // Check the commandName param and fetch a handler from the commandMap
        String handlerClassStr = commandMap.get(commandName);
        Class clazz = null;
        try {
            clazz = Class.forName(handlerClassStr);
        } catch (ClassNotFoundException e) {
            logger.error("Could not find class for handler class: " + handlerClassStr, e);
            return;
        }
        Class partypes[] = new Class[2];
        partypes[0] = ChannelHandlerContext.class;
        partypes[1] = GameEngine.class;
        Constructor ct = null;
        try {
            ct = clazz.getConstructor(partypes);
        } catch (NoSuchMethodException e) {
            logger.error("Handler class " + handlerClassStr + " doesn't have correct constructor", e);
            return;
        }
        Object arglist[] = new Object[2];
        arglist[0] = ctx;
        arglist[1] = this;
        AbstractCommandHandler handler = null;
        try {
            handler = (AbstractCommandHandler)ct.newInstance(arglist);
        } catch (Exception e) {
            logger.error("Error while trying to instantiate an AbstractCommandHandler: ", e);
            return;
        }
        handler.executeCommand(command);
    }

    /**
     * Handle client disconnects
     * The default implementation does nothing.  Subclasses should override this method to implement any game-specific
     * cleanup that needs to happen at client disconnect time.
     * @param ctx
     */
    public void handleClientDisconnect(ChannelHandlerContext ctx) {
        logger.info("Handling clientDisconnect in abstract GameEngine class");
    }

    /**
     * Add a CommandHandler for the given command name to our command map
     * @param commandName The name of the command
     * @param commandHandlerClassName The fully qualified class name of the CommandHandler
     */
    public void addCommandHandlerToCommandMap(String commandName, String commandHandlerClassName) {
        commandMap.put(commandName, commandHandlerClassName);
    }
}
