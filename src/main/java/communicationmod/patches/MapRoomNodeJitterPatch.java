package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.map.MapRoomNode;
import communicationmod.CommunicationMod;

@SpirePatch2(
        clz = MapRoomNode.class,
        method = SpirePatch.CONSTRUCTOR
)
public class MapRoomNodeJitterPatch {

    public static void Postfix(MapRoomNode __instance) {
        float JITTER_X = Settings.isMobile ? (13.0F * Settings.xScale) : (27.0F * Settings.xScale);
        float JITTER_Y = Settings.isMobile ? (18.0F * Settings.xScale) : (37.0F * Settings.xScale);
        __instance.offsetX = (int) CommunicationMod.mapPositionRng.random(-JITTER_X, JITTER_X);
        __instance.offsetY = (int) CommunicationMod.mapPositionRng.random(-JITTER_Y, JITTER_Y);
    }

}
