package communicationmod.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.events.shrines.GremlinMatchGame;
import communicationmod.GameStateConverter;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.*;

public class GremlinMatchGamePatch {

    public static HashMap<UUID, Integer> cardPositions;

    @SpirePatch(
            clz=GremlinMatchGame.class,
            method=SpirePatch.CONSTRUCTOR
    )
    public static class InitializeCardsPatch {

        public static void Postfix(GremlinMatchGame _instance) {
            CardGroup cards = (CardGroup) ReflectionHacks.getPrivate(_instance, GremlinMatchGame.class, "cards");
            // If 0 is top left and 11 is bottom right, the positions of the cards in the result array are:
            // [0, 5, 10, 3, 4, 9, 2, 7, 8, 1, 6, 11]. We want to store the initial positions for easy reference.
            // Cards can be removed from the card group, so it is easier to just calculate them at the start.
            cardPositions = new HashMap<>();
            for(int i = 0; i < 12; i++) {
                AbstractCard currentCard = cards.group.get(i);
                int target_x = i % 4;
                int target_y = i % 3;
                int position = target_x + 4 * target_y;
                cardPositions.put(currentCard.uuid, position);
                System.out.println(currentCard.uuid);
                System.out.println(currentCard.cardID);
            }
        }

    }


    @SpirePatch(
            clz=GremlinMatchGame.class,
            method="updateMatchGameLogic"
    )
    public static class WaitForCardFlipPatch {

        @SpireInsertPatch(
                locator=Locator.class
        )
        public static void Insert(GremlinMatchGame _instance) {
            // We have to wait for the cards to flip or everything goes wrong. Nothing else detects this change.
            int attemptCount = (int) ReflectionHacks.getPrivate(_instance, GremlinMatchGame.class, "attemptCount");
            if(attemptCount > 0) {
                GameStateConverter.registerStateChange();
            }
        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.FieldAccessMatcher(GremlinMatchGame.class, "attemptCount");
                int[] result = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
                result[0] += 1;
                return result;
            }
        }

    }

    @SpirePatch(
            clz=GremlinMatchGame.class,
            method="updateMatchGameLogic"
    )
    public static class RegisterFirstFlipPatch{

        @SpireInsertPatch(
                locator=Locator.class
        )
        public static void Insert(GremlinMatchGame _instance) {
            GameStateConverter.registerStateChange();
        }

        /*
            This locator tries to find the line this.chosenCard = this.hoveredCard, which indicates the first flip of a match
         */
        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher chosenMatcher = new Matcher.FieldAccessMatcher(GremlinMatchGame.class, "chosenCard");
                Matcher hoveredMatcher = new Matcher.FieldAccessMatcher(GremlinMatchGame.class, "hoveredCard");
                int[] chosenMatches = LineFinder.findAllInOrder(ctMethodToPatch, new ArrayList<Matcher>(), chosenMatcher);
                int[] hoveredMatches = LineFinder.findAllInOrder(ctMethodToPatch, new ArrayList<Matcher>(), hoveredMatcher);
                // Not the most computationally efficient way to do this, but it should only be run once anyway.
                for (int waitMatch : chosenMatches) {
                    for (int gameDoneMatch : hoveredMatches) {
                        if (waitMatch == gameDoneMatch) {
                            int[] match = new int[1];
                            match[0] = waitMatch;
                            return match;
                        }
                    }
                }
                throw new PatchingException("Could not find patching location for RegisterFirstFlipPatch in GremlinMatchGame.");
            }
        }

    }
}
