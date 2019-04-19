package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

public class CardRewardScreenPatch {

    public static boolean doHover = false;
    public static AbstractCard hoverCard;

    @SpirePatch(
            clz=CardRewardScreen.class,
            method = "cardSelectUpdate"
    )
    public static class HoverCardPatch {

        @SpireInsertPatch(
                locator=Locator.class,
                localvars = {"c"}
        )
        public static void Insert(CardRewardScreen _instance, AbstractCard c) {
            if(doHover) {
                if(c.equals(hoverCard)) {
                    hoverCard.hb.hovered = true;
                } else {
                    c.hb.hovered = false;
                }
            }
        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.MethodCallMatcher(AbstractCard.class, "updateHoverLogic");
                int[] match = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
                match[0] += 1;
                return match;
            }
        }

    }

    @SpirePatch(
            clz=CardRewardScreen.class,
            method = "cardSelectUpdate"
    )
    public static class AcquireCardPatch {

        @SpireInsertPatch(
                locator=Locator.class
        )
        public static void Insert(CardRewardScreen _instance) {
            doHover = false;
        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.FieldAccessMatcher(CardRewardScreen.class, "skipButton");
                return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
            }
        }

    }
}
