package communicationmod.patches;

import basemod.BaseMod;
import basemod.patches.com.megacrit.cardcrawl.dungeons.AbstractDungeon.CustomBosses;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.TheBeyond;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SpirePatch(
        clz= CustomBosses.AddBosses.class,
        method="Do"
)
public class BaseModDungeonInitializationPatch {

    // Just a little patch to help me with testing stuff while BaseMod is on
    public static void Replace(AbstractDungeon dungeon) {
        if(AbstractDungeon.bossList.size() == 1) {
            return;
        }
        List<String> bossIds = BaseMod.getBossIDs(AbstractDungeon.id);
        if(!bossIds.isEmpty()) {
            AbstractDungeon.bossList.addAll(BaseMod.getBossIDs(AbstractDungeon.id));
            Collections.shuffle(AbstractDungeon.bossList);
        }
        // Just a little thing to add in the act 3 event for testing purposes
        if(AbstractDungeon.id.equals(TheBeyond.ID)) {
            CardCrawlGame.playtime = 900.0f;
        }
    }

}
