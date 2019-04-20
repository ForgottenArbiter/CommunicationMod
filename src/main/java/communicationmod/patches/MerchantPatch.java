package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.shop.Merchant;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

public class MerchantPatch {

    public static boolean visitMerchant = false;

    @SpirePatch(
            clz=Merchant.class,
            method="update"
    )
    public static class MerchantUpdatePatch {

        @SpireInsertPatch(
                locator=Locator.class
        )
        public static void Insert(Merchant _instance) {
            if(visitMerchant) {
                _instance.hb.hovered = true;
                InputHelper.justClickedLeft = true;
                visitMerchant = false;
            }

        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.MethodCallMatcher(Hitbox.class, "update");
                int[] results = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
                results[0] += 1;
                return results;
            }
        }

    }

}
