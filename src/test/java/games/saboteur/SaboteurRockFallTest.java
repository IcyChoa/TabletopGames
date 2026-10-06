package games.saboteur;

import core.Game;
import core.actions.AbstractAction;
import core.components.Deck;
import core.components.PartialObservableGridBoard;
import games.GameType;
import games.saboteur.actions.PlacePathCard;
import games.saboteur.actions.PlayRockFallCard;
import games.saboteur.components.ActionCard;
import games.saboteur.components.PathCard;
import games.saboteur.components.RoleCard;
import games.saboteur.components.SaboteurCard;
import org.junit.Test;
import utilities.ActionTreeNode;
import utilities.Vector2D;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Rockfall legality is the same for miners and saboteurs: any tunnel or dead-end,
 * never the start card or a goal card. The action tree stays one leaf per cell.
 */
public class SaboteurRockFallTest {

    private static final boolean[] STRAIGHT = {false, false, true, true};

    private record Table(
            SaboteurForwardModel fm,
            SaboteurGameState state,
            int pathX,
            int pathY,
            int beyondX,
            int beyondY,
            int edgeX,
            int edgeY,
            int miner,
            int saboteur
    ) {
    }

    private static Table layTunnel() {
        SaboteurForwardModel fm = new SaboteurForwardModel();
        SaboteurGameParameters params = new SaboteurGameParameters();
        params.setRandomSeed(1L);
        // 5 players: 2 saboteurs + 4 miners, one card undealt, so both roles are dealt.
        Game game = new Game(GameType.Saboteur, fm, new SaboteurGameState(params, 5));
        SaboteurGameState state = (SaboteurGameState) game.getGameState();
        PartialObservableGridBoard grid = state.getGridBoard();
        int sx = state.startingSquare.getX();
        int sy = state.startingSquare.getY();

        int pathX = sx + 1;
        int pathY = sy;
        int beyondX = sx + 2;
        int beyondY = sy;
        int edgeX = sx;
        int edgeY = sy - 1;
        grid.setElement(pathX, pathY, new PathCard(PathCard.PathCardType.Path, STRAIGHT.clone()));
        grid.setElement(beyondX, beyondY, new PathCard(PathCard.PathCardType.Path, STRAIGHT.clone()));
        grid.setElement(edgeX, edgeY, new PathCard(PathCard.PathCardType.Edge, new boolean[]{false, true, false, false}));

        int miner = -1;
        int saboteur = -1;
        for (int i = 0; i < state.getNPlayers(); i++) {
            if (state.getRole(i) == RoleCard.RoleCardType.Saboteur) {
                saboteur = i;
            } else {
                miner = i;
            }
        }
        assertTrue("expected both roles to be dealt", miner >= 0 && saboteur >= 0);
        return new Table(fm, state, pathX, pathY, beyondX, beyondY, edgeX, edgeY, miner, saboteur);
    }

    private static void giveRockFall(SaboteurGameState state, int player) {
        Deck<SaboteurCard> hand = state.getPlayerDecks().get(player);
        hand.clear();
        hand.add(new ActionCard(ActionCard.ActionCardType.RockFall, new ActionCard.ToolCardType[]{}));
        state.setTurnOwner(player);
    }

    @Test
    public void minerAndSaboteurCanRockfallTunnelAndDeadEndButNotStartOrGoal() {
        Table t = layTunnel();
        PartialObservableGridBoard grid = t.state.getGridBoard();
        int gridId = grid.getComponentID();
        int width = grid.getWidth();
        int height = grid.getHeight();
        SaboteurGameParameters params = (SaboteurGameParameters) t.state.getGameParameters();
        int hand = params.cardsPerPlayer[t.state.getNPlayers()];
        int nPlace = hand * width * height * 2;
        int nTool = hand * t.state.getNPlayers() * 3;
        int rockOff = nPlace + nTool + params.nGoals;
        int expectedLeaves = rockOff + width * height + hand + 1;

        for (int player : new int[]{t.miner, t.saboteur}) {
            giveRockFall(t.state, player);
            List<AbstractAction> actions = t.fm.computeAvailableActions(t.state);
            String role = t.state.getRole(player).name();
            assertTrue(role + " can rockfall a tunnel",
                    actions.contains(new PlayRockFallCard(gridId, t.pathX, t.pathY)));
            assertTrue(role + " can rockfall a dead-end",
                    actions.contains(new PlayRockFallCard(gridId, t.edgeX, t.edgeY)));

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    PathCard card = (PathCard) grid.getElement(x, y);
                    if (card == null) {
                        continue;
                    }
                    if (card.type == PathCard.PathCardType.Start || card.type == PathCard.PathCardType.Goal) {
                        assertFalse(role + " must not rockfall " + card.type + " at " + x + "," + y,
                                actions.contains(new PlayRockFallCard(gridId, x, y)));
                    }
                }
            }

            ActionTreeNode root = t.fm.updateActionTree(t.fm.initActionTree(t.state), t.state);
            List<ActionTreeNode> leaves = root.getLeafNodes();
            assertEquals("rockfall mask must not change the action-tree shape", expectedLeaves, leaves.size());
            int pathLeaf = rockOff + t.pathX * height + t.pathY;
            int startLeaf = rockOff + t.state.startingSquare.getX() * height + t.state.startingSquare.getY();
            assertTrue(leaves.get(pathLeaf).getAction() instanceof PlayRockFallCard);
            assertNull(leaves.get(startLeaf).getAction());
        }
    }

    @Test
    public void removingATunnelReopensThatCellAndDisconnectsTheFarSide() {
        Table t = layTunnel();
        giveRockFall(t.state, t.miner);
        int gridId = t.state.getGridBoard().getComponentID();
        t.fm.next(t.state, new PlayRockFallCard(gridId, t.pathX, t.pathY));

        PartialObservableGridBoard grid = t.state.getGridBoard();
        assertNull(grid.getElement(t.pathX, t.pathY));
        assertNotNull("cards past the gap stay on the board", grid.getElement(t.beyondX, t.beyondY));
        assertTrue(t.state.getPathCardOptions().contains(new Vector2D(t.pathX, t.pathY)));
        assertFalse(t.state.getPathCardOptions().contains(new Vector2D(t.beyondX + 1, t.beyondY)));

        int actor = t.state.getCurrentPlayer();
        Deck<SaboteurCard> hand = t.state.getPlayerDecks().get(actor);
        hand.clear();
        hand.add(new PathCard(PathCard.PathCardType.Path, STRAIGHT.clone()));
        List<AbstractAction> actions = t.fm.computeAvailableActions(t.state);
        assertTrue("the removed cell is placeable again from the start",
                actions.stream().anyMatch(a -> a instanceof PlacePathCard p
                        && p.getX() == t.pathX && p.getY() == t.pathY));
        assertFalse("a fragment cut off from the start cannot be extended",
                actions.stream().anyMatch(a -> a instanceof PlacePathCard p
                        && p.getX() == t.beyondX + 1 && p.getY() == t.beyondY));
    }

    @Test
    public void rockfallKeepsRoleSecretAndDiscardsBothCards() {
        for (boolean asSaboteur : new boolean[]{false, true}) {
            Table t = layTunnel();
            int actor = asSaboteur ? t.saboteur : t.miner;
            giveRockFall(t.state, actor);
            int discardBefore = t.state.getDiscardDeck().getSize();
            int gridId = t.state.getGridBoard().getComponentID();
            t.fm.next(t.state, new PlayRockFallCard(gridId, t.pathX, t.pathY));

            assertEquals("rockfall and the removed path card both go to the discard",
                    discardBefore + 2, t.state.getDiscardDeck().getSize());
            for (int p = 0; p < t.state.getNPlayers(); p++) {
                boolean visible = t.state.getRoleDeck().getVisibilityForPlayer(actor, p);
                if (p == actor) {
                    assertTrue("a player still sees their own role", visible);
                } else {
                    assertFalse("rockfall must not reveal the actor to player " + p, visible);
                }
            }
        }
    }
}
