package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import communicationmod.GameStateConverter;

@SpirePatch(
        clz= GenericEventDialog.class,
        method="setDialogOption",
        paramtypez = {String.class}
)
public class SetDialogOptionPatch {
    public static void Postfix(GenericEventDialog _instance, String _arg) {
        GameStateConverter.registerStateChange();
    }
}
