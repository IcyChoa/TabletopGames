package games.saboteur;

import core.Game;
import core.actions.AbstractAction;
import core.components.Deck;
import games.GameType;
import games.saboteur.actions.PlayToolCard;
import games.saboteur.components.ActionCard;
import games.saboteur.components.SaboteurCard;
import org.junit.Test;
import utilities.ActionTreeNode;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Legal-mask rules for tool cards. Action-tree shape is unchanged (targetPlayer 0..N-1
 * including self); only availability of self-target leaves changes.
 */
public class SaboteurToolActionsTest {

    private static final ActionCard.ToolCardType PICKAXE = ActionCard.ToolCardType.Pickaxe;

    private record Fixture(SaboteurForwardModel fm, SaboteurGameState state) {
    }

    private static Fixture newGame() {
        SaboteurForwardModel fm = new SaboteurForwardModel();
        SaboteurGameParameters params = new SaboteurGameParameters();
        params.setRandomSeed(1L);
        Game game = new Game(GameType.Saboteur, fm, new SaboteurGameState(params, 4));
        return new Fixture(fm, (SaboteurGameState) game.getGameState());
    }

    private static void replaceHand(SaboteurGameState state, ActionCard card) {
        Deck<SaboteurCard> hand = state.getPlayerDecks().get(state.getCurrentPlayer());
        hand.clear();
        hand.add(card);
    }

    @Test
    public void fixToolsCanTargetActingPlayerWhenThatToolIsBroken() {
        Fixture f = newGame();
        int actor = f.state.getCurrentPlayer();
        f.state.setToolFunctional(actor, PICKAXE, false);
        replaceHand(f.state, new ActionCard(
                ActionCard.ActionCardType.FixTools, new ActionCard.ToolCardType[]{PICKAXE}));

        List<AbstractAction> actions = f.fm.computeAvailableActions(f.state);
        PlayToolCard selfFix = new PlayToolCard(0, actor, PICKAXE, true);
        assertTrue("FixTools should be playable on the acting player when that tool is broken",
                actions.contains(selfFix));

        ActionTreeNode root = f.fm.updateActionTree(f.fm.initActionTree(f.state), f.state);
        boolean selfFixLeaf = root.getValidLeaves().stream()
                .map(ActionTreeNode::getAction)
                .anyMatch(selfFix::equals);
        assertTrue("action tree should expose a self-fix leaf (mask only; tree shape unchanged)",
                selfFixLeaf);
    }

    @Test
    public void brokenToolsCannotTargetActingPlayer() {
        Fixture f = newGame();
        int actor = f.state.getCurrentPlayer();
        int other = (actor + 1) % f.state.getNPlayers();
        f.state.setToolFunctional(actor, PICKAXE, true);
        f.state.setToolFunctional(other, PICKAXE, true);
        replaceHand(f.state, new ActionCard(
                ActionCard.ActionCardType.BrokenTools, new ActionCard.ToolCardType[]{PICKAXE}));

        List<AbstractAction> actions = f.fm.computeAvailableActions(f.state);
        PlayToolCard selfBreak = new PlayToolCard(0, actor, PICKAXE, false);
        PlayToolCard otherBreak = new PlayToolCard(0, other, PICKAXE, false);
        assertFalse("BrokenTools must not target the acting player", actions.contains(selfBreak));
        assertTrue("BrokenTools should still be playable on another player",
                actions.contains(otherBreak));
    }
}
