package com.fenrissoftwerks.loki.commandhandler;

import com.fenrissoftwerks.loki.Command;
import com.fenrissoftwerks.loki.GameEngine;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * AbstractCommandHandler - definition of a class for handling incoming Commands from clients.  Each Command in a
 * game needs to have an implementation of this class to handle its functionality.
 */
public abstract class AbstractCommandHandler {
    protected ChannelHandlerContext ctx;
    protected GameEngine engine;

    public abstract void executeCommand(Command command);
}