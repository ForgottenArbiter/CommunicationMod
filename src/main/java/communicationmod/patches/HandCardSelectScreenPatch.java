package communicationmod.patches;


import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import communicationmod.GameStateConverter;

@SpirePatch(
        clz= HandCardSelectScreen.class,
        method="selectHoveredCard"
)
public class HandCardSelectScreenPatch {

    public static void Postfix(HandCardSelectScreen _instance) {
        // If the card selection was going to trigger a screen close due to the quick card select option, don't register the state change.
        if (!(Settings.FAST_HAND_CONF && _instance.numCardsToSelect == 1 && _instance.selectedCards.size() == 1 && !_instance.canPickZero)) {
            GameStateConverter.registerStateChange();

        }
    }

}
