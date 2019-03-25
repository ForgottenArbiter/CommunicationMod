package communicationmod;

import com.megacrit.cardcrawl.actions.AbstractGameAction;

public class EndOfTurnAction extends AbstractGameAction {
    public void update() {
        GameStateConverter.signalTurnEnd();
        this.isDone = true;
    }
}
