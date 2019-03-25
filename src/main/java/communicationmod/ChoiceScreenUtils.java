package communicationmod;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.TheEnding;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.events.shrines.GremlinMatchGame;
import com.megacrit.cardcrawl.events.shrines.GremlinWheelGame;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.map.DungeonMap;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.shop.Merchant;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.buttons.*;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import communicationmod.patches.GridCardSelectScreenPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ChoiceScreenUtils {

    private static final Logger logger = LogManager.getLogger(ChoiceScreenUtils.class.getName());

    public enum ChoiceType {
        EVENT,
        CHEST,
        SHOP_ROOM,
        REST,
        CARD_REWARD,
        COMBAT_REWARD,
        MAP,
        BOSS_REWARD,
        SHOP_SCREEN,
        GRID,
        INVALID
    }

    public static ArrayList<String> getCardRewardScreenChoices() {
        SkipCardButton skipButton = (SkipCardButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, CardRewardScreen.class, "skipButton");
        SingingBowlButton bowlButton = (SingingBowlButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, CardRewardScreen.class, "bowlButton");
        boolean skipAvailable = !(boolean) ReflectionHacks.getPrivate(skipButton, SkipCardButton.class, "isHidden");
        boolean bowlAvailable = !(boolean) ReflectionHacks.getPrivate(bowlButton, SingingBowlButton.class, "isHidden");
        ArrayList<String> choices = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.cardRewardScreen.rewardGroup) {
            choices.add(card.name.toLowerCase());
        }
        if(skipAvailable) {
            choices.add("skip");
        }
        if(bowlAvailable) {
            choices.add("bowl");
        }
        return choices;
    }

    public static void makeCardRewardChoice(int choice) {
        ArrayList<String> choices = getCardRewardScreenChoices();
        if(choices.get(choice).equals("skip")) {
            AbstractDungeon.closeCurrentScreen();
        } else if(choices.get(choice).equals("bowl")) {
            SingingBowlButton bowlButton = (SingingBowlButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, CardRewardScreen.class, "bowlButton");
            bowlButton.onClick();
            AbstractDungeon.cardRewardScreen.closeFromBowlButton();
            AbstractDungeon.closeCurrentScreen();
        } else {
            AbstractCard selectedCard = AbstractDungeon.cardRewardScreen.rewardGroup.get(choice);
            Gdx.input.setCursorPosition((int)selectedCard.hb.cX, (int)(Settings.HEIGHT - selectedCard.hb.cY));
            selectedCard.hb.clicked = true;
            selectedCard.hb.hovered = true;
        }
    }

    public static ChoiceType getCurrentChoiceType() {
        if (!AbstractDungeon.isScreenUp) {
            if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.EVENT) {
                return ChoiceType.EVENT;
            } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss || AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
                return ChoiceType.CHEST;
            } else if (AbstractDungeon.getCurrRoom() instanceof ShopRoom) {
                return ChoiceType.SHOP_ROOM;
            } else if (AbstractDungeon.getCurrRoom() instanceof RestRoom) {
                return ChoiceType.REST;
            } else {
                return ChoiceType.INVALID;
            }
        }
        AbstractDungeon.CurrentScreen screen = AbstractDungeon.screen;
        switch(screen) {
            case CARD_REWARD:
                return ChoiceType.CARD_REWARD;
            case COMBAT_REWARD:
                return ChoiceType.COMBAT_REWARD;
            case MAP:
                return ChoiceType.MAP;
            case BOSS_REWARD:
                return ChoiceType.BOSS_REWARD;
            case SHOP:
                return ChoiceType.SHOP_SCREEN;
            case GRID:
                return ChoiceType.GRID;
            default:
                return ChoiceType.INVALID;
        }
    }

    public static ArrayList<String> getCurrentChoiceList() {
        ChoiceType choiceType = getCurrentChoiceType();
        switch (choiceType) {
            case EVENT:
                return getEventScreenChoices();
            case CHEST:
                return getChestRoomChoices();
            case SHOP_ROOM:
                return getShopRoomChoices();
            case REST:
                return getRestRoomChoices();
            case CARD_REWARD:
                return getCardRewardScreenChoices();
            case COMBAT_REWARD:
                return getCombatRewardScreenChoices();
            case MAP:
                return getMapScreenChoices();
            case BOSS_REWARD:
                return getBossRewardScreenChoices();
            case SHOP_SCREEN:
                return getShopScreenChoices();
            case GRID:
                return getGridScreenChoices();
            default:
                return new ArrayList<>();
        }
    }

    public static void executeChoice(int choice_index) {
        ChoiceType choiceType = getCurrentChoiceType();
        switch (choiceType) {
            case EVENT:
                makeEventChoice(choice_index);
                return;
            case CHEST:
                makeChestRoomChoice(choice_index);
                return;
            case SHOP_ROOM:
                makeShopRoomChoice(choice_index);
                return;
            case REST:
                makeRestRoomChoice(choice_index);
                return;
            case CARD_REWARD:
                makeCardRewardChoice(choice_index);
                return;
            case COMBAT_REWARD:
                makeCombatRewardChoice(choice_index);
                return;
            case MAP:
                makeMapChoice(choice_index);
                return;
            case BOSS_REWARD:
                makeBossRewardChoice(choice_index);
                return;
            case SHOP_SCREEN:
                makeShopScreenChoice(choice_index);
                return;
            case GRID:
                makeGridScreenChoice(choice_index);
                return;
            default:
                logger.info("Unimplemented choice.");
        }
    }

    public static ArrayList<String> getGridScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        if(isGridScreenCancelAvailable()) {
            choices.add("skip");
        }
        if(isGridScreenConfirmAvailable()) {
            choices.add("confirm");
        }
        CardGroup cards = (CardGroup) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "targetGroup");
        for(AbstractCard card : cards.group) {
            choices.add(card.name.toLowerCase());
        }
        return choices;
    }

    public static void makeGridScreenChoice (int choice) {
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        if(isGridScreenCancelAvailable()) {
            if(choice == 0) {
                clickCancelButton();
                return;
            }
            choice -= 1;
        }
        if(isGridScreenConfirmAvailable()) {
            if(choice == 0) {
                screen.confirmButton.hb.clicked = true;
                if (AbstractDungeon.previousScreen == AbstractDungeon.CurrentScreen.SHOP) {
                    GameStateConverter.blockStateUpdate(); // The rest of the associated shop purge logic will not run in this update
                }
                return;
            }
            choice -= 1;
        }
        CardGroup cards = (CardGroup) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "targetGroup");
        GridCardSelectScreenPatch.hoverCard = cards.group.get(choice);
        GridCardSelectScreenPatch.replaceHoverCard = true;
    }

    private static boolean isGridScreenCancelAvailable() {
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        boolean canCancel = (boolean)ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "canCancel");
        if(canCancel && (screen.forPurge || screen.forTransform || screen.forUpgrade || (AbstractDungeon.previousScreen == AbstractDungeon.CurrentScreen.SHOP))) {
            return true;
        } else {
            return screen.confirmScreenUp;
        }
    }

    private static boolean isGridScreenConfirmAvailable() {
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        if (screen.confirmScreenUp || screen.isJustForConfirming) {
            return true;
        } else if ((!screen.confirmButton.isDisabled) && (!(boolean)ReflectionHacks.getPrivate(screen.confirmButton, GridSelectConfirmButton.class, "isHidden")) ) {
            if(screen.forUpgrade || screen.forTransform || screen.forPurge || screen.anyNumber) {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<String> getCombatRewardScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add("skip");
        for(RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            choices.add(reward.type.name().toLowerCase());
        }
        return choices;
    }

    public static void makeCombatRewardChoice(int choice) {
        ArrayList<String> choices = getCombatRewardScreenChoices();
        if(choices.get(choice).equals("skip")) {
            clickProceedButton();
        } else {
            RewardItem reward = AbstractDungeon.combatRewardScreen.rewards.get(choice - 1);
            reward.isDone = true;
        }
    }

    public static ArrayList<String> getBossRewardScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add("skip");
        for(AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
            choices.add(relic.name);
        }
        return choices;
    }

    public static void makeBossRewardChoice(int choice) {
        if(choice == 0) {
            AbstractDungeon.bossRelicScreen.cancelButton.hb.clicked = true;
        } else {
            AbstractRelic chosenRelic = AbstractDungeon.bossRelicScreen.relics.get(choice - 1);
            setCursorPosition(chosenRelic.hb.cX, Settings.HEIGHT - chosenRelic.hb.cY);
            InputHelper.justClickedLeft = true;
        }
    }

    public static ArrayList<String> getChestRoomChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add("skip");
        choices.add("open");
        return choices;
    }

    public static void makeChestRoomChoice (int choice) {
        ArrayList<String> choices = getChestRoomChoices();
        if(choices.get(choice).equals("skip")) {
            clickProceedButton();
        } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss) {
            ((TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest.open(true);
        } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
            ((TreasureRoom) AbstractDungeon.getCurrRoom()).chest.open(false);
        }
    }

    public static ArrayList<String> getShopRoomChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add("skip");
        choices.add("shop");
        return choices;
    }

    public static void makeShopRoomChoice (int choice) {
        ArrayList<String> choices = getShopRoomChoices();
        if(choices.get(choice).equals("skip")) {
            clickProceedButton();
        } else if (choices.get(choice).equals("shop")) {
            Merchant merchant = ((ShopRoom)AbstractDungeon.getCurrRoom()).merchant;
            setCursorPosition(merchant.hb.cX, Settings.HEIGHT - merchant.hb.cY);
            InputHelper.justClickedLeft = true;
        }
    }

    public static ArrayList<String> getShopScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add("skip");
        ShopScreen screen = AbstractDungeon.shopScreen;
        ArrayList<Object> shopItems = getAvailableShopItems();
        for (Object item : shopItems) {
            if (item instanceof String) {
                choices.add((String) item);
            } else if (item instanceof AbstractCard) {
                choices.add(((AbstractCard) item).name.toLowerCase());
            } else if (item instanceof StoreRelic) {
                choices.add(((StoreRelic)item).relic.name);
            } else if (item instanceof StorePotion) {
                choices.add(((StorePotion)item).potion.name);
            }
        }
        return choices;
    }

    private static ArrayList<Object> getAvailableShopItems() {
        ArrayList<Object> choices = new ArrayList<>();
        ShopScreen screen = AbstractDungeon.shopScreen;
        ArrayList<AbstractCard> coloredCards = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "coloredCards");
        ArrayList<AbstractCard> colorlessCards = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "colorlessCards");
        ArrayList<StoreRelic> relics = (ArrayList<StoreRelic>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "relics");
        ArrayList<StorePotion> potions = (ArrayList<StorePotion>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "potions");
        if(screen.purgeAvailable && AbstractDungeon.player.gold >= ShopScreen.actualPurgeCost) {
            choices.add("purge");
        }
        for(AbstractCard card : coloredCards) {
            if(card.price <= AbstractDungeon.player.gold) {
                choices.add(card);
            }
        }
        for(AbstractCard card : colorlessCards) {
            if(card.price <= AbstractDungeon.player.gold) {
                choices.add(card);
            }
        }
        for(StoreRelic relic : relics) {
            if(relic.price <= AbstractDungeon.player.gold) {
                choices.add(relic);
            }
        }
        for(StorePotion potion : potions) {
            if(potion.price <= AbstractDungeon.player.gold) {
                choices.add(potion);
            }
        }
        return choices;
    }

    public static void makeShopScreenChoice(int choice) {
        ArrayList<Object> shopItems = getAvailableShopItems();
        if(choice == 0) {
            clickCancelButton();
            return;
        }
        Object shopItem = shopItems.get(choice - 1);
        if (shopItem instanceof String) {
            AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.SHOP;
            AbstractDungeon.gridSelectScreen.open(
                    CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()),
                    1, ShopScreen.NAMES[13], false, false, true, true);
        } else if (shopItem instanceof AbstractCard) {
            AbstractCard card = (AbstractCard)shopItem;
            setCursorPosition(card.hb.cX, Settings.HEIGHT - card.hb.cY);
            card.hb.clicked = true;
        } else if (shopItem instanceof StoreRelic) {
            StoreRelic relic = (StoreRelic) shopItem;
            setCursorPosition(relic.relic.hb.cX, Settings.HEIGHT - relic.relic.hb.cY);
            relic.relic.hb.clicked = true;
        } else if (shopItem instanceof StorePotion) {
            StorePotion potion = (StorePotion) shopItem;
            setCursorPosition(potion.potion.hb.cX, Settings.HEIGHT - potion.potion.hb.cY);
            potion.potion.hb.clicked = true;
        }
    }

    public static void clickProceedButton() {
        AbstractDungeon.overlayMenu.proceedButton.show();
        Hitbox hb = (Hitbox) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class, "hb");
        hb.clicked = true;
    }

    public static void clickCancelButton() {
        AbstractDungeon.overlayMenu.cancelButton.hb.clicked = true;
    }

    public static void setCursorPosition(float x, float y) {
        Gdx.input.setCursorPosition((int)x, (int)y);
        InputHelper.updateFirst();
    }

    public static ArrayList<String> getMapScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        if(AbstractDungeon.dungeonMapScreen.dismissable) {
            choices.add("return");
        }
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        if(currMapNode.y == 14 || (AbstractDungeon.id.equals(TheEnding.ID) && currMapNode.y == 2)) {
            choices.add("boss");
            return choices;
        }
        ArrayList<MapRoomNode> availableNodes = getMapScreenNodeChoices();
        for (MapRoomNode node: availableNodes) {
            choices.add(String.format("{\"x\":%d, \"y\":%d, \"symbol\":\"%s\"}", node.x, node.y, node.getRoomSymbol(true)).toLowerCase());
        }
        return choices;
    }

    public static ArrayList<MapRoomNode> getMapScreenNodeChoices() {
        ArrayList<MapRoomNode> choices = new ArrayList<>();
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        if(!AbstractDungeon.firstRoomChosen) {
            for(MapRoomNode node : map.get(0)) {
                if (node.hasEdges()) {
                    choices.add(node);
                }
            }
        } else {
            for (ArrayList<MapRoomNode> rows : map) {
                for (MapRoomNode node : rows) {
                    if (node.hasEdges()) {
                        boolean normalConnection = currMapNode.isConnectedTo(node);
                        boolean wingedConnection = currMapNode.wingedIsConnectedTo(node);
                        if (normalConnection || wingedConnection) {
                            choices.add(node);
                        }
                    }
                }
            }
        }
        return choices;
    }

    public static void makeMapChoice(int choice) {
        if(choice == 0) {
            clickCancelButton();
            return;
        }
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        if(currMapNode.y == 14 || (AbstractDungeon.id.equals(TheEnding.ID) && currMapNode.y == 2)) {
            if(choice == 1) {
                DungeonMap map = AbstractDungeon.dungeonMapScreen.map;
                setCursorPosition(map.bossHb.cX, Settings.HEIGHT - map.bossHb.cY);
                map.bossHb.hovered = true;
                InputHelper.justClickedLeft = true;
                return;
            } else {
                return;
            }
        }
        ArrayList<MapRoomNode> nodeChoices = getMapScreenNodeChoices();
        MapRoomNode chosenNode = nodeChoices.get(choice - 1);
        setCursorPosition(chosenNode.hb.cX, Settings.HEIGHT - chosenNode.hb.cY);
        AbstractDungeon.dungeonMapScreen.clicked = true;
    }

    public static String getOptionName(String input) {
        String unformatted = input.replaceAll("#.|NL", "");
        Pattern regex = Pattern.compile("\\[(.*?)\\]");
        Matcher matcher = regex.matcher(unformatted);
        if(matcher.find()) {
            return matcher.group(1);
        } else {
            return unformatted;
        }
    }

    public static ArrayList<String> getEventScreenChoices() {
        ArrayList<String> choiceList = new ArrayList<>();
        ArrayList<LargeDialogOptionButton> buttons1 = AbstractDungeon.getCurrRoom().event.imageEventText.optionList;
        ArrayList<LargeDialogOptionButton> buttons2 = RoomEventDialog.optionList;
        boolean genericShown = (boolean) ReflectionHacks.getPrivateStatic(GenericEventDialog.class, "show");
        boolean roomShown = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.getCurrRoom().event.roomEventText, RoomEventDialog.class, "show");

        /*if(AbstractDungeon.getCurrRoom().event instanceof GremlinWheelGame) {
            GremlinWheelGame event = (GremlinWheelGame)AbstractDungeon.getCurrRoom().event;

        } else*/ if(genericShown && buttons1.size() > 0) {
            for (LargeDialogOptionButton button : buttons1) {
                if (!button.isDisabled) {
                    choiceList.add(getOptionName(button.msg).toLowerCase());
                }
            }
        } else if (roomShown && buttons2.size() > 0) {
            for (LargeDialogOptionButton button : buttons2) {
                if (!button.isDisabled) {
                    choiceList.add(getOptionName(button.msg).toLowerCase());
                }
            }
        } else if(AbstractDungeon.getCurrRoom().event instanceof GremlinWheelGame) {
            choiceList.add("spin");
        } else if(AbstractDungeon.getCurrRoom().event instanceof GremlinMatchGame) {
            GremlinMatchGame event = (GremlinMatchGame) (AbstractDungeon.getCurrRoom().event);
            CardGroup gameCardGroup = (CardGroup) ReflectionHacks.getPrivate(event, GremlinMatchGame.class, "cards");
            for (AbstractCard c : gameCardGroup.group) {
                if (c.isFlipped) {
                    choiceList.add("unknown");
                }
            }
        }
        return choiceList;
    }

    public static void makeEventChoice(int choice) {
        ArrayList<LargeDialogOptionButton> buttons1 = AbstractDungeon.getCurrRoom().event.imageEventText.optionList;
        ArrayList<LargeDialogOptionButton> buttons2 = RoomEventDialog.optionList;
        boolean genericShown = (boolean) ReflectionHacks.getPrivateStatic(GenericEventDialog.class, "show");
        boolean roomShown = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.getCurrRoom().event.roomEventText, RoomEventDialog.class, "show");
        if (genericShown && buttons1.size() > 0) {
            LargeDialogOptionButton chosenButton = buttons1.get(choice);
            chosenButton.pressed = true;
        } else if(roomShown && buttons2.size() > 0) {
            LargeDialogOptionButton chosenButton = buttons2.get(choice);
            chosenButton.pressed = true;
        } else if (AbstractDungeon.getCurrRoom().event instanceof GremlinWheelGame) {
            GremlinWheelGame event = (GremlinWheelGame) AbstractDungeon.getCurrRoom().event;
            ReflectionHacks.setPrivate(event, GremlinWheelGame.class, "buttonPressed", true);
            CardCrawlGame.sound.play("WHEEL");
        } else if (AbstractDungeon.getCurrRoom().event instanceof GremlinMatchGame) {
            GremlinMatchGame event = (GremlinMatchGame) AbstractDungeon.getCurrRoom().event;
            CardGroup gameCardGroup = (CardGroup) ReflectionHacks.getPrivate(event, GremlinMatchGame.class, "cards");
            ArrayList<AbstractCard> pickable = new ArrayList<>();
            for (AbstractCard c : gameCardGroup.group) {
                if (c.isFlipped) {
                    pickable.add(c);
                }
            }
            AbstractCard chosenCard = pickable.get(choice);
            setCursorPosition(chosenCard.hb.cX, Settings.HEIGHT - chosenCard.hb.cY);
            InputHelper.justClickedLeft = true;
        }
    }

    public static ArrayList<String> getRestRoomChoices() {
        ArrayList<String> choiceList = new ArrayList<>();
        if(AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE) {
            choiceList.add("proceed");
            return choiceList;
        }
        ArrayList<AbstractCampfireOption> buttons = getValidRestRoomButtons();
        for(AbstractCampfireOption button : buttons) {
            choiceList.add(getCampfireOptionName(button));
        }
        return choiceList;
    }

    public static void makeRestRoomChoice(int choice_index) {
        ArrayList<String> choices = getRestRoomChoices();
        String choice = choices.get(choice_index);
        if(choice.equals("proceed")) {
            clickProceedButton();
        } else {
            ArrayList<AbstractCampfireOption> buttons = getValidRestRoomButtons();
            AbstractCampfireOption button = buttons.get(choice_index);
            RestRoom room = (RestRoom) AbstractDungeon.getCurrRoom();
            button.useOption();
            room.campfireUI.somethingSelected = true;
        }
    }

    private static ArrayList<AbstractCampfireOption> getValidRestRoomButtons() {
        ArrayList<AbstractCampfireOption> choiceList = new ArrayList<>();
        RestRoom room = (RestRoom) AbstractDungeon.getCurrRoom();
        ArrayList<AbstractCampfireOption> buttons = (ArrayList<AbstractCampfireOption>) ReflectionHacks.getPrivate(room.campfireUI, CampfireUI.class, "buttons");
        for(AbstractCampfireOption button : buttons) {
            if(button.usable) {
                choiceList.add(button);
            }
        }
        return choiceList;
    }

    private static String getCampfireOptionName(AbstractCampfireOption option) {
        String label = (String) ReflectionHacks.getPrivate(option, AbstractCampfireOption.class, "label");
        return label.toLowerCase();
    }


}
