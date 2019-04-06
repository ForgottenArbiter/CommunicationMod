package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.ui.DialogWord;

public class UpdateBodyTextPatch {

    public static String bodyText = "";

    @SpirePatch(
            clz= RoomEventDialog.class,
            method = "updateBodyText",
            paramtypez = {String.class, DialogWord.AppearEffect.class}
    )
    public static class RoomEventPatch {
        public static void Prefix(RoomEventDialog _instance, String text, DialogWord.AppearEffect ae) {
            bodyText = text;
        }
    }

    @SpirePatch(
            clz= GenericEventDialog.class,
            method = "updateBodyText",
            paramtypez = {String.class, DialogWord.AppearEffect.class}
    )
    public static class ImageEventPatch {
        public static void Prefix(GenericEventDialog _instance, String text, DialogWord.AppearEffect ae) {
            bodyText = text;
        }
    }
}
