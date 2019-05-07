package communicationmod.patches;

import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import communicationmod.GameStateListener;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

@SpirePatch(
        clz= AbstractRelic.class,
        method="update"
)
public class AbstractRelicUpdatePatch {

    public static AbstractRelic hoverRelic;
    public static boolean doHover = false;

    @SpireInsertPatch(
            locator=ObtainedLocator.class
    )
    public static void BlockStateChange(AbstractRelic _instance) {
        // A relic's equip code isn't actually called until it reaches the top of the screen.
        // To avoid problems, we cannot report a state update until this happens.
        if(_instance.isObtained) {
            GameStateListener.blockStateUpdate();
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
        GameStateListener.resumeStateUpdate();
    }

    private static class EquipLocator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.MethodCallMatcher(AbstractRelic.class, "onEquip");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
        }
    }

    @SpireInsertPatch(
            locator=HitboxLocator.class
    )
    public static void DoHitboxHover(AbstractRelic _instance) {
        if(doHover) {
            if(hoverRelic == _instance) {
                _instance.hb.hovered = true;
                doHover = false;
            } else {
                _instance.hb.hovered = false;
            }
        }
    }

    private static class HitboxLocator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.MethodCallMatcher(Hitbox.class, "update");
            int[] results = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
            results[0] += 1;
            return results;
        }
    }
}
