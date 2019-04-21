package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.common.EnableEndTurnButtonAction;
import communicationmod.GameStateListener;

@SpirePatch(
        clz = EnableEndTurnButtonAction.class,
        method = "update"
)
public class EnableEndTurnPatch {
    public static void Postfix(EnableEndTurnButtonAction _instance) {
        GameStateListener.signalTurnStart();
    }
}
