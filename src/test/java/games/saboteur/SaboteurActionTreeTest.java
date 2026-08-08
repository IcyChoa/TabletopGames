package games.saboteur;

import core.Game;
import core.actions.AbstractAction;
import games.GameType;
import org.junit.Test;
import utilities.ActionTreeNode;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Sanity checks for the PyTAG action tree: for many random playouts, across the min/max player
 * counts, the set of leaves marked "valid" in the tree must always exactly match the action list
 * produced by the regular (list-based) forward model.
 */
public class SaboteurActionTreeTest {

    @Test
    public void actionTreeMatchesAvailableActionsAcrossRandomPlayouts() {
        int[] playerCounts = {3, 4, 6, 10};
        int seedsPerCount = 5;
        int maxStepsPerGame = 2000;

        for (int nPlayers : playerCounts) {
            for (int seed = 0; seed < seedsPerCount; seed++) {
                SaboteurForwardModel fm = new SaboteurForwardModel();
                SaboteurGameParameters params = new SaboteurGameParameters();
                params.setRandomSeed(seed * 991L + nPlayers);
                Game game = new Game(GameType.Saboteur, fm, new SaboteurGameState(params, nPlayers));
                SaboteurGameState state = (SaboteurGameState) game.getGameState();

                Random rnd = new Random(seed * 37L + nPlayers);
                ActionTreeNode root = fm.initActionTree(state);

                int steps = 0;
                while (state.isNotTerminal() && steps < maxStepsPerGame) {
                    root = fm.updateActionTree(root, state);
                    List<ActionTreeNode> validLeaves = root.getValidLeaves();

                    int expected = fm.computeAvailableActions(state).size();
                    String context = "nPlayers=" + nPlayers + " seed=" + seed + " step=" + steps;
                    assertEquals(context + " : valid leaf count should match computeAvailableActions size",
                            expected, validLeaves.size());
                    assertTrue(context + " : there should always be at least one legal move", validLeaves.size() > 0);

                    ActionTreeNode chosen = validLeaves.get(rnd.nextInt(validLeaves.size()));
                    AbstractAction action = chosen.getAction();
                    assertNotNull(context + " : chosen leaf must carry an action", action);

                    fm.next(state, action);
                    steps++;
                }
                assertTrue("nPlayers=" + nPlayers + " seed=" + seed + " : game should have progressed",
                        steps > 0);
            }
        }
    }
}
