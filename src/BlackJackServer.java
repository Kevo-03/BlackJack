import java.awt.BorderLayout;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.JScrollPane;
import java.util.HashMap;
import java.util.Map;

public class BlackJackServer extends JFrame
{
    private Card[] deck = new Card[52];
    private List <Card> dealerHand = new ArrayList <Card>();
    private int dealerScore = 0;
    private int deckIndex;
    private JTextArea outputArea;
    private ServerSocket server;
    private int playerToDeal;
    private Player[] players;
    private final static String[] MARKS = {"X", "O"}; 
    private ExecutorService runGame;
    private final static int PLAYER_X = 0;
    private final static int PLAYER_O = 1;
    private Lock gameLock;
    private Condition gameStarted;
    private Condition otherPlayerTurn;
    private Condition roundCondition;
    private Condition canStart;
    private boolean start = false; 
    private boolean isGameOver = false;
    private boolean dealerScoreCalculatedFlag = false;
    private Map <Face,Integer> faceValues = new HashMap <Face,Integer>();
    {
        faceValues.put(Face.Ace,1);
        faceValues.put(Face.Deuce, 2);
        faceValues.put(Face.Three, 3);
        faceValues.put(Face.Four, 4);
        faceValues.put(Face.Five, 5);
        faceValues.put(Face.Six, 6);
        faceValues.put(Face.Seven, 7);
        faceValues.put(Face.Eight, 8);
        faceValues.put(Face.Nine, 9);
        faceValues.put(Face.Ten, 10);
        faceValues.put(Face.King, 10);
        faceValues.put(Face.Queen, 10);
        faceValues.put(Face.Jack, 10);
    }
  
    public BlackJackServer()
    {
        super("Black-Jack-Server");

        runGame = Executors.newFixedThreadPool(2);

        gameLock = new ReentrantLock();
        gameStarted = gameLock.newCondition();
        otherPlayerTurn = gameLock.newCondition();
        roundCondition = gameLock.newCondition();
        canStart = gameLock.newCondition();
        int count = 0;

        for(Suit suit : Suit.values())
        {
            for(Face face : Face.values())
            {
                deck[count] = new Card(face,suit);
                ++count;
            }
        }

        shuffleDeck();
        deckIndex = 51;

        players = new Player[2];
        playerToDeal = PLAYER_X;

        try
        {
            server = new ServerSocket(12345,2);
        }
        catch(IOException ioException)
        {
            ioException.printStackTrace();
            System.exit(1);
        }

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        add(new JScrollPane(outputArea),BorderLayout.CENTER);
        outputArea.setText("server awaiting connectiıns\n");
        setSize(400,400);
        setVisible(true);
    }

    public void execute()
    {
        for(int i = 0; i < players.length; i++)
        {
            try
            {
                players[i] = new Player(server.accept(), i);
                runGame.execute(players[i]);
            }
            catch(IOException ioException)
            {
                ioException.printStackTrace();
                System.exit(1);
            } 
        }

        gameLock.lock();
        setStartGame(true);
        gameStarted.signalAll();
        gameLock.unlock();
    }

    private void displayMessage(final String message)
    {
        SwingUtilities.invokeLater(
            new Runnable() 
            {
                public void run()
                {
                    outputArea.append(message);
                }   
            }
        );
    }

    public void shuffleDeck()
    {
        List <Card> list = Arrays.asList(deck);
        Collections.shuffle(list);
        deck = list.toArray(new Card[list.size()]);
    }

    
    public Card addCardToDealerHand()
    {
        Card cardToAdd = deck[deckIndex];
        dealerHand.add(cardToAdd);
        deckIndex--;
        return cardToAdd;
    }

    public void setStartGame(boolean status)
    {
        start = status;
    }

    public void finishGame()
    {
        isGameOver = true;
    }

    private class Player implements Runnable
    {
        private Socket connection;
        private Scanner input;
        private Formatter output;
        private int playerNumber;
        private int playerScore;
        private String mark;
        private List <Card> playerHand = new ArrayList<Card>();
        private boolean finishRound = false;
        private boolean canStartRound = true;

        public Player(Socket socket, int number)
        {
            playerNumber = number;
            mark = MARKS[playerNumber];
            connection = socket;
            playerScore = 0;

            try
            {
                input = new Scanner(connection.getInputStream());
                output = new Formatter(connection.getOutputStream());
            }
            catch(IOException ioException)
            {
                ioException.printStackTrace();
                System.exit(1);
            }
        }

        @Override
        public void run()
        {
            try
            {
                displayMessage("Player" + mark + " connected\n");
                output.format("%s\n",mark);
                output.flush();

                output.format("Waiting for game to start\n");
                output.flush(); 

                gameLock.lock();

                try
                {
                    while(!start)
                    {
                        gameStarted.await();
                    }
                }
                catch(InterruptedException interruptedException)
                {
                    interruptedException.printStackTrace();
                }
                finally
                {
                    gameLock.unlock();
                }

                displayMessage("Game Starting\n");
                output.format("Game starting\n");
                output.flush();

                while(!isGameOver)
                {
                    displayMessage("Reamining cards : " + deckIndex + "\n");
                    //output.format("debugging, reached the beginning\n");
                    //output.flush();
                    gameLock.lock();
                    try
                    {
                        while(!players[0].canStartRound || !players[1].canStartRound)
                        {
                            //output.format("debugging, inside the while loop, will be waiting\n");
                            //output.flush();
                            canStart.await();
                        }
                    }
                    catch(InterruptedException exception)
                    {
                        exception.printStackTrace();
                    }
                    finally
                    {
                        gameLock.unlock();
                    }
                    //output.format("debugging, will be dealing cards\nPlayer to deal: %d\n", playerToDeal);
                    //output.flush();
                    gameLock.lock();
                    try
                    {
                        if(deckIndex <= 15)
                        {
                            shuffleDeck();
                            deckIndex = 51;
                        }
                    }
                    finally
                    {
                        gameLock.unlock();
                    }
                    dealCards();
                    dealAdditionalCards();
                    canStartRound = false;
                    finishRound = false;
                    gameLock.lock();
                    try
                    {
                        if(dealerScoreCalculatedFlag)
                        {
                            dealerScoreCalculatedFlag = false;
                            dealerScore = 0;
                        }
                    }
                    finally
                    {
                        gameLock.unlock();
                    }
                    while(true)
                    {
                        if(input.hasNextLine())
                        {
                            String command = input.nextLine();
                            if(command.equals("DRAW"))
                                drawCard();
                            else if(command.equals("DONE"))
                            {
                                //output.format("debugging done signal 1\n");
                                //output.flush();
                                gameLock.lock();
                                try
                                {
                                    while(playerToDeal != playerNumber)
                                    {
                                        otherPlayerTurn.await();
                                    }
                                    finishRoundPlayer();
                                    roundCondition.signal();
                                    //output.format("debugging player to handle change\n");
                                    //output.flush();
                                    playerToDeal = (playerNumber + 1) % 2; 
                                    otherPlayerTurn.signal();
                                }
                                catch(InterruptedException exception)
                                {
                                    exception.printStackTrace();
                                }
                                finally
                                {
                                    gameLock.unlock();
                                }
                                break;
                            }
                            else if(command.equals("FINISH"))
                            {
                                gameLock.lock();
                                try
                                {
                                    //output.format("debugging finish signal Player %d finishing the game\n",playerNumber);
                                    //output.flush();
                                    if(!isGameOver)
                                        finishGame();
                                }
                                finally
                                {
                                    gameLock.unlock();
                                }
                                break;
                            }
                        }
                    }
                    if(isGameOver)
                    {
                        //output.format("debugging finish signal Player %d \n",playerNumber);
                        //output.flush();
                        break;
                    }
                    gameLock.lock();
                    try
                    {
                        while(!players[0].isRoundFinished() || !players[1].isRoundFinished())
                        {
                            //output.format("debugging. Player %d waiting for other player\n", playerNumber);
                            //output.flush();
                            roundCondition.await();
                        }
                    }
                    catch(InterruptedException exception)
                    {
                        exception.printStackTrace();
                    }
                    finally
                    {
                        gameLock.unlock();
                    }   
                    //output.format("Outside of the round loop\n");
                    //output.flush();
                    gameLock.lock();
                    try 
                    {
                        //output.format("debugging dealer calculation\nPlayer %d inside try body\n", playerNumber);
                        //output.flush();
                        if(!dealerScoreCalculatedFlag)
                        {
                            //output.format("debugging dealer calculation\nPlayer %d inside if\n", playerNumber);
                            //output.flush();
                            int aces = 0;
                            Card dealerFirst = dealerHand.get(0);
                            displayMessage("Showing dealer's hand\n " + dealerFirst.toString() + "\n");
                            for (Player player : players) 
                            {
                                player.output.format("Showing dealer's hand\n");
                                player.output.flush();
                            }
                            for (Card card : dealerHand) 
                            {
                                for (Player player : players) 
                                {
                                    player.output.format("%s\n", card);
                                    player.output.flush();
                                }
                                dealerScore += faceValues.get(card.geFace());
                                if((card.geFace() == Face.Ace) && (dealerScore + 10) <= 21) 
                                {
                                    aces++;
                                    dealerScore += 10;
                                }
                            }
                            while (dealerScore < 17) 
                            {
                                Card added = addCardToDealerHand();
                                for (Player player : players) 
                                {
                                    player.output.format("%s\n", added);
                                    player.output.flush();
                                }
                                dealerScore += faceValues.get(added.geFace());
                                if((added.geFace() == Face.Ace) && (dealerScore + 10) <= 21) 
                                {
                                    aces++;
                                    dealerScore += 10;
                                }
                                while(aces > 0 && dealerScore > 21)
                                {
                                    aces--;
                                    dealerScore -= 10;
                                }
                            }
                            for (Player player : players) 
                            {
                                player.output.format("Dealer's score : %d\n", dealerScore);
                                player.output.flush();
                            }
                            dealerScoreCalculatedFlag = true;
                        }
                    } 
                    finally 
                    {
                        gameLock.unlock();
                    }
                    //output.format("debugging dealer calculation\nPlayer %d outside of  try body\n", playerNumber);
                    //output.flush();
                    calculateScore();
                    if(playerScore == 21)
                    {
                        output.format("Black Jack! You Won!\n");
                        output.flush();
                    }
                    else if(playerScore > 21)
                    {
                        output.format("21 Exceeded. You Lost!\n");
                        output.flush();
                    }
                    else if(dealerScore > 21 && playerScore < 21)
                    {
                        output.format("Dealer Exceeded 21. You Won!\n");
                        output.flush();
                    }
                    else if(dealerScore == 21)
                    {
                        output.format("Dealer Hit Black Jack! You Lost!\n");
                        output.flush();
                    }
                    else if((dealerScore < 21) && (playerScore > dealerScore))
                    {
                        output.format("You Exceeded Dealer's Hand. You Won!\n");
                        output.flush();
                    }
                    else if((dealerScore < 21) && (playerScore < dealerScore))
                    {
                        output.format("Dealer Exceeded Your Hand. You Lost!\n");
                        output.flush();
                    }
                    if(deckIndex < 0)
                    {
                        isGameOver = true;
                        output.format("Deck is empty, game over\n");
                        output.flush();
                    }
                    else
                    {
                        dealerHand.clear();
                        playerHand.clear();
                        playerScore = 0;
                        output.format("Round is finished, starting another round\n");
                        output.flush();
                        gameLock.lock();
                        try
                        {
                            canStartRound = true;
                            canStart.signal();
                            roundCondition.signal();
                        }
                        finally
                        {
                            gameLock.unlock();
                        }
                    }
                    //output.format("debugging, reached the end\n");
                    //output.flush();
                }
            }
            finally
            {
                try
                {
                    //output.format("debugging finish signal \n");
                    //output.flush();
                    displayMessage("Player " + playerNumber + " finished");
                    connection.close();
                }
                catch(IOException ioException)
                {
                    ioException.printStackTrace();
                    System.exit(1);
                }
            }
        }

        public void dealCards()
        {
            gameLock.lock();

            try
            {
                while(playerToDeal != playerNumber)
                {
                    otherPlayerTurn.await();
                }
                if(playerNumber == PLAYER_X)
                {
                    addCardToDealerHand();
                    displayMessage("Dealer started with first card\n");
                    for(Player player : players)
                    {
                        player.output.format("Dealer started with first card\n");
                        player.output.flush();
                    }
                }
                Card cardAdded = players[playerNumber].addCardToHand();
                displayMessage("Player " + mark + " dealt with " + cardAdded + "\n");
                output.format("You got %s\n",cardAdded);
                output.flush();
                if(playerNumber == PLAYER_O)
                {
                    Card cardToDealer = addCardToDealerHand();
                    displayMessage("Dealer drawn " + cardToDealer + "\n");
                    for(Player player : players)
                    {
                        player.output.format("Dealer drawn %s\n", cardToDealer);
                        player.output.flush();
                    }
                }
                playerToDeal = (playerNumber + 1) % 2; 
                otherPlayerTurn.signal();
            }
            catch(InterruptedException interruptedException)
            {
                interruptedException.printStackTrace();
            }
            finally
            {
                gameLock.unlock();
            }
        }

        public void dealAdditionalCards()
        {
            gameLock.lock(); 
            try 
            {
                while (playerToDeal != playerNumber) 
                {
                    otherPlayerTurn.await(); 
                }

                Card cardAdded = addCardToHand(); 
                displayMessage("Player " + mark + " dealt with " + cardAdded + "\n");
                output.format("You got %s\n", cardAdded);
                output.flush();
                
                playerToDeal = (playerNumber + 1) % 2; 
                
                otherPlayerTurn.signal(); 
            } 
            catch (InterruptedException interruptedException) 
            {
                interruptedException.printStackTrace();
            } 
            finally 
            {
                gameLock.unlock(); 
            }
        }

        public void drawCard()
        {
            gameLock.lock();
            try
            {
                while (playerToDeal != playerNumber) 
                {
                    otherPlayerTurn.await(); 
                }

                Card cardAdded = addCardToHand(); 
                displayMessage("Player " + mark + " dealt with " + cardAdded + "\n");
                output.format("You got %s\n", cardAdded);
                output.flush();
            }
            catch(InterruptedException interruptedException)
            {
                interruptedException.printStackTrace();
            }
            finally
            {
                gameLock.unlock();
            }
        }

        public Card addCardToHand()
        {
            Card cardToAdd = deck[deckIndex];
            playerHand.add(cardToAdd);
            deckIndex--;
            return cardToAdd;
        }

        public int calculateScore()
        {
            int aces = 0;
            for(Card card : playerHand)
            {
                int value = faceValues.get(card.geFace());
                if(card.geFace() == Face.Ace)
                    aces++;
                playerScore += value;
            }
            while (aces > 0 && playerScore + 10 <= 21) 
            {
                playerScore += 10;
                aces--;
            }
            output.format("Your score : %d\n", playerScore);
            output.flush();
            return playerScore;
        }

        public void finishRoundPlayer()
        {
            finishRound =true;
        }

        public boolean isRoundFinished()
        {
            return finishRound;
        }
    }
}
