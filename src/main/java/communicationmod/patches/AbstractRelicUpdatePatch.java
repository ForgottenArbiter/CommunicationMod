package communicationmod.patches;

import com.megacrit.cardcrawl.relics.AbstractRelic;
import communicationmod.GameStateConverter;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.helpers.Hitbox;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

@SpirePatch(
        clz= AbstractRelic.class,
        method="update"
)
public class AbstractRelicUpdatePatch {

    @SpireInsertPatch(
            locator=ObtainedLocator.class
    )
    public static void BlockStateChange(AbstractRelic _instance) {
        // A relic's equip code isn't actually called until it reaches the top of the screen.
        // To avoid problems, we cannot report a state update until this happens.
        if(_instance.isObtained) {
            System.out.println("blocking state update...");
            GameStateConverter.blockStateUpdate();
        }
    }

    private static class ObtainedLocator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.FieldAccessMatcher(AbstractRelic.class, "isObtained");
            int[] results = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
            results[0] += 1;
            return results;
        }
    }

    @SpireInsertPatch(
            locator=EquipLocator.class
    )
    public static void ResumeStateChange(AbstractRelic _instance) {
        GameStateConverter.resumeStateUpdate();
    }

    private static class EquipLocator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.MethodCallMatcher(AbstractRelic.class, "onEquip");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
        }
    }

}
