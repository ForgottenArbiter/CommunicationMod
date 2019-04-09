package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.map.MapRoomNode;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;
import java.util.Map;

@SpirePatch(
        clz= MapRoomNode.class,
        method="update"
)
public class MapRoomNodeHoverPatch {

    public static MapRoomNode hoverNode;
    public static boolean doHover = false;

    @SpireInsertPatch(
            locator=Locator.class
    )
    public static void Insert(MapRoomNode _instance) {
        if(doHover && hoverNode == _instance) {
            _instance.hb.hovered = true;
            doHover = false;
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
