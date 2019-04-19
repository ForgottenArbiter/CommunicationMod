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
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.RunicDome;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

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
        // Lots of stuff can happen while the dungeon is fading out, but nothing that requires input from the user.
        if(AbstractDungeon.isFadingOut || AbstractDungeon.isFadingIn) {
            return false;
        }
        // This check happens before the rest since dying can happen in combat and messes with the other cases.
        if(newScreen == AbstractDungeon.CurrentScreen.DEATH && newScreen != previousScreen) {
            return true;
        }
        // These screens have no interaction available.
        if(newScreen == AbstractDungeon.CurrentScreen.DOOR_UNLOCK || newScreen == AbstractDungeon.CurrentScreen.NO_INTERACT) {
            return false;
        }
        // We are not ready to receive commands when it is not our turn, except for some pesky screens
        if(inCombat && (!myTurn || AbstractDungeon.getMonsters().areMonstersBasicallyDead())) {
            if(!newScreenUp) {
                return false;
            }
        }
        // In event rooms, we need to wait for the event wait timer to reach 0 before we can accurately assess its state.
        AbstractRoom currentRoom = AbstractDungeon.getCurrRoom();
        if((currentRoom instanceof EventRoom
                  || currentRoom instanceof NeowRoom
                  || (currentRoom instanceof  VictoryRoom && ((VictoryRoom)currentRoom).eType == VictoryRoom.EventType.HEART ))
                && AbstractDungeon.getCurrRoom().event.waitTimer != 0.0F) {
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
                else if(AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER)
                        && AbstractDungeon.actionManager.cardQueue.isEmpty()
                        && AbstractDungeon.actionManager.actions.isEmpty()) {
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
        if((externalChange || previousGold != AbstractDungeon.player.gold)
                && AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER)
                && AbstractDungeon.actionManager.preTurnActions.isEmpty()
                && AbstractDungeon.actionManager.actions.isEmpty()
                && AbstractDungeon.actionManager.cardQueue.isEmpty()) {
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

    public static boolean isWaitingForCommand() {
        return waitingForCommand;
    }

    /**
     * Creates a JSON representation of the status of CommunicationMod that will be sent to the external process.
     * The JSON object returned contains:
     * - "available_commands": A list of commands (Strings) available to the user (list)
     * - "ready_for_command": Denotes whether the game state is stable and ready to receive a command (boolean)
     * - "in_game": True if in the main menu, False if the player is in the dungeon (boolean)
     * - "game_state": Present if in_game=True, contains the game state object returned by getGameState() (object)
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
     * - "screen_name": The name of the Enum representing the current screen (defined by Mega Crit) (string)
     * - "is_screen_up": The game's isScreenUp variable (boolean)
     * - "screen_type": The type of screen (or decision) that the user if facing (defined by Communication Mod) (string)
     * - "screen_state": The state of the current state, see getScreenState() (as defined by Communication Mod) (object)
     * - "room_phase": The phase of the current room (COMBAT, EVENT, etc.) (string)
     * - "action_phase": The phase of the action manager (WAITING_FOR_USER_INPUT, EXECUTING_ACTIONS) (string)
     * - "room_type": The name of the class of the current room (ShopRoom, TreasureRoom, MonsterRoom, etc.) (string)
     * - "current_hp": The player's current hp (int)
     * - "max_hp": The player's maximum hp (int)
     * - "floor": The current floor number (int)
     * - "act": The current act number (int)
     * - "gold": The player's current gold total (int)
     * - "seed": The seed used by the current game (long)
     * - "class": The player's current class (string)
     * - "ascension_level": The ascension level of the current run (int)
     * - "relics": A list of the player's current relics (list)
     * - "deck": A list of the cards in the player's deck (list)
     * - "potions": A list of the player's potions (empty slots are PotionSlots) (list)
     * - "map": The current dungeon map (list)
     * Sometimes present:
     * - "current_action": The class name of the action in the action manager queue, if not empty (list)
     * - "combat_state": The state of the combat (draw pile, monsters, etc.) (object)
     * - "choice_list": If the command is available, the possible choices for the choose command (list)
     * @return A HashMap encoding the JSON representation of the game state
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
     * This method removes the special text formatting characters found in the game.
     * These extra formatting characters are turned into things like colored or wiggly text in game, but
     * we would like to report the text without dealing with these characters.
     * @param text The text for which the formatting should be removed
     * @return The input text, with the formatting characters removed
     */
    private static String removeTextFormatting(String text) {
        text = text.replaceAll("~|@(\\S+)~|@", "$1");
        return text.replaceAll("#.|NL", "");
    }

    /**
     * The event state object contains:
     * "body_text": The current body text for the event, or an empty string if there is none (string)
     * "event_name": The name of the event, in the current language (string)
     * "event_id": The ID of the event (NOTE: This implementation is sketchy and may not play nice with mods) (string)
     * "options": A list of options, in the order they are presented in game. Each option contains:
     * - "text": The full text associated with the option (Eg. "[Banana] Heal 10 hp") (string)
     * - "disabled": Whether the current option or button is disabled. Disabled buttons cannot be chosen (boolean)
     * - "label": The simple label of a button or option (Eg. "Banana") (string)
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
            try {
                // AbstractEvent does not have a static "ID" field, but all of the events in the base game do.
                Field targetField = event.getClass().getDeclaredField("ID");
                state.put("event_id", (String)targetField.get(null));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                state.put("event_id", "");
            }
            state.put("event_id", ReflectionHacks.getPrivateStatic(event.getClass(), "ID"));
        }
        state.put("options", options);
        return state;
    }

    /**
     * The card reward state object contains:
     * "bowl_available": Whether the Singing Bowl button is present (boolean)
     * "skip_available": Whether the card reward is skippable (boolean)
     * "cards": The list of cards that can be chosen (list)
     * @return The card reward state object
     */
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

    /**
     * The combat reward screen state object contains:
     * "rewards": A list of reward objects, each of which contains:
     * - "reward_type": The name of the RewardItem.RewardType enum for the reward (string)
     * - "gold": The amount of gold in the reward, if applicable (int)
     * - "relic": The relic in the reward, if applicable (object)
     * - "potion": The potion in the reward, if applicable (object)
     * - "link": The relic that the sapphire key is linked to, if applicable (object)
     * @return The combat reward screen state object
     */
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

    /**
     * The map screen state object contains:
     * "current_node": The node object for the currently selected node, if applicable (object)
     * "next_nodes": A list of nodes that can be chosen next (list)
     * "first_node_chosen": Whether the first node in the act has already been chosen (boolean)
     * "boss_available": Whether the next node choice is a boss (boolean)
     * @return The map screen state object
     */
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
        state.put("first_node_chosen", AbstractDungeon.firstRoomChosen);
        state.put("boss_available", ChoiceScreenUtils.bossNodeAvailable());
        return state;
    }

    /**
     * The boss reward screen state contains:
     * "relics": A list of relics that can be chosen from the boss (list)
     * Note: Blights are not supported.
     * @return The boss reward screen state object
     */
    private static HashMap<String, Object> getBossRewardState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> bossRelics = new ArrayList<>();
        for(AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
            bossRelics.add(convertRelicToJson(relic));
        }
        state.put("relics", bossRelics);
        return state;
    }

    /**
     * The shop screen state contains:
     * "cards": A list of cards available to buy (list)
     * "relics": A list of relics available to buy (list)
     * "potions": A list of potions available to buy (list)
     * "purge_available": Whether the card remove option is available (boolean)
     * "purge_cost": The cost of the card remove option (int)
     * @return The shop screen state object
     */
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

    /**
     * The grid select screen state contains:
     * "cards": The list of cards available to pick, including selected cards (list)
     * "selected_cards": The list of cards that are currently selected (list)
     * "num_cards": The number of cards that must be selected (int)
     * "for_upgrade": Whether the selected cards will be upgraded (boolean)
     * "for_transform": Whether the selected cards will be transformed (boolean)
     * _for_purge": Whether the selected cards will be removed from the deck (boolean)
     * "confirm_up": Whether the confirm screen is up, and cards cannot be selected (boolean)
     * @return The grid select screen state object
     */
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
        state.put("confirm_up", screen.confirmScreenUp || screen.isJustForConfirming);
        return state;
    }

    /**
     * The hand select screen state contains:
     * "hand": The list of cards currently in your hand, not including selected cards (list)
     * "selected": The list of currently selected cards (list)
     * "max_cards": The maximum number of cards that can be selected (int)
     * "can_pick_zero": Whether zero cards can be selected (boolean)
     * @return The hand select screen state object
     */
    private static HashMap<String, Object> getHandSelectState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> handJson = new ArrayList<Object>();
        ArrayList<Object> selectedJson = new ArrayList<Object>();
        ArrayList<AbstractCard> handCards = AbstractDungeon.player.hand.group;
        // As far as I can tell, this comment is a Java 8 analogue of a Python list comprehension? I think just looping is more readable.
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

    /**
     * The game over screen state contains:
     * "score": Your final score (int)
     * "victory": Whether you won (boolean)
     * @return The game over screen state object
     */
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

    /**
     * Gets the appropriate screen state object
     * @return An object containing your current screen state
     */
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

    /**
     * Gets the state of the current combat in game.
     * The combat state object contains:
     * "draw_pile": The list of cards in your draw pile (list)
     * "discard_pile": The list of cards in your discard pile (list)
     * "exhaust_pile": The list of cards in your exhaust pile (list)
     * "hand": The list of cards in your hand (list)
     * "player": The state of the player (object)
     * "monsters": A list of the enemies in the combat, including dead enemies (list)
     * Note: The order of the draw pile is not currently randomized when sent to the client.
     * @return The combat state object
     */
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

    /**
     * Creates a GSON-compatible representation of the game map
     * The map object is a list of nodes, each of which with two extra fields:
     * "parents": Not implemented (list)
     * "children": The nodes connected by an edge out of the node in question (list)
     * @return A list of node objects
     */
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

    /**
     * Creates a GSON-compatible representation of the given node
     * The node object contains:
     * "x": The node's x coordinate (int)
     * "y": The node's y coordinate (int)
     * "symbol": The map symbol for the node (?, $, T, M, E, R) (string, optional)
     * "children": The nodes connected by an edge out of the provided node (list, optional)
     * Note: children are added by convertMapToJson()
     * @param node The node to convert
     * @return A node object
     */
    private static HashMap<String, Object> convertMapRoomNodeToJson(MapRoomNode node) {
        HashMap<String, Object> json_node = convertCoordinatesToJson(node.x, node.y);
        json_node.put("symbol", node.getRoomSymbol(true));
        return json_node;
    }

    /**
     * Creates a GSON-compatible representation of the given cards
     * The card object contains:
     * "name": The name of the card, in the currently selected language (string)
     * "uuid": The unique identifier of the card (string)
     * "misc": The misc field for the card, used by cards like Ritual Dagger (int)
     * "is_playable": Whether the card can currently be played, though does not guarantee a target (boolean)
     * "cost": The current cost of the card. -2 is unplayable and -1 is X cost (int)
     * "upgrades": The number of times the card is upgraded (int)
     * "id": The id of the card (string)
     * "type": The name of the AbstractCard.CardType enum for the card (string)
     * "rarity": The name of the AbstractCard.CardRarity enum for the card (string)
     * "has_target": Whether the card requires a target to be played (boolean)
     * "exhausts": Whether the card exhausts when played (boolean)
     * @param card The card to convert
     * @return A card object
     */
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
        json_card.put("exhausts", card.exhaust);
        return json_card;
    }

    /**
     * Creates a GSON-compatible representation of the given monster
     * The monster object contains:
     * "name": The monster's name, in the currently selected language (string)
     * "id": The monster's id (string)
     * "current_hp": The monster's current hp (int)
     * "max_hp": The monster's maximum hp (int)
     * "block": The monster's current block
     * "intent": The name of the AbstractMonster.Intent enum for the monster's current intent (string)
     * "move_id": The move id byte for the monster's current move (int)
     * "move_base_damage": The base damage for the monster's current attack (int)
     * "move_adjusted_damage": The damage number actually shown on the intent for the monster's current attack (int)
     * "move_hits": The number of hits done by the current attack (int)
     * "half_dead": Whether the monster is half dead (boolean)
     * "is_gone": Whether the monster is dead or has run away (boolean)
     * "powers": The monster's current powers (list)
     * Note: If the player has Runic Dome, intent will always return NONE
     * @param monster The monster to convert
     * @return A monster object
     */
    private static HashMap<String, Object> convertMonsterToJson(AbstractMonster monster) {
        HashMap<String, Object> json_monster = new HashMap<>();
        json_monster.put("id", monster.id);
        json_monster.put("name", monster.name);
        json_monster.put("current_hp", monster.currentHealth);
        json_monster.put("max_hp", monster.maxHealth);
        if (AbstractDungeon.player.hasRelic(RunicDome.ID)) {
            json_monster.put("intent", AbstractMonster.Intent.NONE);
        } else {
            json_monster.put("intent", monster.intent.name());
            EnemyMoveInfo moveInfo = (EnemyMoveInfo)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "move");
            if (moveInfo != null) {
                json_monster.put("move_id", moveInfo.nextMove);
                json_monster.put("move_base_damage", moveInfo.baseDamage);
                int intentDmg = (int)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "intentDmg");
                json_monster.put("move_adjusted_damage", intentDmg);
                int move_hits = moveInfo.multiplier;
                // If isMultiDamage is not set, the multiplier is probably 0, but there is really 1 attack.
                if (!moveInfo.isMultiDamage) {
                    move_hits = 1;
                }
                json_monster.put("move_hits", move_hits);
            }
        }
        json_monster.put("half_dead", monster.halfDead);
        json_monster.put("is_gone", monster.isDeadOrEscaped());
        json_monster.put("block", monster.currentBlock);
        json_monster.put("powers", convertCreaturePowersToJson(monster));
        return json_monster;
    }

    /**
     * Creates a GSON-compatible representation of the given player
     * The player object contains:
     * "max_hp": The player's maximum hp (int)
     * "current_hp": The player's current hp (int)
     * "block": The player's current block (int)
     * "powers": The player's current powers (list)
     * "energy": The player's current energy (int)
     * Note: many other things, like draw pile and discard pile, are in the combat state
     * @param player The player to convert
     * @return A player object
     */
    private static HashMap<String, Object> convertPlayerToJson(AbstractPlayer player) {
        HashMap<String, Object> json_player = new HashMap<>();
        json_player.put("max_hp", player.maxHealth);
        json_player.put("current_hp", player.currentHealth);
        json_player.put("powers", convertCreaturePowersToJson(player));
        json_player.put("energy", player.energy.energy);
        json_player.put("block", player.currentBlock);
        return json_player;
    }

    /**
     * Creates a GSON-compatible representation of the given creature's powers
     * The power object contains:
     * "id": The id of the power (string)
     * "name": The name of the power, in the currently selected language (string)
     * "amount": The amount of the power (int)
     * @param creature The creature whose powers are to be converted
     * @return A list of power objects
     */
    private static ArrayList<Object> convertCreaturePowersToJson(AbstractCreature creature) {
        ArrayList<Object> powers = new ArrayList<>();
        for(AbstractPower power : creature.powers) {
            HashMap<String, Object> json_power = new HashMap<>();
            json_power.put("id", power.ID);
            json_power.put("name", power.name);
            json_power.put("amount", power.amount);
            powers.add(json_power);
        }
        return powers;
    }

    /**
     * Creates a GSON-compatible representation of the given relic
     * The relic object contains:
     * "id": The id of the relic (string)
     * "name": The name of the relic, in the currently selected language (string)
     * "counter": The counter on the relic (int)
     * @param relic The relic to convert
     * @return A relic object
     */
    private static HashMap<String, Object> convertRelicToJson(AbstractRelic relic) {
        HashMap<String, Object> json_relic = new HashMap<>();
        json_relic.put("id", relic.relicId);
        json_relic.put("name", relic.name);
        json_relic.put("counter", relic.counter);
        return json_relic;
    }

    /**
     * Creates a GSON-compatible representation of the given potion
     * The potion object contains:
     * "id": The id of the potion (string)
     * "name": The name of the potion, in the currently selected language (string)
     * "can_use": Whether the potion can currently be used (boolean)
     * "can_discard": Whether the potion can currently be discarded (boolean)
     * "requires_target": Whether the potion must be used with a target (boolean)
     * @param potion The potion to convert
     * @return A potion object
     */
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
        json_potion.put("requires_target", potion.isThrown);
        return json_potion;
    }

}
