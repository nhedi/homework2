package client.view;

/**
 * Defines all commands that can be performed by a user of the hangman application.
 */
public enum Command {
    /**
     * Specifies a user name. This name will be prepended to all entries in the game.
     */
    USER,
    /**
     * Establish a connection to the server. The first parameter is IP address (or host name), the
     * second is port number.
     */
    CONNECT,
    /**
     * Leave the game.
     */
    QUIT,
    /**
     * Specifies a guess from a player.
     */
    GUESS,
    /**
     * No command was specified. This means the entire command line is interpreted as an entry in
     * the conversation, and is sent to all clients.
     */
    NO_COMMAND
}
