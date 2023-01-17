package communicationmod;

import basemod.ReflectionHacks;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.monsters.beyond.*;
import com.megacrit.cardcrawl.monsters.city.BookOfStabbing;
import com.megacrit.cardcrawl.monsters.city.Champ;
import com.megacrit.cardcrawl.monsters.exordium.Hexaghost;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.RunicDome;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.GameOverScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import communicationmod.patches.UpdateBodyTextPatch;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

public class GameStateConverter {

    /**
     * Creates a JSON representation of the status of CommunicationMod that will be sent to the external process.
     * The JSON object returned contains:
     * - "available_commands" (list): A list of commands (strings) available to the user
     * - "ready_for_command" (boolean): Denotes whether the game state is stable and ready to receive a command
     * - "in_game" (boolean): True if in the main menu, False if the player is in the dungeon
     * - "game_state" (object): Present if in_game=True, contains the game state object returned by getGameState()
     * @return A string containing the JSON representation of CommunicationMod's status
     */
    public static String getCommunicationState() {
        HashMap<String, Object> response = new HashMap<>();
        response.put("available_commands", CommandExecutor.getAvailableCommands());
        response.put("ready_for_command", GameStateListener.isWaitingForCommand());
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
     * - "screen_name" (string): The name of the Enum representing the current screen (defined by Mega Crit)
     * - "is_screen_up" (boolean): The game's isScreenUp variable
     * - "screen_type" (string): The type of screen (or decision) that the user if facing (defined by Communication Mod)
     * - "screen_state" (object): The state of the current state, see getScreenState() (as defined by Communication Mod)
     * - "room_phase" (string): The phase of the current room (COMBAT, EVENT, etc.)
     * - "action_phase" (string): The phase of the action manager (WAITING_FOR_USER_INPUT, EXECUTING_ACTIONS)
     * - "room_type" (string): The name of the class of the current room (ShopRoom, TreasureRoom, MonsterRoom, etc.)
     * - "current_hp" (int): The player's current hp
     * - "max_hp" (int): The player's maximum hp
     * - "floor" (int): The current floor number
     * - "act" (int): The current act number
     * - "act_boss" (string): The name of the current Act's visible boss encounter
     * - "gold" (int): The player's current gold total
     * - "seed" (long): The seed used by the current game
     * - "class" (string): The player's current class
     * - "ascension_level" (int): The ascension level of the current run
     * - "relics" (list): A list of the player's current relics
     * - "deck" (list): A list of the cards in the player's deck
     * - "potions" (list): A list of the player's potions (empty slots are PotionSlots)
     * - "map" (list): The current dungeon map
     * Sometimes present:
     * - "current_action" (list): The class name of the action in the action manager queue, if not empty
     * - "combat_state" (list): The state of the combat (draw pile, monsters, etc.)
     * - "choice_list" (list): If the command is available, the possible choices for the choose command
     * @return A HashMap encoding the JSON representation of the game state
     */
    private static HashMap<String, Object> getGameState() {
        HashMap<String, Object> state = new HashMap<>();

        state.put("screen_name", AbstractDungeon.screen.name());
        state.put("is_screen_up", AbstractDungeon.isScreenUp);
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
        state.put("act_boss", AbstractDungeon.bossKey);
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
     * "body_text" (string): The current body text for the event, or an empty string if there is none
     * "event_name" (string): The name of the event, in the current language
     * "event_id" (string): The ID of the event (NOTE: This implementation is sketchy and may not play nice with mods)
     * "options" (list): A list of options, in the order they are presented in game. Each option contains:
     * - "text" (string): The full text associated with the option (Eg. "[Banana] Heal 10 hp")
     * - "disabled" (boolean): Whether the current option or button is disabled. Disabled buttons cannot be chosen
     * - "label" (string): The simple label of a button or option (Eg. "Banana")
     * - "choice_index" (int): The index of the option for the choose command, if applicable
     * @return The event state object
     */
    private static HashMap<String, Object> getEventState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> options = new ArrayList<>();
        ChoiceScreenUtils.EventDialogType eventDialogType = ChoiceScreenUtils.getEventDialogType();
        AbstractEvent event = AbstractDungeon.getCurrRoom().event;
        int choice_index = 0;
        if (eventDialogType == ChoiceScreenUtils.EventDialogType.IMAGE || eventDialogType == ChoiceScreenUtils.EventDialogType.ROOM) {
            for (LargeDialogOptionButton button : ChoiceScreenUtils.getEventButtons()) {
                HashMap<String, Object> json_button = new HashMap<>();
                json_button.put("text", removeTextFormatting(button.msg));
                json_button.put("disabled", button.isDisabled);
                json_button.put("label", ChoiceScreenUtils.getOptionName(button.msg));
                if (!button.isDisabled) {
                    json_button.put("choice_index", choice_index);
                    choice_index += 1;
                }
                options.add(json_button);
            }
            state.put("body_text", removeTextFormatting(UpdateBodyTextPatch.bodyText));
        } else {
            for (String misc_option : ChoiceScreenUtils.getEventScreenChoices()) {
                HashMap<String, Object> json_button = new HashMap<>();
                json_button.put("text", misc_option);
                json_button.put("disabled", false);
                json_button.put("label", misc_option);
                json_button.put("choice_index", choice_index);
                choice_index += 1;
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
     * "bowl_available" (boolean): Whether the Singing Bowl button is present
     * "skip_available" (boolean): Whether the card reward is skippable
     * "cards" (list): The list of cards that can be chosen
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
     * "rewards" (list): A list of reward objects, each of which contains:
     * - "reward_type" (string): The name of the RewardItem.RewardType enum for the reward
     * - "gold" (int): The amount of gold in the reward, if applicable
     * - "relic" (object): The relic in the reward, if applicable
     * - "potion" (object): The potion in the reward, if applicable
     * - "link" (object): The relic that the sapphire key is linked to, if applicable
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
     * "current_node" (object): The node object for the currently selected node, if applicable
     * "next_nodes" (list): A list of nodes that can be chosen next
     * "first_node_chosen" (boolean): Whether the first node in the act has already been chosen
     * "boss_available" (boolean): Whether the next node choice is a boss
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
     * "relics" (list): A list of relics that can be chosen from the boss
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
     * "cards" (list): A list of cards available to buy
     * "relics" (list): A list of relics available to buy
     * "potions" (list): A list of potions available to buy
     * "purge_available" (boolean): Whether the card remove option is available
     * "purge_cost" (int): The cost of the card remove option
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
     * "cards" (list): The list of cards available to pick, including selected cards
     * "selected_cards" (list): The list of cards that are currently selected
     * "num_cards" (int): The number of cards that must be selected
     * "any_number" (boolean): Whether any number of cards can be selected
     * "for_upgrade" (boolean): Whether the selected cards will be upgraded
     * "for_transform" (boolean): Whether the selected cards will be transformed
     * _for_purge" (boolean): Whether the selected cards will be removed from the deck
     * "confirm_up" (boolean): Whether the confirm screen is up, and cards cannot be selected
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
        state.put("any_number", screen.anyNumber);
        state.put("for_upgrade", forUpgrade);
        state.put("for_transform", forTransform);
        state.put("for_purge", forPurge);
        state.put("confirm_up", screen.confirmScreenUp || screen.isJustForConfirming);
        return state;
    }

    /**
     * The hand select screen state contains:
     * "hand" (list): The list of cards currently in your hand, not including selected cards
     * "selected" (list): The list of currently selected cards
     * "max_cards" (int): The maximum number of cards that can be selected
     * "can_pick_zero" (boolean): Whether zero cards can be selected
     * @return The hand select screen state object
     */
    private static HashMap<String, Object> getHandSelectState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> handJson = new ArrayList<>();
        ArrayList<Object> selectedJson = new ArrayList<>();
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
     * "score" (int): Your final score
     * "victory" (boolean): Whether you won
     * @return The game over screen state object
     */
    private static HashMap<String, Object> getGameOverState() {
        HashMap<String, Object> state = new HashMap<>();
        int score = 0;
        boolean victory = false;
        if(AbstractDungeon.screen == AbstractDungeon.CurrentScreen.DEATH) {
            score = (int) ReflectionHacks.getPrivate(AbstractDungeon.deathScreen, GameOverScreen.class, "score");
            victory = GameOverScreen.isVictory;
        } else if(AbstractDungeon.screen == AbstractDungeon.CurrentScreen.VICTORY) {
            score = (int) ReflectionHacks.getPrivate(AbstractDungeon.victoryScreen, GameOverScreen.class, "score");
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
     * "draw_pile" (list): The list of cards in your draw pile
     * "discard_pile" (list): The list of cards in your discard pile
     * "exhaust_pile" (list): The list of cards in your exhaust pile
     * "hand" (list): The list of cards in your hand
     * "limbo" (list): The list of cards that are in 'limbo', which is used for a variety of effects in game.
     * "card_in_play" (object, optional): The card that is currently in play, if any.
     * "player" (object): The state of the player
     * "monsters" (list): A list of the enemies in the combat, including dead enemies
     * "turn" (int): The current turn (or round) number of the combat.
     * "cards_discarded_this_turn" (int): The number of cards discarded this turn.
     * "times_damaged" (int): The number of times the player has been damaged this combat (for Blood for Blood).
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
        ArrayList<Object> limbo = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.limbo.group) {
            limbo.add(convertCardToJson(card));
        }
        state.put("draw_pile", draw_pile);
        state.put("discard_pile", discard_pile);
        state.put("exhaust_pile", exhaust_pile);
        state.put("hand", hand);
        state.put("limbo", limbo);
        if (AbstractDungeon.player.cardInUse != null) {
            state.put("card_in_play", convertCardToJson(AbstractDungeon.player.cardInUse));
        }
        state.put("player", convertPlayerToJson(AbstractDungeon.player));
        state.put("turn", GameActionManager.turn);
        state.put("cards_discarded_this_turn", GameActionManager.totalDiscardedThisTurn);
        state.put("times_damaged", AbstractDungeon.player.damagedThisCombat);
        return state;
    }

    /**
     * Creates a GSON-compatible representation of the game map
     * The map object is a list of nodes, each of which with two extra fields:
     * "parents" (list): Not implemented
     * "children" (list): The nodes connected by an edge out of the node in question
     * @return A list of node objects
     */
    private static ArrayList<Object> convertMapToJson() {
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        ArrayList<Object> jsonMap = new ArrayList<>();
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
                    jsonMap.add(json_node);
                }
            }
        }
        return jsonMap;
    }

    private static HashMap<String, Object> convertCoordinatesToJson(int x, int y) {
        HashMap<String, Object> jsonNode = new HashMap<>();
        jsonNode.put("x", x);
        jsonNode.put("y", y);
        return jsonNode;
    }

    /**
     * Creates a GSON-compatible representation of the given node
     * The node object contains:
     * "x" (int): The node's x coordinate
     * "y" (int): The node's y coordinate
     * "symbol" (string, optional): The map symbol for the node (?, $, T, M, E, R)
     * "children" (list, optional): The nodes connected by an edge out of the provided node
     * Note: children are added by convertMapToJson()
     * @param node The node to convert
     * @return A node object
     */
    private static HashMap<String, Object> convertMapRoomNodeToJson(MapRoomNode node) {
        HashMap<String, Object> jsonNode = convertCoordinatesToJson(node.x, node.y);
        jsonNode.put("symbol", node.getRoomSymbol(true));
        return jsonNode;
    }

    /**
     * Creates a GSON-compatible representation of the given cards
     * The card object contains:
     * "name" (string): The name of the card, in the currently selected language
     * "uuid" (string): The unique identifier of the card
     * "misc" (int): The misc field for the card, used by cards like Ritual Dagger
     * "is_playable" (boolean): Whether the card can currently be played, though does not guarantee a target
     * "cost" (int): The current cost of the card. -2 is unplayable and -1 is X cost
     * "upgrades" (int): The number of times the card is upgraded
     * "id" (string): The id of the card
     * "type" (string): The name of the AbstractCard.CardType enum for the card
     * "rarity" (string): The name of the AbstractCard.CardRarity enum for the card
     * "has_target" (boolean): Whether the card requires a target to be played
     * "exhausts" (boolean): Whether the card exhausts when played
     * "ethereal" (boolean): Whether the card is ethereal
     * @param card The card to convert
     * @return A card object
     */
    private static HashMap<String, Object> convertCardToJson(AbstractCard card) {
        HashMap<String, Object> jsonCard = new HashMap<>();
        jsonCard.put("name", card.name);
        jsonCard.put("uuid", card.uuid.toString());
        if(card.misc != 0) {
            jsonCard.put("misc", card.misc);
        }
        if(AbstractDungeon.getMonsters() != null) {
            jsonCard.put("is_playable", card.canUse(AbstractDungeon.player, null));
        }
        jsonCard.put("cost", card.costForTurn);
        jsonCard.put("upgrades", card.timesUpgraded);
        jsonCard.put("id", card.cardID);
        jsonCard.put("type", card.type.name());
        jsonCard.put("rarity", card.rarity.name());
        jsonCard.put("has_target", card.target== AbstractCard.CardTarget.SELF_AND_ENEMY || card.target == AbstractCard.CardTarget.ENEMY);
        jsonCard.put("exhausts", card.exhaust);
        jsonCard.put("ethereal", card.isEthereal);
        return jsonCard;
    }

    /**
     * Creates a GSON-compatible representation of the given monster
     * The monster object contains:
     * "name" (string): The monster's name, in the currently selected language
     * "id" (string): The monster's id
     * "current_hp" (int): The monster's current hp
     * "max_hp" (int): The monster's maximum hp
     * "block" (int): The monster's current block
     * "intent" (string): The name of the AbstractMonster.Intent enum for the monster's current intent
     * "move_id" (int): The move id byte for the monster's current move
     * "move_base_damage" (int): The base damage for the monster's current attack
     * "move_adjusted_damage" (int): The damage number actually shown on the intent for the monster's current attack
     * "move_hits" (int): The number of hits done by the current attack
     * "last_move_id" (int): The move id byte for the monster's previous move
     * "second_last_move_id" (int): The move id byte from 2 moves ago
     * "half_dead" (boolean): Whether the monster is half dead
     * "is_gone" (boolean): Whether the monster is dead or has run away
     * "powers" (list): The monster's current powers
     * Note: If the player has Runic Dome, intent will always return NONE
     * @param monster The monster to convert
     * @return A monster object
     */
    private static HashMap<String, Object> convertMonsterToJson(AbstractMonster monster) {
        HashMap<String, Object> jsonMonster = new HashMap<>();
        jsonMonster.put("id", monster.id);
        jsonMonster.put("name", monster.name);
        jsonMonster.put("current_hp", monster.currentHealth);
        jsonMonster.put("max_hp", monster.maxHealth);
        // send over full intent information
        // the user must exclude this if runic dome is present if they wish to avoid "cheating"
        jsonMonster.put("intent", monster.intent.name());
        EnemyMoveInfo moveInfo = (EnemyMoveInfo)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "move");
        if (moveInfo != null) {
            jsonMonster.put("move_id", moveInfo.nextMove);
            jsonMonster.put("move_base_damage", moveInfo.baseDamage);
            int intentDmg = (int)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "intentDmg");
            if (moveInfo.baseDamage > 0) {
                jsonMonster.put("move_adjusted_damage", intentDmg);
            } else {
                jsonMonster.put("move_adjusted_damage", moveInfo.baseDamage);
            }
            int move_hits = moveInfo.multiplier;
            // If isMultiDamage is not set, the multiplier is probably 0, but there is really 1 attack.
            if (!moveInfo.isMultiDamage) {
                move_hits = 1;
            }
            jsonMonster.put("move_hits", move_hits);
        }
        if (monster.moveHistory.size() >= 2) {
            jsonMonster.put("last_move_id", monster.moveHistory.get(monster.moveHistory.size() - 2));
        }
        if (monster.moveHistory.size() >= 3) {
            jsonMonster.put("second_last_move_id", monster.moveHistory.get(monster.moveHistory.size() - 3));
        }

        // some monster-specific information that isn't a part of powers
        String[] miscIntFieldNames = {
                "nipDmg", "thornsCount", "stabCount", "biteDmg", "currentCharge"
        };
        // nipDmg is the darkling nip attack damage amount (only available to the player after the first time the darkling rolls nip)
        // thornsCount is the number of times a spiker has buffed its thorns amount
        // stabCount is the number of stabs the book of stabbing does in its multi-attack
        // biteDmg is the louse bite attack damage amount (only available to the player after the first time the louse rolls bite)
        // currentCharge is the amount of turns the gremlin wizard has charged up
        Object misc;
        for (String fieldName : miscIntFieldNames) {
            misc = getFieldIfExists(monster, fieldName);
            if (misc != null) {
                jsonMonster.put("miscInt", (int)misc);
                break;
            }
        }
        String[] miscBoolFieldNames = {
                "thresholdReached", "usedHaste", "form1", "usedMegaDebuff", "usedStasis"
        };
        // thresholdReached is if champ has gone below half HP - this is probably redundant
        // usedHaste is if time eater has used haste (the heal move)
        // form1 is if awakened one is in its first form still
        // usedMegaDebuff is if writhing mass has used implant (the curse move)
        // usedStasis is if the bronze orb has used stasis yet
        for (String fieldName : miscBoolFieldNames) {
            misc = getFieldIfExists(monster, fieldName);
            if (misc != null) {
                jsonMonster.put("miscBool", (boolean)misc);
                break;
            }
        }
        if (monster instanceof Hexaghost) {
            jsonMonster.put("active_orbs", getFieldIfExists(monster, "orbActiveCount"));
            jsonMonster.put("miscInt", monster.damage.get(2).base);
        }

        jsonMonster.put("half_dead", monster.halfDead);
        jsonMonster.put("is_escaping", monster.isEscaping);
        jsonMonster.put("is_gone", monster.isDeadOrEscaped());
        jsonMonster.put("block", monster.currentBlock);
        jsonMonster.put("powers", convertCreaturePowersToJson(monster));
        return jsonMonster;
    }

    /**
     * Creates a GSON-compatible representation of the given player
     * The player object contains:
     * "max_hp" (int): The player's maximum hp
     * "current_hp" (int): The player's current hp
     * "block" (int): The player's current block
     * "powers" (list): The player's current powers
     * "energy" (int): The player's current energy
     * "orbs" (list): The player's current orb slots
     * Note: many other things, like draw pile and discard pile, are in the combat state
     * @param player The player to convert
     * @return A player object
     */
    private static HashMap<String, Object> convertPlayerToJson(AbstractPlayer player) {
        HashMap<String, Object> jsonPlayer = new HashMap<>();
        jsonPlayer.put("max_hp", player.maxHealth);
        jsonPlayer.put("current_hp", player.currentHealth);
        jsonPlayer.put("powers", convertCreaturePowersToJson(player));
        jsonPlayer.put("energy", EnergyPanel.totalCount);
        jsonPlayer.put("block", player.currentBlock);
        ArrayList<Object> orbs = new ArrayList<>();
        for(AbstractOrb orb : player.orbs) {
            orbs.add(convertOrbToJson(orb));
        }
        jsonPlayer.put("orbs", orbs);
        return jsonPlayer;
    }

    /**
     * Checks whether the given object has the specified field. If so, returns the field's value. Else returns null.
     * @param object The object used to look for the specified field
     * @param fieldName The field that we want to access
     * @return The value of the field, if present, or else null.
     */
    private static Object getFieldIfExists(Object object, String fieldName) {
        Class objectClass = object.getClass();
        for (Field field : objectClass.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                try {
                    field.setAccessible(true);
                    return field.get(object);
                } catch(IllegalAccessException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Creates a GSON-compatible representation of the given creature's powers
     * The power object contains:
     * "id" (string): The id of the power
     * "name" (string): The name of the power, in the currently selected language
     * "amount" (int): The amount of the power
     * "damage" (int, optional): The amount of damage the power does, if applicable
     * "card" (object, optional): The card associated with the power (for powers like Nightmare)
     * "misc" (int, optional): Contains misc values that don't fit elsewhere (such as the base value for Flight)
     * "just_applied" (boolean, optional): Used with many powers to prevent them from expiring immediately
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
            Object damage = getFieldIfExists(power, "damage");
            if (damage != null) {
                json_power.put("damage", (int)damage);
            }
            Object card = getFieldIfExists(power, "card");
            if (card != null) {
                json_power.put("card", convertCardToJson((AbstractCard)card));
            }
            String[] miscFieldNames = {
                    "basePower", "maxAmt", "storedAmount", "hpLoss", "cardsDoubledThisTurn", "energyGainAmount"
            };
            // basePower gives the base power for Malleable
            // maxAmt gives the max amount of damage per turn for Invincible
            // storedAmount gives the number of stacks per turn for Flight
            // hpLoss gives the amount of HP lost per turn with Combust
            // cardsDoubledThisTurn gives the number of cards already doubled with Echo Form
            // energyGainedAmount gives the amount of energy per turn given by Deva Form
            Object misc = null;
            for (String fieldName : miscFieldNames) {
                misc = getFieldIfExists(power, fieldName);
                if (misc != null) {
                    json_power.put("misc", (int)misc);
                    break;
                }
            }

            String[] justAppliedNames = {
                    "justApplied", "skipFirst"
            };
            // justApplied is used with a variety of powers to prevent them from expiring immediately (cast from bool)
            // skipFirst is the same as justApplied, for the Ritual power
            Object justApplied = null;
            for (String fieldName : justAppliedNames) {
                justApplied = getFieldIfExists(power, fieldName);
                if (justApplied != null) {
                    json_power.put("just_applied", (boolean)justApplied);
                    break;
                }
            }

            powers.add(json_power);
        }
        return powers;
    }

    /**
     * Creates a GSON-compatible representation of the given relic
     * The relic object contains:
     * "id" (string): The id of the relic
     * "name" (string): The name of the relic, in the currently selected language
     * "counter" (int): The counter on the relic
     * @param relic The relic to convert
     * @return A relic object
     */
    private static HashMap<String, Object> convertRelicToJson(AbstractRelic relic) {
        HashMap<String, Object> jsonRelic = new HashMap<>();
        jsonRelic.put("id", relic.relicId);
        jsonRelic.put("name", relic.name);
        jsonRelic.put("counter", relic.counter);
        return jsonRelic;
    }

    /**
     * Creates a GSON-compatible representation of the given potion
     * The potion object contains:
     * "id" (string): The id of the potion
     * "name" (string): The name of the potion, in the currently selected language
     * "can_use" (boolean): Whether the potion can currently be used
     * "can_discard" (boolean): Whether the potion can currently be discarded
     * "requires_target" (boolean): Whether the potion must be used with a target
     * @param potion The potion to convert
     * @return A potion object
     */
    private static HashMap<String, Object> convertPotionToJson(AbstractPotion potion) {
        HashMap<String, Object> jsonPotion = new HashMap<>();
        jsonPotion.put("id", potion.ID);
        jsonPotion.put("name", potion.name);
        boolean canUse = potion.canUse();
        boolean canDiscard = potion.canDiscard();
        if (potion instanceof PotionSlot) {
            canDiscard = canUse = false;
        }
        jsonPotion.put("can_use", canUse);
        jsonPotion.put("can_discard", canDiscard);
        jsonPotion.put("requires_target", potion.isThrown);
        return jsonPotion;
    }

    /**
     * Creates a GSON-compatible representation of the given orb
     * The orb object contains:
     * "id" (string): The id of the orb
     * "name" (string): The name of the orb, in the currently selected language
     * "evoke_amount" (int): The evoke amount of the orb
     * "passive_amount" (int): The passive amount of the orb
     * @param orb The orb to convert
     * @return An orb object
     */
    private static HashMap<String, Object> convertOrbToJson(AbstractOrb orb) {
        HashMap<String, Object> jsonOrb =  new HashMap<>();
        jsonOrb.put("id", orb.ID);
        jsonOrb.put("name", orb.name);
        jsonOrb.put("evoke_amount", orb.evokeAmount);
        jsonOrb.put("passive_amount", orb.passiveAmount);
        return jsonOrb;
    }

}
