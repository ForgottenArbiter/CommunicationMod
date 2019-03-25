package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import communicationmod.EndOfTurnAction;

@SpirePatch(
        clz= GameActionManager.class,
        method="callEndOfTurnActions"
)
public class GameActionManagerEndTurnPatch {
    public static void Postfix(GameActionManager _instance) {
        AbstractDungeon.actionManager.addToBottom(new EndOfTurnAction());
    }
}
