package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.vfx.campfire.CampfireSleepEffect;
import communicationmod.GameStateConverter;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

@SpirePatch(
        clz= CampfireSleepEffect.class,
        method="update"
)
public class CampfireSleepEffectPatch {

    @SpireInsertPatch(
            locator=Locator.class
    )
    public static void Insert(CampfireSleepEffect _instance) {
        GameStateConverter.resumeStateUpdate();
    }

    private static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.FieldAccessMatcher(CampfireSleepEffect.class, "isDone");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
        }
    }

}

