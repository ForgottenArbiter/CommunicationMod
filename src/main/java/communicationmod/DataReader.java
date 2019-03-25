package communicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;

public class DataReader implements Runnable{

    private final BlockingQueue<String> queue;
    private final InputStream stream;
    private static final Logger logger = LogManager.getLogger(DataReader.class.getName());

    public DataReader (BlockingQueue<String> queue, InputStream stream) {
        this.queue = queue;
        this.stream = stream;
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            StringBuilder inputBuffer = new StringBuilder();
            try {
                while (true) {
                    int nextChar = this.stream.read();
                    if (nextChar == -1) {
                        continue;
                    } else if (nextChar == 0 || nextChar == '\n') {
                        break;
                    }
                    inputBuffer.append((char) nextChar);
                }
                if (inputBuffer.length() > 0) {
                    logger.info("Received message: " + inputBuffer.toString());
                    queue.put(inputBuffer.toString());
                }
            } catch(IOException e){
                logger.error("Message could not be received from child process. Shutting down reading thread.");
                Thread.currentThread().interrupt();
            } catch (InterruptedException e) {
                logger.info("Communications reading thread interrupted.");
                Thread.currentThread().interrupt();
            }
        }
    }
}
