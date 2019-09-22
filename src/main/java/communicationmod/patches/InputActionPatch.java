package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.helpers.input.InputAction;

public class InputActionPatch {

    public static boolean doKeypress = false;
    public static int key = 0;

    @SpirePatch(
            clz= InputAction.class,
            method="isJustPressed"
    )
    public static class JustPressedPatch {

        public static SpireReturn<Boolean> Prefix(InputAction _instance, int ___keycode) {
            if (doKeypress && ___keycode == key) {
                return SpireReturn.Return(true);
            } else {
                return SpireReturn.Continue();
            }
        }

    }

    @SpirePatch(
            clz=InputAction.class,
            method="isPressed"
    )
    public static class PressedPatch {

        public static SpireReturn<Boolean> Prefix(InputAction _instance, int ___keycode) {
            if (doKeypress && ___keycode == key) {
                return SpireReturn.Return(true);
            } else {
                return SpireReturn.Continue();
            }
        }

    }
}
