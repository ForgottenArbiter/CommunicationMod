package communicationmod.patches;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import communicationmod.CommunicationMod;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

@SpirePatch(
        clz = CardCrawlGame.class,
        method = "update"
)
public class DungeonUpdatePatch {

    @SpireInsertPatch(
            locator = Locator.class
    )
    public static SpireReturn<Void> Insert(CardCrawlGame _instance) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) {
            return SpireReturn.Continue();
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) {
            if (CommunicationMod.gamePaused) {
                CommunicationMod.gamePaused = false;
                return SpireReturn.Continue();
            } else {
                CommunicationMod.gamePaused = true;
            }
        }
        if (CommunicationMod.gamePaused) {
            return SpireReturn.Return();
        } else {
            return SpireReturn.Continue();
        }
    }


    private static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.FieldAccessMatcher(CardCrawlGame.class, "cardPopup");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
        }
    }

}
