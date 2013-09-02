package com.fenrissoftwerks.loki;

import java.util.Arrays;

/**
 * Command - The Command class represents a command to execute either on the server or the client.  It consists of a
 * command name and an array of Objects which make up the arguments to the command.  A GameEngine or client can
 * examine the name and based on that call the appropriate method.
 */
public class Command {

    private String commandName;
    private Object[] commandArgs;

    public Command(String commandName, Object[] commandArgs) {
        this.commandName = commandName;
        this.commandArgs = commandArgs;
    }

    public Command() {
    }

    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public Object[] getCommandArgs() {
        return commandArgs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Command command = (Command) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(commandArgs, command.commandArgs)) return false;
        if (commandName != null ? !commandName.equals(command.commandName) : command.commandName != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = commandName != null ? commandName.hashCode() : 0;
        result = 31 * result + (commandArgs != null ? Arrays.hashCode(commandArgs) : 0);
        return result;
    }

    public void setCommandArgs(Object[] commandArgs) {
        this.commandArgs = commandArgs;
    }
}
