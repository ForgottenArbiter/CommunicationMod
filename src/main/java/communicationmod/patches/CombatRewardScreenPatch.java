package communicationmod.patches;

import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.CombatRewardScreen;
import communicationmod.GameStateConverter;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

@SpirePatch(
        clz= CombatRewardScreen.class,
        method="rewardViewUpdate"
)
public class CombatRewardScreenPatch {


    @SpireInsertPatch(
            locator=Locator.class
    )
    public static void Insert(CombatRewardScreen _instance) {
        // This will deal with linked relics / keys
        for(RewardItem reward : _instance.rewards) {
            if (reward.isDone) {
                return;
            }
        }
        GameStateConverter.registerStateChange();
    }

    private static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.MethodCallMatcher(CombatRewardScreen.class, "setLabel");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
        }
    }
}
