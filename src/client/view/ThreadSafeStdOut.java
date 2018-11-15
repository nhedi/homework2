package client.view;

/**
 * This class provides a thread safe output, all methods are synchronized. 
 */
class ThreadSafeStdOut {
    /**
     * Prints the specified output to <code>System.out</code>,
     * 
     * @param output The output to print. 
     */
    synchronized void print(String output) {
        System.out.print(output);
    }

    /**
     * Prints the specified output, plus a line break, to <code>System.out</code>,
     * 
     * @param output The output to print. 
     */
    synchronized void println(String output) {
        System.out.println(output);
    }
}
