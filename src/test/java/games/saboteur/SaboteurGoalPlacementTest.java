package games.saboteur;

import core.Game;
import core.components.PartialObservableGridBoard;
import games.GameType;
import games.saboteur.components.PathCard;
import org.junit.Test;
import utilities.Vector2D;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Gold must be shuffled onto the board, not pinned by construction order.
 * A previous bug placed goals first and shuffled the deck afterwards, so
 * Deck.add-prepend left gold on the bottom cell every episode.
 */
public class SaboteurGoalPlacementTest {

    private static Vector2D goldCell(SaboteurGameState state) {
        PartialObservableGridBoard grid = state.getGridBoard();
        Vector2D found = null;
        int golds = 0;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                PathCard card = (PathCard) grid.getElement(x, y);
                if (card == null || card.type != PathCard.PathCardType.Goal) {
                    continue;
                }
                if (card.hasTreasure()) {
                    golds++;
                    found = new Vector2D(x, y);
                }
            }
        }
        assertEquals("exactly one gold goal", 1, golds);
        return found;
    }

    @Test
    public void goldIsNotPinnedToOneCellAcrossSeeds() {
        Set<String> cells = new HashSet<>();
        for (int seed = 0; seed < 24; seed++) {
            SaboteurForwardModel fm = new SaboteurForwardModel();
            SaboteurGameParameters params = new SaboteurGameParameters();
            params.setRandomSeed(seed * 97L + 1);
            Game game = new Game(GameType.Saboteur, fm, new SaboteurGameState(params, 3));
            SaboteurGameState state = (SaboteurGameState) game.getGameState();
            Vector2D gold = goldCell(state);
            assertNotNull(gold);
            cells.add(gold.getX() + "," + gold.getY());
        }
        assertTrue(
                "gold should occupy more than one cell across seeds, got " + cells,
                cells.size() >= 2);
    }
}
