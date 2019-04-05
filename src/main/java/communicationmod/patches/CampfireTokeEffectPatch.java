package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.metrics.MetricData;
import com.megacrit.cardcrawl.vfx.campfire.CampfireTokeEffect;
import communicationmod.GameStateConverter;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

@SpirePatch(
        clz= CampfireTokeEffect.class,
        method="update"
)
public class CampfireTokeEffectPatch {

    @SpireInsertPatch(
            locator=LocatorAfter.class
    )
    public static void After(CampfireTokeEffect _instance) {
        GameStateConverter.resumeStateUpdate();
    }

    private static class LocatorAfter extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.FieldAccessMatcher(CampfireTokeEffect.class, "isDone");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
        }
    }

    @SpireInsertPatch(
            locator=LocatorBefore.class
    )
    public static void Before(CampfireTokeEffect _instance) {
        GameStateConverter.blockStateUpdate();
    }

    private static class LocatorBefore extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.MethodCallMatcher(MetricData.class, "addCampfireChoiceData");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
        }
    }
}
