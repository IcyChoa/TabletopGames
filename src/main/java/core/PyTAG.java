package core;

import core.actions.AbstractAction;
import core.actions.ActionSpace;
import core.actions.DoNothing;
import core.interfaces.IStateFeatureVector;
import core.interfaces.ITreeActionSpace;
import core.interfaces.IStateFeatureJSON;
import evaluation.optimisation.TunableParameters;
import games.GameType;
import games.diamant.DiamantFeatures;
import games.loveletter.features.LLStateFeaturesReduced;
import games.powergrid.PowerGridFeatures;
import games.powergrid.PowerGridGameState;
import games.powergrid.PowerGridParameters;
import games.powergrid.components.PowerGridCard;
import games.stratego.StrategoFeatures;
import games.saboteur.SaboteurFeatures;
import games.saboteur.SaboteurGameParameters;
import games.saboteur.SaboteurGameState;
import games.saboteur.components.RoleCard;
import games.sushigo.SGFeatures;
import games.tictactoe.TTTFeatures;
import org.json.simple.JSONObject;
import players.human.HumanGUIPlayer;
import players.python.PythonAgent;
import players.simple.RandomPlayer;
import utilities.ActionTreeNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

enum FeatureExtractors {
    /* Every game implementing the RL interfaces should be registered here, PyTAG uses this to reference the correct features extractors and action spaces
    * In case that an interface is not implemented they can be set to null*/
  //  ExplodingKittens( ExplodingKittensFeatures.class, null),
    LoveLetter(LLStateFeaturesReduced.class, null),
    Stratego(StrategoFeatures.class, null),
    SushiGo(null, SGFeatures.class),
    TicTacToe(TTTFeatures.class, TTTFeatures.class),
    Diamant(DiamantFeatures.class, DiamantFeatures.class),
	PowerGrid(PowerGridFeatures.class, null), //gets both the JSON and Vector observation
    Saboteur(SaboteurFeatures.class, null);
    Class<? extends IStateFeatureVector> stateFeatureVector;
    Class<? extends IStateFeatureJSON> stateFeatureJSON;
    FeatureExtractors(Class<? extends IStateFeatureVector> stateFeatureVector, Class<? extends IStateFeatureJSON> stateFeatureJSON) {
        this.stateFeatureVector = stateFeatureVector;
        this.stateFeatureJSON = stateFeatureJSON;
    }

    @Override
    public String toString() {
        return "name:" + this.name() + " vector:" + (this.stateFeatureVector != null) + " json:" + (this.stateFeatureJSON != null);
    }

    public IStateFeatureVector getStateFeatureVector() {
        if (stateFeatureVector == null) return null;
        try {
            return stateFeatureVector.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public IStateFeatureJSON getStateFeatureJSON() {
        if (stateFeatureJSON == null) return null;
        try {
            return stateFeatureJSON.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}

public class PyTAG {
    private Game game;
    // root of the action tree
    private ActionTreeNode root;
    // list of leaf nodes
    private List<ActionTreeNode> leaves;
    private AbstractGameState gameState;
    private AbstractForwardModel forwardModel;
    private IStateFeatureVector stateVectoriser;
    private IStateFeatureJSON stateJSONiser;
    private List<AbstractPlayer> players;
    private int turnPause = 0;
    private int tick;
    private int lastPlayer; // used to track actions per 'turn'
    private List<AbstractAction> availableActions;

    boolean isNormalized; // Bool for whether you want observations to be normalized

    private Random seedRandom; // Random used for setting the seed for each episode
    private long lastSeed;

    public static String getSupportedGames(){
        /* returns the supported games with the corresponding feature extractors */
        String supportedGames = "";
        for (FeatureExtractors fe : FeatureExtractors.values()) {
            supportedGames += fe.toString() + "\n";
//            System.out.println(fe);
        }
        return supportedGames;
    }

    public static String getSupportedGamesJSON(){
        /* returns the supported games with the corresponding feature extractors */
        JSONObject json = new JSONObject();
        String supportedGames = "";
        for (FeatureExtractors fe : FeatureExtractors.values()) {
            JSONObject features = new JSONObject();
            features.put("vector", fe.stateFeatureVector != null);
            features.put("json", fe.stateFeatureJSON != null);
            json.put(fe.name(), features);
        }
        return json.toJSONString();
    }


    public PyTAG(GameType gameToPlay, String parameterConfigFile, List<AbstractPlayer> players, long seed, boolean isNormalized) throws Exception {

        // boolean randomizeParameters, List<IGameListener> listeners
        this.seedRandom = new Random(seed);
        this.isNormalized = isNormalized;
        this.players = players;
        this.stateVectoriser = FeatureExtractors.valueOf(gameToPlay.name()).getStateFeatureVector();
        this.stateJSONiser = FeatureExtractors.valueOf(gameToPlay.name()).getStateFeatureJSON();

        // Creating game instance (null if not implemented)
        if (parameterConfigFile != null) {
            AbstractParameters params = AbstractParameters.createFromFile(gameToPlay, parameterConfigFile);
            this.game = gameToPlay.createGameInstance(players.size(), seed, params);
        } else game = gameToPlay.createGameInstance(players.size(), seed);

        assert game != null;

        // RL training uses one-round team episodes by default for Saboteur.
        // This goes through the tunable map rather than the field directly: any later
        // setParameterValue call triggers _reset(), which reloads every field from that
        // map and would otherwise clear the flag.
        if (gameToPlay == GameType.Saboteur) {
            SaboteurGameParameters sgp = (SaboteurGameParameters) game.getGameState().getGameParameters();
            sgp.setParameterValue("singleRoundEpisode", true);
        }

        if (this.stateVectoriser == null && this.stateJSONiser == null){
            throw new Exception("Game does not implement the state feature vector or JSON interface");
        }

//        if (game != null) {
//            if (listeners != null)
//                listeners.forEach(game::addListener);
//
//            // Randomize parameters
//            if (randomizeParameters) {
//                AbstractParameters gameParameters = game.getGameState().getGameParameters();
//                gameParameters.randomize();
//            }
//
//        }

    }

    /**
     * Set an integer game parameter. Must be called before reset(), since most
     * parameters are only read while the board is being built.
     *
     * For the Saboteur curriculum, goalSpacingX moves the goals closer to the start and
     * horizontalPadding compensates so the grid keeps its size: the width is
     * goalSpacingX + 2 + 2 * horizontalPadding, so the observation vector and action
     * tree stay the same shape across difficulty levels.
     */
    public void setGameParameter(String name, int value) {
        AbstractParameters params = game.getGameState().getGameParameters();
        if (!(params instanceof TunableParameters)) {
            throw new IllegalStateException("Game parameters are not tunable: " + params.getClass().getName());
        }
        ((TunableParameters<?>) params).setParameterValue(name, value);
    }

    // --Wrappers for interface functions--

    // Gets observations in JSON
    public String getObservationJson() throws Exception {
        AbstractGameState gs = gameState.copy(gameState.getCurrentPlayer());
        if (stateJSONiser != null){
            return stateJSONiser.getObservationJson(gs, gs.getCurrentPlayer());
        }
        else throw new Exception("JSON feature extractor is not implemented");
    }

    // Gets the observation space as an integer
    public int getObservationSpace() {
        if (stateVectoriser != null){
            return stateVectoriser.names().length;
        }
        return 0; // dummy value
//        else throw new Exception("Function is not implemented");

    }

    public double[] getObservationVector() throws Exception {
        AbstractGameState gs = gameState.copy(gameState.getCurrentPlayer());
        if (stateVectoriser != null){
            return stateVectoriser.doubleVector(gs, gs.getCurrentPlayer());
        }
        else throw new Exception("Observation vectoriser function is not implemented");
    }

    // Gets the action space size as an integer
    public int getActionSpace(){
        return leaves.size();
    }

    // Gets the actions as an integer array
    public int[] getFixedActionSpace() {
        return new int[this.leaves.size()];

    }

    // Gets the action mask as a boolean array
    public int[] getActionMask() {
        return leaves.stream()
                .mapToInt(ActionTreeNode::getValue)
                .toArray();
    }

    // gets the whole action tree as an array (tree can be reconstructed using the getTreeShape() function)
    public int[] getActionTree() {
        return root.getActionMask();
    }

    // gets the action tree shape as a list of arrays
    public List getTreeShape(){
        return this.root.getTreeShape();
    }

    // Plays an action given an actionID
    public void executeAction(int actionID) throws Exception {
        if (forwardModel instanceof ITreeActionSpace) {
            ActionTreeNode node = leaves.get(actionID);
            AbstractAction action = node.getAction();
            forwardModel.next(gameState, action);
        }
        else throw new Exception("Function is not implemented");
    }

    // --End of Wrapper Functions--

    public void reset(){
        // Reset game instance, run built-in agents until a python agent is required to make a decision
        this.game.reset(players);
        this.turnPause = 0;
        this.tick = 0;
        this.game.setTurnPause(turnPause);
        this.gameState = game.getGameState();
        this.lastSeed = seedRandom.nextLong();
        gameState.gameParameters.setRandomSeed(this.lastSeed);
        this.forwardModel = game.getForwardModel();
        this.availableActions = forwardModel.computeAvailableActions(gameState);

        
        // execute the game if needed until Python agent is required to make a decision
        boolean isTerminal = nextDecision();
        

        // get action tree for current player
        if (this.root == null){
            this.root = ((ITreeActionSpace)this.forwardModel).initActionTree(this.gameState);
        }
        // update with initial actions
        // Compute the updated available actions and the action tree
        AbstractGameState observation = gameState.copy(gameState.getCurrentPlayer());
        this.availableActions = forwardModel.computeAvailableActions(observation);
        this.root = ((ITreeActionSpace)this.forwardModel).updateActionTree(this.root, this.gameState);
        this.leaves = root.getLeafNodes();
    }


   

    public int getPlayerID(){
        return gameState.getCurrentPlayer();
    }

    public boolean isDone(){
        return !gameState.isNotTerminal();
    }

    public double getReward(){
        return gameState.getGameScore(gameState.getCurrentPlayer());
    }

    public List<AbstractAction> getActions(){
        return availableActions;
    }

    // Executes game loop until RL agent is required to make a decision
    // returns true if game is over
    public boolean nextDecision(){
        int activePlayer = gameState.getCurrentPlayer();
        AbstractPlayer currentPlayer = players.get(activePlayer);
        while ( !(currentPlayer instanceof PythonAgent)){
            AbstractGameState observation = gameState.copy(activePlayer);
            List<core.actions.AbstractAction> observedActions = forwardModel.computeAvailableActions(observation);

            if (isDone()){
                // game is over
                return true;
            }

            // Start the timer for this decision
            gameState.playerTimer[activePlayer].resume();

            // Either ask player which action to use or, in case no actions are available, report the updated observation
            core.actions.AbstractAction action = null;
            if (observedActions.size() > 0) {
                if (observedActions.size() == 1 && (!(currentPlayer instanceof HumanGUIPlayer) || observedActions.get(0) instanceof DoNothing)) {
                    // Can only do 1 action, so do it.
                    action = observedActions.get(0);
                    currentPlayer.registerUpdatedObservation(observation);
                } else {
                    // Get action from player, and time it
                    action = currentPlayer.getAction(observation, observedActions);
                }
            } else {
                currentPlayer.registerUpdatedObservation(observation);
            }

            // End the timer for this decision
            gameState.playerTimer[activePlayer].pause();
            gameState.playerTimer[activePlayer].incrementAction();

            if (gameState.coreGameParameters.verbose && !(action == null)) {
                System.out.println(action);
            }
            if (action == null)
                throw new AssertionError("We have a NULL action in the Game loop");

            // Check player timeout
            forwardModel.next(gameState, action);
            tick++;

            lastPlayer = activePlayer;
            activePlayer = gameState.getCurrentPlayer();
            currentPlayer = players.get(gameState.getCurrentPlayer());
        }
        return false;
    }


    public AbstractGameState step(int actionId) throws Exception{
        // execute action and loop until an RL agent is required to make a decision
        if (isDone()){
            throw new Exception("Need to reset the environment after each finished episode");
        } else if (this.gameState == null){
            throw new Exception("Need to reset the environment before calling step");
        }
        // executes the seleted actions
        executeAction(actionId);
        if (isDone()){
            // check if the game has just ended
            // game is over
            return gameState.copy(gameState.getCurrentPlayer());
        }

        // update game until RL agent is required to make a decision - if game is over in the mean time returns isTerminal
        boolean isTerminal = nextDecision();
        if (isTerminal){
            // game is over
            return gameState.copy(gameState.getCurrentPlayer());
        }

        int activePlayer = gameState.getCurrentPlayer();
        AbstractGameState observation = gameState.copy(activePlayer);

        // Compute the updated available actions and the action tree
        this.availableActions = forwardModel.computeAvailableActions(observation);
        this.root = ((ITreeActionSpace)this.forwardModel).updateActionTree(this.root, this.gameState);
        this.leaves = root.getLeafNodes();

        return observation;
    }

    public int getTick(){
        return this.tick;
    }

    public long getSeed(){
        return this.lastSeed;
    }

    public CoreConstants.GameResult[] getPlayerResults(){
        return this.gameState.getPlayerResults();
    }

    /** Whether the most recently completed Saboteur round was a miner win. */
    public boolean getSaboteurMinersWon() {
        SaboteurGameState sgs = (SaboteurGameState) gameState;
        return sgs.didMinersWinLastRound();
    }

    public boolean getSaboteurRoundResolved() {
        SaboteurGameState sgs = (SaboteurGameState) gameState;
        return sgs.isLastRoundResolved();
    }

    /** True if the given seat is a Saboteur (from the live/full state). */
    public boolean getSaboteurIsSaboteur(int playerId) {
        SaboteurGameState sgs = (SaboteurGameState) gameState;
        return sgs.getRole(playerId) == RoleCard.RoleCardType.Saboteur;
    }

    /**
     * Team reward for a seat after a resolved round: +1 if that seat's team won, -1 otherwise.
     * Returns 0 if the round has not resolved yet.
     */
    public double getSaboteurTeamReward(int playerId) {
        SaboteurGameState sgs = (SaboteurGameState) gameState;
        if (!sgs.isLastRoundResolved()) {
            return 0.0;
        }
        boolean isSaboteur = sgs.getRole(playerId) == RoleCard.RoleCardType.Saboteur;
        boolean minersWon = sgs.didMinersWinLastRound();
        if (isSaboteur) {
            return minersWon ? -1.0 : 1.0;
        }
        return minersWon ? 1.0 : -1.0;
    }

    public int sampleRNDAction(int[] mask, Random rnd){
        /* take the action mask and sample a valid random action */
        int[] trueIdx = IntStream.range(0, mask.length)
                .filter(i -> mask[i] == 1)
                .toArray();
        int rndIdx = trueIdx[rnd.nextInt(trueIdx.length)];
        return rndIdx;
    }

    public static void main(String[] args) {
        long seed = 2466;
        Random rnd = new Random(seed);
        ArrayList<AbstractPlayer> players = new ArrayList<>();
        String availableGames = PyTAG.getSupportedGamesJSON();

        // set up players
//        players.add(new MCTSPlayer());
        players.add(new PythonAgent());
        players.add(new RandomPlayer(rnd));
//        players.add(new PythonAgent());

        boolean usePyTAG = true;

        int wins = 0;
        int episodes = 0;

        boolean done = false;
        int MAX_EPISODES = 100;
        int steps = 0;
        String obsType = "json";

        try {
            // Initialise the game
            PyTAG env = new PyTAG(GameType.valueOf("TicTacToe"), null, players, 343, true);
            if (!usePyTAG) env.game.getCoreParameters().actionSpace = new ActionSpace(ActionSpace.Structure.Default);

            // reset is always required before starting a new episode
            env.reset();
            while (!done){

                if (usePyTAG){

                    // get action mask and sample random action
                    int randomAction = env.sampleRNDAction(env.getActionMask(), rnd);

                    // get observation vector
                    if (obsType.equals("vector")){
                        double[] obs = env.getObservationVector();
                    } else if (obsType.equals("json")){
                        String json = env.getObservationJson();
                    }
//                    double[] obs = env.getObservationVector();
//                    String json = env.getObservationJson();
                    double reward = env.getReward();
//                    System.out.println("at step " + steps + " the reward is " + reward + "player ID " + env.gameState.getCurrentPlayer());

                    // step the environment
                    env.step(randomAction);
                } else {
                    // this is the normal game loop
                    List<AbstractAction> actions = env.forwardModel.computeAvailableActions(env.gameState);

                    double reward = env.getReward();
//                    System.out.println("at step " + steps + " the reward is " + reward + "player ID " + env.gameState.getCurrentPlayer());

                    int randomAction = rnd.nextInt(actions.size());
                    env.step(randomAction);
//                    env.forwardModel.next(env.gameState, actions.get(randomAction));

                }

                // update stats
                steps += 1;
                done = env.isDone();
                if (done){
                    episodes += 1;
                    System.out.println("episodes " + episodes + " is done in " + steps + " ; outcome:  " + env.getPlayerResults()[0].value);
                    if (env.getPlayerResults()[0] == CoreConstants.GameResult.WIN_GAME)wins += 1;
                    if (episodes == MAX_EPISODES)break;
                    env.reset();
                    done = false;
                    steps = 0;
                }
            }
        } catch (Exception e){
            System.out.println("Exception during game initialisation" + e);
        }
        System.out.println("Run finished won " + wins + " out of " + episodes);


    }  
 
}
