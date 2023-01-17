package communicationmod;

import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;

import java.util.ArrayList;

public class CommsCommand extends ConsoleCommand {

        public CommsCommand() {
            maxExtraTokens = 1;
            minExtraTokens = 1;
            requiresPlayer = false;
        }

        private static void cmdHelp() {
            DevConsole.couldNotParse();
            DevConsole.log("options are:");
            DevConsole.log("* pause");
            DevConsole.log("* play");
            DevConsole.log("* restart");
        }

        public void execute(String[] tokens, int depth) {
            switch (tokens[1].toLowerCase()) {
                case "pause":
                    CommunicationMod.paused = true;
                    break;
                case "play":
                    CommunicationMod.paused = false;
                    break;
                case "restart":
                    CommunicationMod.paused = false;
                    CommunicationMod.mustSendGameState = true;
            }
        }

        @Override
        public ArrayList<String> extraOptions(String[] tokens, int depth) {
            ArrayList<String> result = new ArrayList<>();
            result.add("pause");
            result.add("play");
            result.add("restart");

            if (tokens.length == depth + 1) {
                return result;
            } else if (result.contains(tokens[depth])) {
                complete = true;
                result = new ArrayList<>();
            }
            return result;
        }

        @Override
        public void errorMsg() {
            cmdHelp();
        }

}
