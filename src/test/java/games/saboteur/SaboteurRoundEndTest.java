package games.saboteur;

import core.AbstractGameState;
import core.Game;
import core.actions.AbstractAction;
import core.actions.DoNothing;
import core.components.Deck;
import games.GameType;
import games.saboteur.components.ActionCard;
import games.saboteur.components.SaboteurCard;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SaboteurRoundEndTest {

    private static SaboteurGameState newGame(boolean singleRound) {
        SaboteurForwardModel fm = new SaboteurForwardModel();
        SaboteurGameParameters params = new SaboteurGameParameters();
        params.setParameterValue("singleRoundEpisode", singleRound);
        params.setRandomSeed(1L);
        Game game = new Game(GameType.Saboteur, fm, new SaboteurGameState(params, 3));
        return (SaboteurGameState) game.getGameState();
    }

    private static SaboteurForwardModel model() {
        return new SaboteurForwardModel();
    }

    private static void emptyHands(SaboteurGameState state) {
        for (Deck<SaboteurCard> hand : state.getPlayerDecks()) {
            hand.clear();
        }
    }

    /** Draw pile empty, cards parked in the discard so a later round can be dealt. */
    private static void parkDrawPileInDiscard(SaboteurGameState state) {
        Deck<SaboteurCard> draw = state.getDrawDeck();
        while (draw.getSize() > 0) {
            state.getDiscardDeck().add(draw.draw());
        }
    }

    private static void setRound(SaboteurGameState state, int round) throws Exception {
        Field field = AbstractGameState.class.getDeclaredField("roundCounter");
        field.setAccessible(true);
        field.setInt(state, round);
    }

    @Test
    public void emptyHandDoesNotEndTheRoundWhileAnotherPlayerStillHasCards() {
        SaboteurForwardModel fm = model();
        SaboteurGameState state = newGame(true);
        assertTrue(((SaboteurGameParameters) state.getGameParameters()).singleRoundEpisode);
        state.getDrawDeck().clear();
        emptyHands(state);
        state.getPlayerDecks().get(1).add(
                new ActionCard(ActionCard.ActionCardType.Map, new ActionCard.ToolCardType[]{}));
        state.setTurnOwner(0);

        List<AbstractAction> actions = fm.computeAvailableActions(state);
        assertEquals(1, actions.size());
        assertTrue(actions.get(0) instanceof DoNothing);
        fm.next(state, actions.get(0));

        assertTrue(state.isNotTerminal());
        assertFalse(state.isLastRoundResolved());
        assertEquals(0, state.getPlayerDecks().get(0).getSize());
        assertEquals(1, state.getPlayerDecks().get(1).getSize());
        assertEquals(1, state.getCurrentPlayer());

        state.getPlayerDecks().get(1).clear();
        actions = fm.computeAvailableActions(state);
        assertTrue(actions.get(0) instanceof DoNothing);
        fm.next(state, actions.get(0));

        assertTrue(state.isLastRoundResolved());
        assertFalse(state.didMinersWinLastRound());
        assertFalse(state.isNotTerminal());
    }

    @Test
    public void saboteurWinOnTheThirdRoundEndsTheMatch() throws Exception {
        SaboteurForwardModel fm = model();
        SaboteurGameState state = newGame(false);
        parkDrawPileInDiscard(state);
        emptyHands(state);
        setRound(state, 2);
        state.setTurnOwner(0);

        fm.next(state, new DoNothing());

        assertTrue(state.isLastRoundResolved());
        assertFalse(state.didMinersWinLastRound());
        assertFalse("third round is the last round", state.isNotTerminal());
        assertEquals(2, state.getRoundCounter());
    }

    @Test
    public void saboteurWinBeforeTheThirdRoundStartsAnotherRound() throws Exception {
        SaboteurForwardModel fm = model();
        SaboteurGameState state = newGame(false);
        parkDrawPileInDiscard(state);
        emptyHands(state);
        setRound(state, 1);
        state.setTurnOwner(0);

        fm.next(state, new DoNothing());

        assertTrue(state.isNotTerminal());
        assertTrue(state.isLastRoundResolved());
        assertFalse(state.didMinersWinLastRound());
        assertEquals(2, state.getRoundCounter());
        assertTrue(state.getPlayerDecks().get(0).getSize() > 0);
    }

    @Test
    public void roleDeckMatchesOfficialPlayerCounts() {
        SaboteurGameParameters params = new SaboteurGameParameters();
        assertArrayEquals(new int[]{0, 0, 0, 1, 1, 2, 2, 3, 3, 3, 4}, params.saboteursForPlayerCount);
        assertArrayEquals(new int[]{0, 0, 0, 3, 4, 4, 5, 5, 6, 7, 7}, params.minersForPlayerCount);
        assertEquals(4, params.saboteursForPlayerCount[3] + params.minersForPlayerCount[3]);
        assertEquals(11, params.saboteursForPlayerCount[10] + params.minersForPlayerCount[10]);
    }
}
