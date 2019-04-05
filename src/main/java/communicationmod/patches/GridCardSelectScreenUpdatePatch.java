package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import communicationmod.GameStateConverter;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

import static com.evacipated.cardcrawl.modthespire.lib.LineFinder.findAllInOrder;

@SpirePatch(
        clz= GridCardSelectScreen.class,
        method="update"
)
public class GridCardSelectScreenUpdatePatch {

    @SpireInsertPatch(
            locator=Locator.class
    )
    public static void Insert(GridCardSelectScreen _instance) {
        GameStateConverter.registerStateChange();
    }

    private static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.FieldAccessMatcher(Hitbox.class, "clicked");
            int[] matches = LineFinder.findAllInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
            int[] selectedMatches = new int[matches.length/2];
            for(int i = 0; i < matches.length; i++) {
                if(i % 2 == 1) {
                    selectedMatches[i/2] = matches[i]; // Take every other access to hb.clicked, as the others are in if statements
                }
            }
            return selectedMatches;
        }
    }

}
