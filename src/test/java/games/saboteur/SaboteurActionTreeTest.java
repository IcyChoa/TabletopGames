package games.saboteur;

import core.Game;
import core.actions.AbstractAction;
import games.GameType;
import games.saboteur.actions.PlacePathCard;
import games.saboteur.actions.PlayRockFallCard;
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

    /**
     * Place/rockfall leaf IDs must decode as (x, y) using the nested-loop formula.
     * Fails if getLeafNodes() is BFS: shallow pass/map/doNothing leaves occupy the
     * place block, so the spatial policy would treat the wrong grid cells as legal.
     */
    @Test
    public void placeAndRockfallLeafIdsMatchGridCoordinates() {
        int placeChecked = 0;
        int rockfallChecked = 0;
        for (int seed = 0; seed < 40 && placeChecked == 0; seed++) {
            SaboteurForwardModel fm = new SaboteurForwardModel();
            SaboteurGameParameters params = new SaboteurGameParameters();
            params.setRandomSeed(seed * 97L + 1);
            Game game = new Game(GameType.Saboteur, fm, new SaboteurGameState(params, 3));
            SaboteurGameState state = (SaboteurGameState) game.getGameState();
            ActionTreeNode root = fm.updateActionTree(fm.initActionTree(state), state);

            int width = state.getGridBoard().getWidth();
            int height = state.getGridBoard().getHeight();
            int handSize = params.cardsPerPlayer[state.getNPlayers()];
            int nPlace = handSize * width * height * 2;
            int nTool = handSize * state.getNPlayers() * 3;
            int nMap = params.nGoals;
            int rockfallOff = nPlace + nTool + nMap;

            List<ActionTreeNode> leaves = root.getLeafNodes();
            assertEquals("r0", leaves.get(0).getName());
            assertEquals("doNothing", leaves.get(leaves.size() - 1).getName());

            for (int i = 0; i < leaves.size(); i++) {
                AbstractAction action = leaves.get(i).getAction();
                if (action instanceof PlacePathCard a) {
                    assertTrue("place leaf idx=" + i + " should be < " + nPlace, i < nPlace);
                    int rest = i % (width * height * 2);
                    int x = rest / (height * 2);
                    int y = (rest % (height * 2)) / 2;
                    int rot = rest % 2;
                    assertEquals("place x at leaf " + i, a.getX(), x);
                    assertEquals("place y at leaf " + i, a.getY(), y);
                    assertEquals("place rot at leaf " + i, a.isRotated() ? 1 : 0, rot);
                    placeChecked++;
                } else if (action instanceof PlayRockFallCard a) {
                    int local = i - rockfallOff;
                    int x = local / height;
                    int y = local % height;
                    assertEquals("rockfall x at leaf " + i, a.getX(), x);
                    assertEquals("rockfall y at leaf " + i, a.getY(), y);
                    rockfallChecked++;
                }
            }
        }
        assertTrue(
                "some opening hand should have a placeable path card (rockfallLeaves=" + rockfallChecked + ")",
                placeChecked > 0);
    }
}
