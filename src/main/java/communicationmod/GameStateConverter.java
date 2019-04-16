package communicationmod;

import basemod.ReflectionHacks;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import communicationmod.patches.UpdateBodyTextPatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class GameStateConverter {

    private static AbstractDungeon.CurrentScreen previousScreen = null;
    private static boolean previousScreenUp = false;
    private static AbstractRoom.RoomPhase previousPhase = null;
    private static int previousGold = 99;
    private static boolean externalChange = false;
    private static boolean myTurn = false;
    private static boolean blocked = false;
    private static boolean waitingForCommand = false;
    private static boolean hasPresentedOutOfGameState = false;

    /**
     * Used to indicate that something (in game logic, not external command) has been done that will change the game state,
     * and hasStateChanged() should indicate a state change when the state next becomes stable.
     */
    public static void registerStateChange() {
        externalChange = true;
        waitingForCommand = false;
    }

    /**
     * Used to indicate that an external command has been executed
     */
    public static void registerCommandExecution() {
        waitingForCommand = false;
    }

    /**
     * Prevents hasStateChanged() from indicating a state change until resumeStateUpdate() is called.
     */
    public static void blockStateUpdate() {
        blocked = true;
    }

    /**
     * Removes the block instantiated by blockStateChanged()
     */
    public static void resumeStateUpdate() {
        blocked = false;
    }

    /**
     * Used by a patch in the game to signal the start of your turn. We do not care about state changes
     * when it is not our turn in combat, as we cannot take action until then.
     */
    public static void signalTurnStart() {
        myTurn = true;
    }

    /**
     * Used by patches in the game to signal the end of your turn (or the end of combat).
     */
    public static void signalTurnEnd() {
        myTurn = false;
    }

    /**
     * Detects whether the game state is stable and we are ready to receive a command from the user.
     * @return whether the state is stable
     */
    private static boolean hasDungeonStateChanged() {
        if(blocked) {
            return false;
        }
        hasPresentedOutOfGameState = false;
        AbstractDungeon.CurrentScreen newScreen = AbstractDungeon.screen;
        boolean newScreenUp = AbstractDungeon.isScreenUp;
        AbstractRoom.RoomPhase newPhase = AbstractDungeon.getCurrRoom().phase;
        boolean inCombat = (newPhase == AbstractRoom.RoomPhase.COMBAT);
        // This check happens first since dying can happen in combat and messes with the other cases.
        if(newScreen == AbstractDungeon.CurrentScreen.DEATH && newScreen != previousScreen) {
            return true;
        }
        // These screens have no interaction available.
        if(newScreen == AbstractDungeon.CurrentScreen.DOOR_UNLOCK || newScreen == AbstractDungeon.CurrentScreen.NO_INTERACT) {
            return false;
        }
        // We are never ready to receive commands when it is not our turn.
        if(inCombat && (!myTurn || AbstractDungeon.getMonsters().areMonstersBasicallyDead() )) {
            return false;
        }
        // In event rooms, we need to wait for the event wait timer to reach 0 before we can accurately assess its state.
        if((AbstractDungeon.getCurrRoom() instanceof EventRoom || AbstractDungeon.getCurrRoom() instanceof NeowRoom) && AbstractDungeon.getCurrRoom().event.waitTimer != 0.0F) {
            return false;
        }
        // The state has always changed in some way when one of these variables is different.
        // However, the state may not be finished changing, so we need to do some additional checks.
        if(newScreen != previousScreen || newScreenUp != previousScreenUp || newPhase != previousPhase) {
            if(inCombat) {
                // In combat, newScreenUp being true indicates an action that requires our immediate attention.
                if(newScreenUp) {
                    return true;
                }
                // In combat, if no screen is up, we should wait for all actions to complete before indicating a state change.
                else if(AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER) && AbstractDungeon.actionManager.cardQueue.isEmpty() && AbstractDungeon.actionManager.actions.isEmpty()) {
                    return true;
                }

            // Out of combat, there is nothing to wait for. We just indicate the state change right away.
            } else {
                return true;
            }
        }
        // We are assuming that commands are only being submitted through our interface. Some actions that require
        // our attention, like retaining a card, occur after the end turn is queued, but the previous cases
        // cover those actions. We would like to avoid registering other state changes after the end turn
        // command but before the game actually ends your turn.
        if(inCombat && AbstractDungeon.player.endTurnQueued) {
            return false;
        }
        // If some other code registered a state change through registerStateChange(), or if we notice a state
        // change through the gold amount changing, we still need to wait until all actions are finished
        // resolving to claim a stable state and ask for a new command.
        if(
                (externalChange || previousGold != AbstractDungeon.player.gold) &&
                AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER) &&
                AbstractDungeon.actionManager.preTurnActions.isEmpty() &&
                AbstractDungeon.actionManager.actions.isEmpty() &&
                AbstractDungeon.actionManager.cardQueue.isEmpty()) {

            return true;
        }
        // Sometimes, we need to register an external change in combat while an action is resolving which brings
        // the screen up. Because the screen did not change, this is not covered by other cases.
        if (externalChange && inCombat && newScreenUp) {
            return true;
        }
        return false;
    }

    /**
     * Detects whether the state of the game menu has changed. Right now, this only occurs when you first enter the
     * menu, either after starting Slay the Spire for the first time, or after ending a game and returning to the menu.
     * @return Whether the main menu has just been entered.
     */
    public static boolean checkForMenuStateChange() {
        boolean stateChange = false;
        if(!hasPresentedOutOfGameState && CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT && CardCrawlGame.mainMenuScreen != null) {
            stateChange = true;
            hasPresentedOutOfGameState = true;
        }
        if(stateChange) {
            externalChange = false;
            waitingForCommand = true;
        }
        return stateChange;
    }

    /**
     * Detects a state change in AbstractDungeon, and updates all of the local variables used to detect
     * changes in the dungeon state. Sets waitingForCommand = true if a state change was registered since
     * the last command was sent.
     * @return Whether a dungeon state change was detected
     */
    public static boolean checkForDungeonStateChange() {
        boolean stateChange = false;
        if(CommandExecutor.isInDungeon()) {
            stateChange = hasDungeonStateChanged();
            if(stateChange) {
                externalChange = false;
                waitingForCommand = true;
                previousPhase = AbstractDungeon.getCurrRoom().phase;
                previousScreen = AbstractDungeon.screen;
                previousScreenUp = AbstractDungeon.isScreenUp;
                previousGold = AbstractDungeon.player.gold;
            }
        } else {
            myTurn = false;
        }
        return stateChange;
    }

    /**
     * Creates a JSON representation of the status of CommunicationMod that will be sent to the external process.
     * The JSON object returned contains:
     * "available_commands": A list of commands (Strings) that are available to the user when the state is sent
     * "ready_for_command": A boolean, denoting whether the game state is stable and ready to receive a command
     * "in_game": A boolean, True if in the main menu, False if the player is in the dungeon
     * "game_state": Present if in_game=True, contains the game state object returned by getGameState()
     * @return A string containing the JSON representation of CommunicationMod's status
     */
    public static String getCommunicationState() {
        HashMap<String, Object> response = new HashMap<>();
        response.put("available_commands", CommandExecutor.getAvailableCommands());
        response.put("ready_for_command", waitingForCommand);
        boolean isInGame = CommandExecutor.isInDungeon();
        response.put("in_game", isInGame);
        if(isInGame) {
            response.put("game_state", getGameState());
        }
        Gson gson = new Gson();
        return gson.toJson(response);
    }


    /**
     * Creates a JSON representation of the game state, which will be sent to the client.
     * Always present:
     * - "screen": The current screen
     * - "is_screen_up": The game's isScreenUp variable
     * - "room_phase": The phase of the current room (COMBAT, EVENT, etc.)
     * - "action_phase": The phase of the action manager (WAITING_FOR_USER_INPUT, EXECUTING_ACTIONS)
     * - "room_type": The type of the current room (ShopRoom, TreasureRoom, MonsterRoom, etc.)
     * - "current_hp": The player's current hp
     * - "max_hp": The player's maximum hp
     * - "floor": The current floor number
     * - "act": The current act number
     * - "gold": The player's current gold total
     * - "relics": A list of the player's current relics
     * - "deck": A list of the cards in the player's deck
     * - "potions": A list of the player's potions (empty slots are PotionSlots)
     * - "map": The current dungeon map
     * - "room_state": Extra state information about the current room
     * Sometimes present:
     * - "current_action": The action in the action manager queue, if not empty.
     * - "screen_state": State information about the current screen.
     * - "choice_list": If the command is available, the possible choices for the choose command.
     * @return A JSON representation of the game state (as a String)
     */
    public static HashMap<String, Object> getGameState() {
        HashMap<String, Object> state = new HashMap<>();


        state.put("screen_name", AbstractDungeon.screen.name());
        state.put("is_screen_up", previousScreenUp);
        state.put("screen_type", ChoiceScreenUtils.getCurrentChoiceType());
        state.put("room_phase", AbstractDungeon.getCurrRoom().phase.toString());
        state.put("action_phase", AbstractDungeon.actionManager.phase.toString());
        if(AbstractDungeon.actionManager.currentAction != null) {
            state.put("current_action", AbstractDungeon.actionManager.currentAction.getClass().getSimpleName());
        }
        state.put("room_type", AbstractDungeon.getCurrRoom().getClass().getSimpleName());
        state.put("current_hp", AbstractDungeon.player.currentHealth);
        state.put("max_hp", AbstractDungeon.player.maxHealth);
        state.put("floor", AbstractDungeon.floorNum);
        state.put("act", AbstractDungeon.actNum);
        state.put("gold", AbstractDungeon.player.gold);
        state.put("seed", Settings.seed);
        state.put("class", AbstractDungeon.player.chosenClass.name());
        state.put("ascension_level", AbstractDungeon.ascensionLevel);

        ArrayList<Object> relics = new ArrayList<>();
        for(AbstractRelic relic : AbstractDungeon.player.relics) {
            relics.add(convertRelicToJson(relic));
        }

        state.put("relics", relics);

        ArrayList<Object> deck = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.masterDeck.group) {
            deck.add(convertCardToJson(card));
        }

        state.put("deck", deck);

        ArrayList<Object> potions = new ArrayList<>();
        for(AbstractPotion potion : AbstractDungeon.player.potions) {
            potions.add(convertPotionToJson(potion));
        }

        state.put("potions", potions);

        state.put("map", convertMapToJson());
        state.put("room_state", getRoomState());
        if(CommandExecutor.isChooseCommandAvailable()) {
            state.put("choice_list", ChoiceScreenUtils.getCurrentChoiceList());
        }
        if(AbstractDungeon.getCurrRoom().phase.equals(AbstractRoom.RoomPhase.COMBAT)) {
            state.put("combat_state", getCombatState());
        }
        state.put("screen_state", getScreenState());
        return state;
    }

    private static HashMap<String, Object> getRoomState() {
        AbstractRoom currentRoom = AbstractDungeon.getCurrRoom();
        HashMap<String, Object> state = new HashMap<>();
        if(currentRoom instanceof TreasureRoom) {
            state.put("chest_type", ((TreasureRoom)currentRoom).chest.getClass().getSimpleName());
            state.put("chest_open", ((TreasureRoom) currentRoom).chest.isOpen);
        } else if(currentRoom instanceof TreasureRoomBoss) {
            state.put("chest_type", ((TreasureRoomBoss)currentRoom).chest.getClass().getSimpleName());
            state.put("chest_open", ((TreasureRoomBoss) currentRoom).chest.isOpen);
        } else if(currentRoom instanceof RestRoom) {
            state.put("has_rested", currentRoom.phase == AbstractRoom.RoomPhase.COMPLETE);
            state.put("rest_options", ChoiceScreenUtils.getRestRoomChoices());
        }
        return state;
    }

    /**
     * This method removes most, but not all of the special text formatting characters found in the game.
     * These extra formatting characters are turned into things like colored or wiggly text in game, but
     * we would like to report the text without dealing with these characters.
     * @param text The text for which the formatting should be removed
     * @return The input text, with the formatting characters removed
     */
    private static String removeTextFormatting(String text) {
        return text.replaceAll("#.|NL", "");
    }

    /**
     * The event state object contains:
     * "body_text": The current body text for the event, or an empty string if there is none
     * "event_name": The name of the event (in the current language). Unfortunately, there is no easy access to the ID.
     * "event_id": The ID of the event
     * "options": A list of options, in the order they are presented in game. Each option contains:
     * * "text": The full text associated with the option (Eg. "[Banana] Heal 10 hp")
     * * "disabled": Whether the current option or button is disabled. Disabled buttons cannot be chosen.
     * * "label": The simple label of a button or option (Eg. "Banana")
     * @return The event state object
     */
    private static HashMap<String, Object> getEventState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> options = new ArrayList<>();
        ChoiceScreenUtils.EventDialogType eventDialogType = ChoiceScreenUtils.getEventDialogType();
        AbstractEvent event = AbstractDungeon.getCurrRoom().event;
        if (eventDialogType == ChoiceScreenUtils.EventDialogType.IMAGE || eventDialogType == ChoiceScreenUtils.EventDialogType.ROOM) {
            for (LargeDialogOptionButton button : ChoiceScreenUtils.getEventButtons()) {
                HashMap<String, Object> json_button = new HashMap<>();
                json_button.put("text", removeTextFormatting(button.msg));
                json_button.put("disabled", button.isDisabled);
                json_button.put("label", ChoiceScreenUtils.getOptionName(button.msg));
                options.add(json_button);
            }
            state.put("body_text", removeTextFormatting(UpdateBodyTextPatch.bodyText));
        } else {
            for (String misc_option : ChoiceScreenUtils.getEventScreenChoices()) {
                HashMap<String, Object> json_button = new HashMap<>();
                json_button.put("text", misc_option);
                json_button.put("disabled", false);
                json_button.put("label", misc_option);
                options.add(json_button);
            }
            state.put("body_text", "");
        }
        state.put("event_name", ReflectionHacks.getPrivateStatic(event.getClass(), "NAME"));
        if (event instanceof NeowEvent) {
            state.put("event_id", "Neow Event");
        } else {
            state.put("event_id", ReflectionHacks.getPrivateStatic(event.getClass(), "ID"));
        }
        state.put("options", options);
        return state;
    }

    private static HashMap<String, Object> getCardRewardState() {
        HashMap<String, Object> state = new HashMap<>();
        state.put("bowl_available", ChoiceScreenUtils.isBowlAvailable());
        state.put("skip_available", ChoiceScreenUtils.isCardRewardSkipAvailable());
        ArrayList<Object> cardRewardJson = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.cardRewardScreen.rewardGroup) {
            cardRewardJson.add(convertCardToJson(card));
        }
        state.put("cards", cardRewardJson);
        return state;
    }

    private static HashMap<String, Object> getCombatRewardState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> rewards = new ArrayList<>();
        for(RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            HashMap<String, Object> jsonReward = new HashMap<>();
            jsonReward.put("reward_type", reward.type.name());
            switch(reward.type) {
                case GOLD:
                case STOLEN_GOLD:
                    jsonReward.put("gold", reward.goldAmt + reward.bonusGold);
                    break;
                case RELIC:
                    jsonReward.put("relic", convertRelicToJson(reward.relic));
                    break;
                case POTION:
                    jsonReward.put("potion", convertPotionToJson(reward.potion));
                    break;
                case SAPPHIRE_KEY:
                    jsonReward.put("link", convertRelicToJson(reward.relicLink.relic));
            }
            rewards.add(jsonReward);
        }
        state.put("rewards", rewards);
        return state;
    }

    private static HashMap<String, Object> getMapScreenState() {
        HashMap<String, Object> state = new HashMap<>();
        if (AbstractDungeon.getCurrMapNode() != null) {
            state.put("current_node", convertMapRoomNodeToJson(AbstractDungeon.getCurrMapNode()));
        }
        ArrayList<Object> nextNodesJson = new ArrayList<>();
        for(MapRoomNode node : ChoiceScreenUtils.getMapScreenNodeChoices()) {
            nextNodesJson.add(convertMapRoomNodeToJson(node));
        }
        state.put("next_nodes", nextNodesJson);
        state.put("boss_available", ChoiceScreenUtils.bossNodeAvailable());
        return state;
    }

    private static HashMap<String, Object> getBossRewardState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> bossRelics = new ArrayList<>();
        for(AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
            bossRelics.add(convertRelicToJson(relic));
        }
        state.put("relics", bossRelics);
        return state;
    }

    private static HashMap<String, Object> getShopScreenState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> shopCards = new ArrayList<>();
        ArrayList<Object> shopRelics = new ArrayList<>();
        ArrayList<Object> shopPotions = new ArrayList<>();
        for(AbstractCard card : ChoiceScreenUtils.getShopScreenCards()) {
            HashMap<String, Object> jsonCard = convertCardToJson(card);
            jsonCard.put("price", card.price);
            shopCards.add(jsonCard);
        }
        for(StoreRelic relic : ChoiceScreenUtils.getShopScreenRelics()) {
            HashMap<String, Object> jsonRelic = convertRelicToJson(relic.relic);
            jsonRelic.put("price", relic.price);
            shopRelics.add(jsonRelic);
        }
        for(StorePotion potion : ChoiceScreenUtils.getShopScreenPotions()) {
            HashMap<String, Object> jsonPotion = convertPotionToJson(potion.potion);
            jsonPotion.put("price", potion.price);
            shopPotions.add(jsonPotion);
        }
        state.put("cards", shopCards);
        state.put("relics", shopRelics);
        state.put("potions", shopPotions);
        state.put("purge_available", AbstractDungeon.shopScreen.purgeAvailable);
        state.put("purge_cost", ShopScreen.actualPurgeCost);
        return state;
    }

    private static HashMap<String, Object> getGridState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> gridJson = new ArrayList<>();
        ArrayList<Object> gridSelectedJson = new ArrayList<>();
        ArrayList<AbstractCard> gridCards = ChoiceScreenUtils.getGridScreenCards();
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        for(AbstractCard card : gridCards) {
            gridJson.add(convertCardToJson(card));
        }
        for(AbstractCard card : screen.selectedCards) {
            gridSelectedJson.add(convertCardToJson(card));
        }
        int numCards = (int) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "numCards");
        boolean forUpgrade = (boolean) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "forUpgrade");
        boolean forTransform = (boolean) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "forTransform");
        boolean forPurge = (boolean) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "forPurge");
        state.put("cards", gridJson);
        state.put("selected_cards", gridSelectedJson);
        state.put("num_cards", numCards);
        state.put("for_upgrade", forUpgrade);
        state.put("for_transform", forTransform);
        state.put("for_purge", forPurge);
        state.put("confirm_up", screen.confirmScreenUp);
        return state;
    }

    private static HashMap<String, Object> getHandSelectState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> handJson = new ArrayList<Object>();
        ArrayList<Object> selectedJson = new ArrayList<Object>();
        ArrayList<AbstractCard> handCards = AbstractDungeon.player.hand.group;
        // As far as I can tell, this comment is a Java 8 version of a Python list comprehension? I think just looping is more readable.
        // handJson = handCards.stream().map(GameStateConverter::convertCardToJson).collect(Collectors.toCollection(ArrayList::new));
        for(AbstractCard card : handCards) {
            handJson.add(convertCardToJson(card));
        }
        state.put("hand", handJson);
        ArrayList<AbstractCard> selectedCards = AbstractDungeon.handCardSelectScreen.selectedCards.group;
        for(AbstractCard card : selectedCards) {
            selectedJson.add(convertCardToJson(card));
        }
        state.put("selected", selectedJson);
        state.put("max_cards", AbstractDungeon.handCardSelectScreen.numCardsToSelect);
        state.put("can_pick_zero", AbstractDungeon.handCardSelectScreen.canPickZero);
        return state;
    }

    private static HashMap<String, Object> getGameOverState() {
        HashMap<String, Object> state = new HashMap<>();
        int score = 0;
        boolean victory = false;
        if(AbstractDungeon.deathScreen != null) {
            score = (int) ReflectionHacks.getPrivate(AbstractDungeon.deathScreen, DeathScreen.class, "score");
            victory = AbstractDungeon.deathScreen.isVictory;
        } else if(AbstractDungeon.victoryScreen != null) {
            score = (int) ReflectionHacks.getPrivate(AbstractDungeon.victoryScreen, VictoryScreen.class, "score");
            victory = true;
        }
        state.put("score", score);
        state.put("victory", victory);
        return state;
    }

    private static HashMap<String, Object> getScreenState() {
        ChoiceScreenUtils.ChoiceType screenType = ChoiceScreenUtils.getCurrentChoiceType();
        switch (screenType) {
            case EVENT:
                return getEventState();
            case CHEST:
            case REST:
                return getRoomState();
            case CARD_REWARD:
                return getCardRewardState();
            case COMBAT_REWARD:
                return getCombatRewardState();
            case MAP:
                return getMapScreenState();
            case BOSS_REWARD:
                return getBossRewardState();
            case SHOP_SCREEN:
                return getShopScreenState();
            case GRID:
                return getGridState();
            case HAND_SELECT:
                return getHandSelectState();
            case GAME_OVER:
                return getGameOverState();
        }
        return new HashMap<>();
    }

    private static HashMap<String, Object> getCombatState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> monsters = new ArrayList<>();
        for(AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
            monsters.add(convertMonsterToJson(monster));
        }
        state.put("monsters", monsters);
        ArrayList<Object> draw_pile = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.drawPile.group) {
            draw_pile.add(convertCardToJson(card));
        }
        ArrayList<Object> discard_pile = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.discardPile.group) {
            discard_pile.add(convertCardToJson(card));
        }
        ArrayList<Object> exhaust_pile = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.exhaustPile.group) {
            exhaust_pile.add(convertCardToJson(card));
        }
        ArrayList<Object> hand = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.hand.group) {
            hand.add(convertCardToJson(card));
        }
        state.put("draw_pile", draw_pile);
        state.put("discard_pile", discard_pile);
        state.put("exhaust_pile", exhaust_pile);
        state.put("hand", hand);
        state.put("player", convertPlayerToJson(AbstractDungeon.player));
        return state;
    }

    private static ArrayList<Object> convertMapToJson() {
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        ArrayList<Object> json_map = new ArrayList<>();
        for(ArrayList<MapRoomNode> layer : map) {
            for(MapRoomNode node : layer) {
                if(node.hasEdges()) {
                    HashMap<String, Object> json_node = convertMapRoomNodeToJson(node);
                    ArrayList<Object> json_children = new ArrayList<>();
                    ArrayList<Object> json_parents = new ArrayList<>();
                    for(MapEdge edge : node.getEdges()) {
                        if (edge.srcX == node.x && edge.srcY == node.y) {
                            json_children.add(convertCoordinatesToJson(edge.dstX, edge.dstY));
                        } else {
                            json_parents.add(convertCoordinatesToJson(edge.srcX, edge.srcY));
                        }
                    }

                    json_node.put("parents", json_parents);
                    json_node.put("children", json_children);
                    json_map.add(json_node);
                }
            }
        }
        return json_map;
    }

    private static HashMap<String, Object> convertCoordinatesToJson(int x, int y) {
        HashMap<String, Object> json_node = new HashMap<>();
        json_node.put("x", x);
        json_node.put("y", y);
        return json_node;
    }

    private static HashMap<String, Object> convertMapRoomNodeToJson(MapRoomNode node) {
        HashMap<String, Object> json_node = convertCoordinatesToJson(node.x, node.y);
        json_node.put("symbol", node.getRoomSymbol(true));
        return json_node;
    }

    private static HashMap<String, Object> convertCardToJson(AbstractCard card) {
        HashMap<String, Object> json_card = new HashMap<>();
        json_card.put("name", card.name);
        json_card.put("uuid", card.uuid.toString());
        if(card.misc != 0) {
            json_card.put("misc", card.misc);
        }
        if(AbstractDungeon.getMonsters() != null) {
            json_card.put("is_playable", card.canUse(AbstractDungeon.player, null));
        }
        json_card.put("cost", card.costForTurn);
        json_card.put("upgrades", card.timesUpgraded);
        json_card.put("id", card.cardID);
        json_card.put("type", card.type.name());
        json_card.put("rarity", card.rarity.name());
        json_card.put("has_target", card.target== AbstractCard.CardTarget.SELF_AND_ENEMY || card.target == AbstractCard.CardTarget.ENEMY);
        return json_card;
    }

    private static HashMap<String, Object> convertMonsterToJson(AbstractMonster monster) {
        HashMap<String, Object> json_monster = new HashMap<>();
        json_monster.put("name", monster.id);
        json_monster.put("current_hp", monster.currentHealth);
        json_monster.put("max_hp", monster.maxHealth);
        json_monster.put("intent", monster.intent.name());
        json_monster.put("half_dead", monster.halfDead);
        json_monster.put("is_gone", monster.isDeadOrEscaped());
        json_monster.put("block", monster.currentBlock);
        json_monster.put("powers", convertCreaturePowersToJson(monster));
        return json_monster;
    }

    private static HashMap<String, Object> convertPlayerToJson(AbstractPlayer player) {
        HashMap<String, Object> json_player = new HashMap<>();
        json_player.put("max_hp", player.maxHealth);
        json_player.put("current_hp", player.currentHealth);
        json_player.put("powers", convertCreaturePowersToJson(player));
        json_player.put("energy", player.energy.energy);
        json_player.put("block", player.currentBlock);
        return json_player;
    }

    private static ArrayList<Object> convertCreaturePowersToJson(AbstractCreature creature) {
        ArrayList<Object> powers = new ArrayList<>();
        for(AbstractPower power : creature.powers) {
            HashMap<String, Object> json_power = new HashMap<>();
            json_power.put("name", power.ID);
            json_power.put("amount", power.amount);
            powers.add(json_power);
        }
        return powers;
    }

    private static HashMap<String, Object> convertRelicToJson(AbstractRelic relic) {
        HashMap<String, Object> json_relic = new HashMap<>();
        json_relic.put("id", relic.relicId);
        json_relic.put("name", relic.name);
        json_relic.put("counter", relic.counter);
        return json_relic;
    }

    private static HashMap<String, Object> convertPotionToJson(AbstractPotion potion) {
        HashMap<String, Object> json_potion = new HashMap<>();
        json_potion.put("id", potion.ID);
        json_potion.put("name", potion.name);
        boolean canUse = potion.canUse();
        boolean canDiscard = potion.canDiscard();
        if (potion instanceof PotionSlot) {
            canDiscard = canUse = false;
        }
        json_potion.put("can_use", canUse);
        json_potion.put("can_discard", canDiscard);
        return json_potion;
    }

}
