package communicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;

public class DataWriter implements Runnable {

    private final BlockingQueue<String> queue;
    private final OutputStream stream;
    private boolean verbose;
    private static final Logger logger = LogManager.getLogger(DataWriter.class.getName());

    public DataWriter(BlockingQueue<String> queue, OutputStream stream, boolean verbose) {
        this.queue = queue;
        this.stream = stream;
        this.verbose = verbose;
    }

    public void run() {
        String message = "";
        while (!Thread.currentThread().isInterrupted()) {
            try {
                message = this.queue.take();
                if (verbose) {
                    logger.info("Sending message: " + message);
                }
                stream.write(message.getBytes());
                stream.write('\n');
                stream.flush();
            } catch (InterruptedException e) {
                logger.info("Communications writing thread interrupted.");
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                logger.error("Message could not be sent to child process: " + message);
                e.printStackTrace();
            }
        }
    }
}
