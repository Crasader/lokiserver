package com.fenrissoftwerks.loki.integtest;

import com.fenrissoftwerks.loki.Command;
import com.fenrissoftwerks.loki.GameEngine;
import com.fenrissoftwerks.loki.commandhandler.AbstractCommandHandler;
import com.fenrissoftwerks.loki.gameserver.GameServer;
import org.jboss.netty.channel.ChannelHandlerContext;

public class Main {

    public static class IntegTestEngine extends GameEngine {
        public IntegTestEngine() {
        }
    }

    public static class EchoCommandHandler extends AbstractCommandHandler {

        public EchoCommandHandler(ChannelHandlerContext ctx, GameEngine engine) {
            this.ctx = ctx;
            this.engine = engine;
        }

        @Override
        public void executeCommand(Command command) {
            GameServer server = GameServer.getInstance();

            String commandName = command.getCommandName();
            Object[] args = command.getCommandArgs();
            if(commandName.equals("echo")) {
                String message = (String)args[0];

                Command ackCommand = new Command();
                ackCommand.setCommandName("ack");
                ackCommand.setCommandArgs(new Object[] {message});
                server.sendCommandToClient(ackCommand, ctx.getChannel());
            } else if(commandName.equals("watchSomething")) {
                String message = (String)args[0];
                server.addWatcherForObject(message, ctx.getChannel());
            } else if(commandName.equals("pingSomething")) {
                String message = (String)args[0];
                Command pingCommand = new Command();
                pingCommand.setCommandName("pingResponse");
                pingCommand.setCommandArgs(new Object[] {new String("Hey we got a ping on: " + message)});
                server.sendCommandToWatchers(pingCommand, message);
            }
        }
    }

    public static void main(String[] args) {
        Integer port;
        Integer wsPort;
        GameServer.setGameEngineClassName("com.fenrissoftwerks.loki.integtest.Main$IntegTestEngine");
        GameServer server = GameServer.getInstance();

        server.addCommandHandlerForCommandName("echo", "com.fenrissoftwerks.loki.integtest.Main$EchoCommandHandler");
        server.addCommandHandlerForCommandName("watchSomething", "com.fenrissoftwerks.loki.integtest.Main$EchoCommandHandler");
        server.addCommandHandlerForCommandName("pingSomething", "com.fenrissoftwerks.loki.integtest.Main$EchoCommandHandler");

        if(args.length >= 1) {
            try {
                port = Integer.valueOf(args[0]);
                try {
                    server.startServer(port);
                } catch (Exception e) {
                    System.out.println("Couldn't start server on port: " + port + "\n " + e.getStackTrace());
                }
            } catch (NumberFormatException e) {
                System.out.println("Not a number for port: " + args[0]);
            }
        } else {
            try {
                server.startServer();
            } catch (Exception e) {
                System.out.println("Couldn't start server on default port: " + e.getStackTrace());
            }
        }

        if(args.length >= 2) {
            try {
                wsPort = Integer.valueOf(args[1]);
                try {
                    server.startWebSocketServer(wsPort);
                } catch (Exception e) {
                    System.out.println("Couldn't start server on port: " + wsPort + "\n " + e.getStackTrace());
                }
            } catch (NumberFormatException e) {
                System.out.println("Not a number for wsPort: " + args[1]);
            }
        } else {
            try {
                server.startWebSocketServer();
            } catch (Exception e) {
                System.out.println("Couldn't start WS server on default port: " + e.getStackTrace());
            }
        }

        // Just spin until terminated
        while(true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
