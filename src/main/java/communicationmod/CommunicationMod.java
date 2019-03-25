package communicationmod;

import basemod.BaseMod;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Thread.sleep;

@SpireInitializer
public class CommunicationMod implements PostInitializeSubscriber, PostDungeonUpdateSubscriber, PreUpdateSubscriber {

    private static Process listener;
    private static StringBuilder inputBuffer = new StringBuilder();
    public static boolean messageReceived = false;
    private static final Logger logger = LogManager.getLogger(CommunicationMod.class.getName());
    private static Thread writeThread;
    private static BlockingQueue<String> writeQueue;
    private static Thread readThread;
    private static BlockingQueue<String> readQueue;
    private static final String MODNAME = "Communication Mod";
    private static final String AUTHOR = "Forgotten Arbiter";
    private static final String DESCRIPTION = "This mod communicates with an external program to play Slay the Spire.";
    public static boolean waitingForCommand = false;
    private boolean sentGameState = false;

    public CommunicationMod(){
        BaseMod.subscribe(this);
        ProcessBuilder builder = new ProcessBuilder("python", "main.py");
        File templog = new File("log.txt");
        //builder.redirectOutput(ProcessBuilder.Redirect.appendTo(templog));
        //builder.redirectError(ProcessBuilder.Redirect.appendTo(templog));
        try {
            listener = builder.start();
        } catch (IOException e) {
            logger.error("Could not start external process.");
            e.printStackTrace();
        }
        startCommunicationThreads();
    }

    public static void initialize() {
        CommunicationMod mod = new CommunicationMod();
    }

    public void receivePreUpdate() {
        if(messageAvailable()) {
            try {
                CommandExecutor.executeCommand(readMessage());
            } catch (InvalidCommandException e) {
                sendMessage(String.format("{\"error\": \"%s\"}", e.getMessage()));
            }
        }
    }

    public void receivePostInitialize() {
        sendMessage("Initialization Completed.");
        logger.info(readMessageBlocking());
    }

    public void receivePostDungeonUpdate() {
        if(testForStateChange()) {
            sendGameState();
        }
        if(AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateConverter.signalTurnEnd();
        }
    }

    private void startCommunicationThreads() {
        writeQueue = new LinkedBlockingQueue<String>();
        writeThread = new Thread(new DataWriter(writeQueue, listener.getOutputStream()));
        writeThread.start();
        readQueue = new LinkedBlockingQueue<>();
        readThread = new Thread(new DataReader(readQueue, listener.getInputStream()));
        readThread.start();
    }

    private static void sendGameState() {
        String state = GameStateConverter.getGameStateGson();
        sendMessage(state);
    }

    public static void dispose() {
        logger.info("Shutting down child process...");
        listener.destroy();
    }

    public static void sendMessage(String message) {
        writeQueue.add(message);
    }

    public static boolean messageAvailable() {
        return !readQueue.isEmpty();
    }

    public static String readMessage() {
        if(messageAvailable()) {
            return readQueue.remove();
        } else {
            return null;
        }
    }

    public static String readMessageBlocking() {
        try {
            return readQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to read message from subprocess.");
        }
    }

    public static boolean testForStateChange() {
        boolean hasStateChanged = GameStateConverter.hasStateChanged();
        if (hasStateChanged) {
            waitingForCommand = true;
        }
        return hasStateChanged;
    }

}
