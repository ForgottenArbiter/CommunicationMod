package communicationmod;

import com.google.gson.Gson;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

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
    public static boolean waitingForCommand = false;

    /**
     * Used to indicate that something has been done that will change the game state,
     * and hasStateChanged() should indicate a state change when the state next becomes stable
     */
    public static void registerStateChange() {
        externalChange = true;
    }

    /**
     * Prevents hasStateChanged() from indicating a state change until resumeStateUpdate() is called.
     */
    public static void blockStateUpdate() {
        blocked = true;
    }

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
    public static boolean hasStateChanged() {
        if(blocked) {
            return false;
        }
        AbstractDungeon.CurrentScreen newScreen = AbstractDungeon.screen;
        boolean newScreenUp = AbstractDungeon.isScreenUp;
        AbstractRoom.RoomPhase newPhase = AbstractDungeon.getCurrRoom().phase;
        boolean inCombat = (newPhase == AbstractRoom.RoomPhase.COMBAT);
        // We are never ready to receive commands when it is not our turn.
        if(inCombat && (!myTurn || AbstractDungeon.getMonsters().areMonstersBasicallyDead() )) {
            return false;
        }
        // In event rooms, we need to wait for the event wait timer to reach 0 before we can accurately assess its state.
        if((AbstractDungeon.getCurrRoom() instanceof EventRoom || AbstractDungeon.getCurrRoom() instanceof NeowRoom) && AbstractDungeon.getCurrRoom().event.waitTimer != 0.0F) {
            return false;
        }
        // The state has always changed in some way when one of these variables is different. However, the state may not be finished changing.
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
        return false;
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
     * - "choose_choices": If the command is available, the possible choices for the choose command.
     * @return A JSON representation of the game state
     */
    public static String getGameStateGson() {
        externalChange = false;
        HashMap<String, Object> state = new HashMap<>();

        previousPhase = AbstractDungeon.getCurrRoom().phase;
        previousScreen = AbstractDungeon.screen;
        previousScreenUp = AbstractDungeon.isScreenUp;
        previousGold = AbstractDungeon.player.gold;

        state.put("screen", AbstractDungeon.screen.name());
        state.put("is_screen_up", previousScreenUp);
        state.put("room_phase", AbstractDungeon.getCurrRoom().phase.toString());
        state.put("action_phase", AbstractDungeon.actionManager.phase.toString());
        if(AbstractDungeon.actionManager.currentAction != null) {
            state.put("current_action", AbstractDungeon.actionManager.currentAction.getClass().getName());
        }
        state.put("room_type", splitFinalClassName(AbstractDungeon.getCurrRoom().getClass().getName()));
        state.put("current_hp", AbstractDungeon.player.currentHealth);
        state.put("max_hp", AbstractDungeon.player.maxHealth);
        state.put("floor", AbstractDungeon.floorNum);
        state.put("act", AbstractDungeon.actNum);
        state.put("gold", AbstractDungeon.player.gold);

        ArrayList<Object> relics = new ArrayList<>();
        for(AbstractRelic relic : AbstractDungeon.player.relics) {
            relics.add(relic.relicId);
        }

        state.put("relics", relics);

        ArrayList<Object> deck = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.masterDeck.group) {
            deck.add(convertCardToJson(card));
        }

        state.put("deck", deck);

        ArrayList<Object> potions = new ArrayList<>();
        for(AbstractPotion potion : AbstractDungeon.player.potions) {
            potions.add(potion.ID);
        }

        state.put("potions", potions);

        state.put("map", convertMapToJson());
        state.put("room_state", getRoomState());
        state.put("screen_choices", getScreenChoices());

        Gson gson = new Gson();
        return gson.toJson(state);
    }

    /**
     * Returns just the class name (com.megacrit.cardcrawl.dungeons.AbstractDungeon -> AbstractDungeon).
     * Probably not what this stuff is actually called, but not going to look up precise terminology.
     * @param className Full class name
     * @return Short class name
     */
    private static String splitFinalClassName (String className) {
        String[] parts = className.split("\\.");
        return parts[parts.length - 1];
    }

    private static HashMap<String, Object> getRoomState() {
        switch(AbstractDungeon.getCurrRoom().phase) {
            case EVENT:
                return getEventState();
            case COMBAT:
                return getCombatState();
            default:
                return new HashMap<>();
        }
    }

    private static ArrayList<String> getScreenChoices() {
        if (!AbstractDungeon.isScreenUp && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.EVENT) {
            return ChoiceScreenUtils.getEventScreenChoices();
        } else if (!AbstractDungeon.isScreenUp && AbstractDungeon.getCurrRoom() instanceof RestRoom) {
            return ChoiceScreenUtils.getRestRoomChoices();
        }
        switch(AbstractDungeon.screen) {
            case MAP:
                return ChoiceScreenUtils.getMapScreenChoices();
            case GRID:
                return ChoiceScreenUtils.getGridScreenChoices();
            default:
                return new ArrayList<>();
        }
    }

    private static HashMap<String, Object> getEventState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> options = new ArrayList<>();
        for(LargeDialogOptionButton button : AbstractDungeon.getCurrRoom().event.imageEventText.optionList) {
            HashMap<String, Object> json_button = new HashMap<>();
            json_button.put("text", button.msg);
            json_button.put("disabled", button.isDisabled);
            options.add(json_button);
        }
        state.put("name", splitFinalClassName(AbstractDungeon.getCurrRoom().event.getClass().getName()));
        state.put("options", options);
        return state;
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
                    ArrayList<Object> json_parents = new ArrayList<>();
                    ArrayList<MapRoomNode> parents = node.getParents();
                    for(MapRoomNode parent : parents) {
                        ArrayList<Object> parent_coordinates = new ArrayList<>();
                        parent_coordinates.add(parent.y);
                        parent_coordinates.add(parent.x);
                        json_parents.add(parent_coordinates);
                    }
                    json_node.put("parents", json_parents);
                    json_map.add(json_node);
                }
            }
        }
        return json_map;
    }

    private static HashMap<String, Object> convertMapRoomNodeToJson(MapRoomNode node) {
        HashMap<String, Object> json_node = new HashMap<>();
        ArrayList<Object> node_coordinates = new ArrayList<>();
        node_coordinates.add(node.y);
        node_coordinates.add(node.x);
        json_node.put("symbol", node.getRoomSymbol(true));
        json_node.put("coordinates", node_coordinates);
        return json_node;
    }

    private static HashMap<String, Object> convertCardToJson(AbstractCard card) {
        HashMap<String, Object> json_card = new HashMap<>();
        json_card.put("name", card.name);
        json_card.put("id", card.uuid.toString());
        if(card.misc != 0) {
            json_card.put("misc", card.misc);
        }
        json_card.put("cost", card.costForTurn);
        return json_card;
    }

    private static HashMap<String, Object> convertMonsterToJson(AbstractMonster monster) {
        HashMap<String, Object> json_monster = new HashMap<>();
        json_monster.put("name", monster.id);
        json_monster.put("current_hp", monster.currentHealth);
        json_monster.put("max_hp", monster.maxHealth);
        json_monster.put("intent", monster.intent.name());
        json_monster.put("half_dead", monster.halfDead);
        json_monster.put("block", monster.currentBlock);
        json_monster.put("powers", convertCreaturePowersToJson(monster));
        return json_monster;
    }

    private static HashMap<String, Object> convertPlayerToJson(AbstractPlayer player) {
        HashMap<String, Object> json_player = new HashMap<>();
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

}
