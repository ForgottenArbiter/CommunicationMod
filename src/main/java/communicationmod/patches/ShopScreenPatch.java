package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.shop.ShopScreen;
import communicationmod.GameStateConverter;

@SpirePatch(
        clz= ShopScreen.class,
        method="purgeCard"
)
public class ShopScreenPatch {

    public static void Postfix() {
        GameStateConverter.resumeStateUpdate();  // Needed to wait for the rest of the logic to complete after card was selected.
    }

}
