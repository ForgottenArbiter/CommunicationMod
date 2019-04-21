package communicationmod;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.TheEnding;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.events.shrines.GremlinMatchGame;
import com.megacrit.cardcrawl.events.shrines.GremlinWheelGame;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.map.DungeonMap;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rewards.chests.AbstractChest;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.buttons.*;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import communicationmod.patches.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
        HAND_SELECT,
        GAME_OVER,
        COMPLETE,
        NONE
    }

    public enum EventDialogType {
        IMAGE, ROOM, NONE
    }

    public static ChoiceType getCurrentChoiceType() {
        if (!AbstractDungeon.isScreenUp) {
            if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.EVENT || (AbstractDungeon.getCurrRoom().event != null && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE)) {
                return ChoiceType.EVENT;
            } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss || AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
                return ChoiceType.CHEST;
            } else if (AbstractDungeon.getCurrRoom() instanceof ShopRoom) {
                return ChoiceType.SHOP_ROOM;
            } else if (AbstractDungeon.getCurrRoom() instanceof RestRoom) {
                return ChoiceType.REST;
            } else if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE && AbstractDungeon.actionManager.isEmpty() && !AbstractDungeon.isFadingOut) {
                if (AbstractDungeon.getCurrRoom().event == null || (!(AbstractDungeon.getCurrRoom().event instanceof AbstractImageEvent) && (!AbstractDungeon.getCurrRoom().event.hasFocus))) {
                    return ChoiceType.COMPLETE;
                }
            } else {
                return ChoiceType.NONE;
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
            case HAND_SELECT:
                return ChoiceType.HAND_SELECT;
            case DEATH:
            case VICTORY:
            case UNLOCK:
            case NEOW_UNLOCK:
                return ChoiceType.GAME_OVER;
            default:
                return ChoiceType.NONE;
        }
    }

    public static ArrayList<String> getCurrentChoiceList() {
        ChoiceType choiceType = getCurrentChoiceType();
        ArrayList<String> choices;
        switch (choiceType) {
            case EVENT:
                choices = getEventScreenChoices();
                break;
            case CHEST:
                choices = getChestRoomChoices();
                break;
            case SHOP_ROOM:
                choices = getShopRoomChoices();
                break;
            case REST:
                choices = getRestRoomChoices();
                break;
            case CARD_REWARD:
                choices = getCardRewardScreenChoices();
                break;
            case COMBAT_REWARD:
                choices = getCombatRewardScreenChoices();
                break;
            case MAP:
                choices = getMapScreenChoices();
                break;
            case BOSS_REWARD:
                choices = getBossRewardScreenChoices();
                break;
            case SHOP_SCREEN:
                choices = getShopScreenChoices();
                break;
            case GRID:
                choices = getGridScreenChoices();
                break;
            case HAND_SELECT:
                choices = getHandSelectScreenChoices();
                break;
            default:
                return new ArrayList<>();
        }
        ArrayList<String> lowerCaseChoices = new ArrayList<>();
        for(String item : choices) {
            lowerCaseChoices.add(item.toLowerCase());
        }
        return lowerCaseChoices;
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
            case HAND_SELECT:
                makeHandSelectScreenChoice(choice_index);
                return;
            default:
                logger.info("Unimplemented choice.");
        }
    }

    private static boolean isCancelButtonAvailable(ChoiceType choiceType) {
        switch (choiceType) {
            case EVENT:
                return false;
            case CHEST:
                return false;
            case SHOP_ROOM:
                return false;
            case REST:
                return false;
            case CARD_REWARD:
                return isCardRewardSkipAvailable();
            case COMBAT_REWARD:
                return false;
            case MAP:
                return AbstractDungeon.dungeonMapScreen.dismissable;
            case BOSS_REWARD:
                return true;
            case SHOP_SCREEN:
                return true;
            case GRID:
                return isGridScreenCancelAvailable();
            case HAND_SELECT:
                return false;
            case GAME_OVER:
                return false;
            case COMPLETE:
                return false;
            default:
                return false;
        }
    }

    public static boolean isCancelButtonAvailable() {
        return isCancelButtonAvailable(getCurrentChoiceType());
    }

    private static String getCancelButtonText(ChoiceType choiceType) {
        switch (choiceType) {
            case CARD_REWARD:
                return "skip";
            case MAP:
                return "return";
            case BOSS_REWARD:
                return "skip";
            case SHOP_SCREEN:
                return "leave";
            case GRID:
                return "cancel";
            default:
                return "cancel";
        }
    }

    public static String getCancelButtonText() {
        return getCancelButtonText(getCurrentChoiceType());
    }

    private static void pressCancelButton(ChoiceType choiceType) {
        switch (choiceType) {
            case CARD_REWARD:
                AbstractDungeon.closeCurrentScreen();
                return;
            case MAP:
                clickCancelButton();
                return;
            case BOSS_REWARD:
                AbstractDungeon.bossRelicScreen.cancelButton.hb.clicked = true;
                return;
            case SHOP_SCREEN:
                clickCancelButton();
                return;
            case GRID:
                clickCancelButton();
        }
    }

    public static void pressCancelButton() {
        pressCancelButton(getCurrentChoiceType());
    }

    private static boolean isConfirmButtonAvailable(ChoiceType choiceType) {
        switch (choiceType) {
            case EVENT:
                return false;
            case CHEST:
                return true;
            case SHOP_ROOM:
                return true;
            case REST:
                return isRestRoomProceedAvailable();
            case CARD_REWARD:
                return false;
            case COMBAT_REWARD:
                return true;
            case MAP:
                return false;
            case BOSS_REWARD:
                return false;
            case SHOP_SCREEN:
                return false;
            case GRID:
                return isGridScreenConfirmAvailable();
            case HAND_SELECT:
                return isHandSelectConfirmButtonEnabled();
            case GAME_OVER:
                return true;
            case COMPLETE:
                return true;
            default:
                return false;
        }
    }

    public static boolean isConfirmButtonAvailable() {
        return isConfirmButtonAvailable(getCurrentChoiceType());
    }

    private static String getConfirmButtonText(ChoiceType choiceType) {
        switch (choiceType) {
            case CHEST:
                return "proceed";
            case SHOP_ROOM:
                return "proceed";
            case REST:
                return "proceed";
            case COMBAT_REWARD:
                return "proceed";
            case GRID:
                return "confirm";
            case HAND_SELECT:
                return "confirm";
            case GAME_OVER:
                return "proceed";
            case COMPLETE:
                return "proceed";
            default:
                return "confirm";
        }
    }

    public static String getConfirmButtonText() {
        return getConfirmButtonText(getCurrentChoiceType());
    }

    public static void pressConfirmButton(ChoiceType choiceType) {
        switch (choiceType) {
            case CHEST:
                clickProceedButton();
                return;
            case SHOP_ROOM:
                clickProceedButton();
                return;
            case REST:
                clickProceedButton();
                return;
            case COMBAT_REWARD:
                clickProceedButton();
                return;
            case GRID:
                clickGridScreenConfirmButton();
                return;
            case HAND_SELECT:
                clickHandSelectScreenConfirmButton();
                return;
            case GAME_OVER:
                clickGameOverReturnButton();
                return;
            case COMPLETE:
                clickProceedButton();
        }
    }

    public static void pressConfirmButton() {
        pressConfirmButton(getCurrentChoiceType());
    }

    public static ArrayList<String> getCardRewardScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.cardRewardScreen.rewardGroup) {
            choices.add(card.name.toLowerCase());
        }
        if(isBowlAvailable()) {
            choices.add("bowl");
        }
        return choices;
    }

    public static boolean isBowlAvailable() {
        SingingBowlButton bowlButton = (SingingBowlButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, CardRewardScreen.class, "bowlButton");
        return !((boolean) ReflectionHacks.getPrivate(bowlButton, SingingBowlButton.class, "isHidden"));
    }

    public static boolean isCardRewardSkipAvailable() {
        SkipCardButton skipButton = (SkipCardButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, CardRewardScreen.class, "skipButton");
        return !((boolean) ReflectionHacks.getPrivate(skipButton, SkipCardButton.class, "isHidden"));
    }

    public static void makeCardRewardChoice(int choice) {
        ArrayList<String> choices = getCardRewardScreenChoices();
        if(choices.get(choice).equals("bowl")) {
            SingingBowlButton bowlButton = (SingingBowlButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, CardRewardScreen.class, "bowlButton");
            bowlButton.onClick();
            AbstractDungeon.cardRewardScreen.closeFromBowlButton();
            AbstractDungeon.closeCurrentScreen();
        } else {
            AbstractCard selectedCard = AbstractDungeon.cardRewardScreen.rewardGroup.get(choice);
            //Gdx.input.setCursorPosition((int)selectedCard.hb.cX, (int)(Settings.HEIGHT - selectedCard.hb.cY));
            CardRewardScreenPatch.doHover = true;
            CardRewardScreenPatch.hoverCard = selectedCard;
            selectedCard.hb.clicked = true;
            //selectedCard.hb.hovered = true;
        }
    }

    public static ArrayList<String> getHandSelectScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        HandCardSelectScreen screen = AbstractDungeon.handCardSelectScreen;
        if(screen.numCardsToSelect == screen.selectedCards.group.size()) {
            return choices;
        }
        for(AbstractCard card : AbstractDungeon.player.hand.group) {
            choices.add(card.name.toLowerCase());
        }
        return choices;
    }

    public static void makeHandSelectScreenChoice(int choice) {
        HandCardSelectScreen screen = AbstractDungeon.handCardSelectScreen;
        screen.hoveredCard = AbstractDungeon.player.hand.group.get(choice);
        screen.hoveredCard.setAngle(0.0f, false); // This might not be necessary
        try {
            Method hotkeyCheck = HandCardSelectScreen.class.getDeclaredMethod("selectHoveredCard");
            hotkeyCheck.setAccessible(true);
            hotkeyCheck.invoke(screen);
        } catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException("selectHoveredCard method somehow can't be called.");
        }
    }

    private static void clickHandSelectScreenConfirmButton() {
        HandCardSelectScreen screen = AbstractDungeon.handCardSelectScreen;
        screen.button.hb.clicked = true;
    }

    private static boolean isHandSelectConfirmButtonEnabled() {
        CardSelectConfirmButton button = AbstractDungeon.handCardSelectScreen.button;
        boolean isHidden = (boolean)ReflectionHacks.getPrivate(button, CardSelectConfirmButton.class, "isHidden");
        boolean isDisabled = button.isDisabled;
        return !(isHidden || isDisabled);
    }

    public static ArrayList<AbstractCard> getGridScreenCards() {
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        CardGroup cards = (CardGroup) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "targetGroup");
        return cards.group;
    }

    public static ArrayList<String> getGridScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        if(AbstractDungeon.gridSelectScreen.confirmScreenUp || AbstractDungeon.gridSelectScreen.isJustForConfirming) {
            return choices;
        }
        for(AbstractCard card : getGridScreenCards()) {
            choices.add(card.name.toLowerCase());
        }
        return choices;
    }

    public static void makeGridScreenChoice (int choice) {
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        GridCardSelectScreenPatch.hoverCard = getGridScreenCards().get(choice);
        GridCardSelectScreenPatch.replaceHoverCard = true;
    }

    private static void clickGridScreenConfirmButton() {
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        screen.confirmButton.hb.clicked = true;
        if (AbstractDungeon.previousScreen == AbstractDungeon.CurrentScreen.SHOP) {
            // The rest of the associated shop purge logic will not run in this update, so we need to block until it does.
            GameStateListener.blockStateUpdate();
        }
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
        for(RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            choices.add(reward.type.name().toLowerCase());
        }
        return choices;
    }

    public static void makeCombatRewardChoice(int choice) {
        RewardItem reward = AbstractDungeon.combatRewardScreen.rewards.get(choice);
        reward.isDone = true;
    }

    public static ArrayList<String> getBossRewardScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        for(AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
            choices.add(relic.name);
        }
        return choices;
    }

    public static void makeBossRewardChoice(int choice) {
        AbstractRelic chosenRelic = AbstractDungeon.bossRelicScreen.relics.get(choice);
        setCursorPosition(chosenRelic.hb.cX, Settings.HEIGHT - chosenRelic.hb.cY);
        InputHelper.justClickedLeft = true;
    }

    public static ArrayList<String> getChestRoomChoices() {
        ArrayList<String> choices = new ArrayList<>();
        AbstractChest chest = null;
        if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss) {
            chest = ((TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest;
        } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
            chest = ((TreasureRoom) AbstractDungeon.getCurrRoom()).chest;
        }
        if (chest != null && !chest.isOpen) {
            choices.add("open");
        }
        return choices;
    }

    public static void makeChestRoomChoice (int choice) {
        if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss) {
            AbstractChest chest = ((TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest;
            chest.isOpen = true;
            chest.open(false);
        } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
            AbstractChest chest = ((TreasureRoom) AbstractDungeon.getCurrRoom()).chest;
            chest.isOpen = true;
            chest.open(false);
        }
    }

    public static ArrayList<String> getShopRoomChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add("shop");
        return choices;
    }

    public static void makeShopRoomChoice (int choice) {
        MerchantPatch.visitMerchant = true;
    }

    public static ArrayList<String> getShopScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
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

    @SuppressWarnings("unchecked")
    public static ArrayList<AbstractCard> getShopScreenCards() {
        ArrayList<AbstractCard> cards = new ArrayList<>();
        ShopScreen screen = AbstractDungeon.shopScreen;
        ArrayList<AbstractCard> coloredCards = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "coloredCards");
        ArrayList<AbstractCard> colorlessCards = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "colorlessCards");
        cards.addAll(coloredCards);
        cards.addAll(colorlessCards);
        return cards;
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<StoreRelic> getShopScreenRelics() {
        ShopScreen screen = AbstractDungeon.shopScreen;
        return (ArrayList<StoreRelic>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "relics");
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<StorePotion> getShopScreenPotions() {
        ShopScreen screen = AbstractDungeon.shopScreen;
        return (ArrayList<StorePotion>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "potions");
    }

    private static ArrayList<Object> getAvailableShopItems() {
        ArrayList<Object> choices = new ArrayList<>();
        ShopScreen screen = AbstractDungeon.shopScreen;
        if(screen.purgeAvailable && AbstractDungeon.player.gold >= ShopScreen.actualPurgeCost) {
            choices.add("purge");
        }
        for(AbstractCard card : getShopScreenCards()) {
            if(card.price <= AbstractDungeon.player.gold) {
                choices.add(card);
            }
        }
        for(StoreRelic relic : getShopScreenRelics()) {
            if(relic.price <= AbstractDungeon.player.gold) {
                choices.add(relic);
            }
        }
        for(StorePotion potion : getShopScreenPotions()) {
            if(potion.price <= AbstractDungeon.player.gold) {
                choices.add(potion);
            }
        }
        return choices;
    }

    public static void makeShopScreenChoice(int choice) {
        ArrayList<Object> shopItems = getAvailableShopItems();
        Object shopItem = shopItems.get(choice);
        if (shopItem instanceof String) {
            AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.SHOP;
            AbstractDungeon.gridSelectScreen.open(
                    CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()),
                    1, ShopScreen.NAMES[13], false, false, true, true);
        } else if (shopItem instanceof AbstractCard) {
            AbstractCard card = (AbstractCard)shopItem;
            //setCursorPosition(card.hb.cX, Settings.HEIGHT - card.hb.cY);
            ShopScreenPatch.doHover = true;
            ShopScreenPatch.hoverCard = card;
            card.hb.clicked = true;
        } else if (shopItem instanceof StoreRelic) {
            StoreRelic relic = (StoreRelic) shopItem;
            //setCursorPosition(relic.relic.hb.cX, Settings.HEIGHT - relic.relic.hb.cY);
            relic.relic.hb.clicked = true;
        } else if (shopItem instanceof StorePotion) {
            StorePotion potion = (StorePotion) shopItem;
            //setCursorPosition(potion.potion.hb.cX, Settings.HEIGHT - potion.potion.hb.cY);
            potion.potion.hb.clicked = true;
        }
    }

    private static void clickProceedButton() {
        AbstractDungeon.overlayMenu.proceedButton.show();
        Hitbox hb = (Hitbox) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class, "hb");
        hb.clicked = true;
    }

    private static void clickCancelButton() {
        AbstractDungeon.overlayMenu.cancelButton.hb.clicked = true;
    }

    private static void setCursorPosition(float x, float y) {
        Gdx.input.setCursorPosition((int)x, (int)y);
        InputHelper.updateFirst();
    }

    public static boolean bossNodeAvailable() {
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        return (currMapNode.y == 14 || (AbstractDungeon.id.equals(TheEnding.ID) && currMapNode.y == 2));
    }

    public static ArrayList<String> getMapScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        if(bossNodeAvailable()) {
            choices.add("boss");
            return choices;
        }
        ArrayList<MapRoomNode> availableNodes = getMapScreenNodeChoices();
        for (MapRoomNode node: availableNodes) {
            choices.add(String.format("x=%d", node.x).toLowerCase());
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
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        if(currMapNode.y == 14 || (AbstractDungeon.id.equals(TheEnding.ID) && currMapNode.y == 2)) {
            if(choice == 0) {
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
        MapRoomNode chosenNode = nodeChoices.get(choice);
        //setCursorPosition(chosenNode.hb.cX, Settings.HEIGHT - chosenNode.hb.cY);
        MapRoomNodeHoverPatch.hoverNode = chosenNode;
        MapRoomNodeHoverPatch.doHover = true;
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


    public static EventDialogType getEventDialogType() {
        boolean genericShown = (boolean) ReflectionHacks.getPrivateStatic(GenericEventDialog.class, "show");
        if (genericShown) {
            return EventDialogType.IMAGE;
        }
        boolean roomShown = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.getCurrRoom().event.roomEventText, RoomEventDialog.class, "show");
        if (roomShown) {
            return EventDialogType.ROOM;
        } else {
            return EventDialogType.NONE;
        }
    }

    public static ArrayList<LargeDialogOptionButton> getEventButtons() {
        EventDialogType eventType = getEventDialogType();
        switch(eventType) {
            case IMAGE:
                return AbstractDungeon.getCurrRoom().event.imageEventText.optionList;
            case ROOM:
                return RoomEventDialog.optionList;
            default:
                return new ArrayList<>();
        }
    }

    public static ArrayList<LargeDialogOptionButton> getActiveEventButtons() {
        ArrayList<LargeDialogOptionButton> buttons = getEventButtons();
        ArrayList<LargeDialogOptionButton> activeButtons = new ArrayList<>();
        for(LargeDialogOptionButton button : buttons) {
            if(!button.isDisabled) {
                activeButtons.add(button);
            }
        }
        return activeButtons;
    }

    public static ArrayList<String> getEventScreenChoices() {
        ArrayList<String> choiceList = new ArrayList<>();
        ArrayList<LargeDialogOptionButton> activeButtons = getActiveEventButtons();

        if (activeButtons.size() > 0) {
            for(LargeDialogOptionButton button : activeButtons) {
                choiceList.add(getOptionName(button.msg).toLowerCase());
            }
        } else if(AbstractDungeon.getCurrRoom().event instanceof GremlinWheelGame) {
            choiceList.add("spin");
        } else if(AbstractDungeon.getCurrRoom().event instanceof GremlinMatchGame) {
            GremlinMatchGame event = (GremlinMatchGame) (AbstractDungeon.getCurrRoom().event);
            CardGroup gameCardGroup = (CardGroup) ReflectionHacks.getPrivate(event, GremlinMatchGame.class, "cards");
            for (AbstractCard c : gameCardGroup.group) {
                if (c.isFlipped) {
                    choiceList.add(String.format("card%d", GremlinMatchGamePatch.cardPositions.get(c.uuid)));
                }
            }
        }
        return choiceList;
    }

    public static void makeEventChoice(int choice) {
        ArrayList<LargeDialogOptionButton> activeButtons = getActiveEventButtons();
        if (activeButtons.size() > 0) {
            activeButtons.get(choice).pressed = true;
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
        ArrayList<AbstractCampfireOption> buttons = getValidRestRoomButtons();
        for(AbstractCampfireOption button : buttons) {
            choiceList.add(getCampfireOptionName(button));
        }
        return choiceList;
    }

    public static void makeRestRoomChoice(int choice_index) {
        ArrayList<AbstractCampfireOption> buttons = getValidRestRoomButtons();
        AbstractCampfireOption button = buttons.get(choice_index);
        RestRoom room = (RestRoom) AbstractDungeon.getCurrRoom();
        button.useOption();
        room.campfireUI.somethingSelected = true;
    }

    private static boolean isRestRoomProceedAvailable() {
        return AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE;
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<AbstractCampfireOption> getValidRestRoomButtons() {
        ArrayList<AbstractCampfireOption> choiceList = new ArrayList<>();
        RestRoom room = (RestRoom) AbstractDungeon.getCurrRoom();
        if(!isRestRoomProceedAvailable()) {
            ArrayList<AbstractCampfireOption> buttons = (ArrayList<AbstractCampfireOption>) ReflectionHacks.getPrivate(room.campfireUI, CampfireUI.class, "buttons");
            for (AbstractCampfireOption button : buttons) {
                if (button.usable) {
                    choiceList.add(button);
                }
            }
        }
        return choiceList;
    }

    private static String getCampfireOptionName(AbstractCampfireOption option) {
        String classname = option.getClass().getSimpleName();
        String nameWithoutOption = classname.substring(0, classname.length() - "Option".length());
        return nameWithoutOption.toLowerCase();
    }

    private static void clickGameOverReturnButton() {
        //For now, just copying the functionality from VictoryScreen.update(), always skipping credits
        AbstractDungeon.unlocks.clear();
        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;
        CardCrawlGame.trial = null;
        CardCrawlGame.startOver();
    }

}
