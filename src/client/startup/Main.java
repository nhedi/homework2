package client.startup;

import client.view.NonBlockingInterpreter;

/**
 * Starts the player client.
 */
public class Main {
    /**
     * @param args There are no command line arguments.
     */
    public static void main(String[] args) {
        new NonBlockingInterpreter().start();
    }
}
