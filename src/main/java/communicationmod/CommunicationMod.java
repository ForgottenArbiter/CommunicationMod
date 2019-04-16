package communicationmod;

import basemod.BaseMod;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

@SpireInitializer
public class CommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber, PreUpdateSubscriber {

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
    public static boolean mustSendGameState = false;

    private static SpireConfig communicationConfig;
    private static final String COMMAND_OPTION = "command";
    private static final String GAME_START_OPTION = "runAtGameStart";
    private static final String INITIALIZATION_TIMEOUT_OPTION = "maxInitializationTimeout";
    private static final long DEFAULT_TIMEOUT = 10L;

    public CommunicationMod(){
        BaseMod.subscribe(this);

        try {
            Properties defaults = new Properties();
            defaults.put(COMMAND_OPTION, "python main.py");
            defaults.put(GAME_START_OPTION, Boolean.toString(true));
            defaults.put(INITIALIZATION_TIMEOUT_OPTION, Long.toString(DEFAULT_TIMEOUT));
            communicationConfig = new SpireConfig("CommunicationMod", "config", defaults);
            communicationConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(getRunOnGameStartOption()) {
            boolean success = startExternalProcess();
        }
    }

    public static void initialize() {
        CommunicationMod mod = new CommunicationMod();
    }

    public void receivePreUpdate() {
        if(messageAvailable()) {
            try {
                boolean stateChanged = CommandExecutor.executeCommand(readMessage());
                if(stateChanged) {
                    GameStateConverter.registerCommandExecution();
                }
            } catch (InvalidCommandException e) {
                HashMap<String, Object> jsonError = new HashMap<>();
                jsonError.put("error", e.getMessage());
                Gson gson = new Gson();
                sendMessage(gson.toJson(jsonError));
            }
        }
    }

    public void receivePostInitialize() {
    }

    public void receivePostUpdate() {
        if(!mustSendGameState && GameStateConverter.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
        if(mustSendGameState) {
            sendGameState();
            mustSendGameState = false;
        }
    }
     public void receivePostDungeonUpdate() {
         if (GameStateConverter.checkForDungeonStateChange()) {
             mustSendGameState = true;
         }
         if(AbstractDungeon.getCurrRoom().isBattleOver) {
             GameStateConverter.signalTurnEnd();
         }
     }

    private void startCommunicationThreads() {
        writeQueue = new LinkedBlockingQueue<>();
        writeThread = new Thread(new DataWriter(writeQueue, listener.getOutputStream()));
        writeThread.start();
        readQueue = new LinkedBlockingQueue<>();
        readThread = new Thread(new DataReader(readQueue, listener.getInputStream()));
        readThread.start();
    }

    private static void sendGameState() {
        String state = GameStateConverter.getCommunicationState();
        sendMessage(state);
    }

    public static void dispose() {
        logger.info("Shutting down child process...");
        listener.destroy();
    }

    private static void sendMessage(String message) {
        if(writeQueue != null && writeThread.isAlive()) {
            writeQueue.add(message);
        }
    }

    private static boolean messageAvailable() {
        return readQueue != null && !readQueue.isEmpty();
    }

    private static String readMessage() {
        if(messageAvailable()) {
            return readQueue.remove();
        } else {
            return null;
        }
    }

    private static String readMessageBlocking() {
        try {
            return readQueue.poll(getInitializationTimeoutOption(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to read message from subprocess.");
        }
    }

    private static String[] getSubprocessCommand() {
        if (communicationConfig == null) {
            return new String[0];
        }
        return communicationConfig.getString(COMMAND_OPTION).trim().split("\\s+");
    }

    private static boolean getRunOnGameStartOption() {
        if (communicationConfig == null) {
            return false;
        }
        return communicationConfig.getBool(GAME_START_OPTION);
    }

    private static long getInitializationTimeoutOption() {
        if (communicationConfig == null) {
            return DEFAULT_TIMEOUT;
        }
        return (long)communicationConfig.getInt(INITIALIZATION_TIMEOUT_OPTION);
    }

    private boolean startExternalProcess() {
        if(readThread != null) {
            readThread.interrupt();
        }
        if(writeThread != null) {
            writeThread.interrupt();
        }
        if(listener != null) {
            listener.destroy();
        }
        // TODO: Check compatibility for non-Windows OS here:
        ProcessBuilder builder = new ProcessBuilder(getSubprocessCommand());
        File errorLog = new File("communication_mod_errors.log");
        builder.redirectError(ProcessBuilder.Redirect.appendTo(errorLog));
        try {
            listener = builder.start();
        } catch (IOException e) {
            logger.error("Could not start external process.");
            e.printStackTrace();
        }
        if(listener != null) {
            startCommunicationThreads();
            // We wait for the child process to signal it is ready before we proceed. Note that the game
            // will hang while this is occurring. I may wish to change this behavior at a later point.
            String message = readMessageBlocking();
            if(message == null) {
                // The child process waited too long to respond, so we kill it.
                readThread.interrupt();
                writeThread.interrupt();
                listener.destroy();
                logger.error("Timed out while waiting for signal from external process.");
                return false;
            } else {
                logger.info(String.format("Received message from external process: %s", message));
                return true;
            }
        }
        return false;
    }

}
