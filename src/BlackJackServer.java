import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InterruptedIOException;
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
    private Condition dealerScoreCalculated;
    private boolean start = false; 
    private boolean isGameOver = false;
    private boolean isRoundOver = false;
    private boolean dealerScoreCalculatedFlag = false;
    private Map <Face,Integer> faceValues = new HashMap <Face,Integer>();
    {
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
        dealerScoreCalculated = gameLock.newCondition();

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
        setSize(300,300);
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

    public void finishRound()
    {
        isRoundOver = true;
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
                output.format("%s",mark);
                output.flush();

                output.format( "%s\n%s", "Player %s connected","Waiting for the game to start\n", mark );
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

                displayMessage("Game Starting");
                output.format("Game starting");
                output.flush();

                while(!isGameOver)
                {
                    dealCards();
                    dealAdditionalCards();
                    while(!isRoundOver)
                    {
                        if(input.hasNextLine())
                        {
                            if(input.nextLine().equals("DRAW"))
                                dealAdditionalCards();
                            else if(input.nextLine().equals("DONE"));
                                finishRoundPlayer();
                        }
                        if(players[0].isRoundFinished() && players[1].isRoundFinished())
                        {
                            finishRound();
                            output.format("Round finished\ncCalculating scores...\n");
                            output.flush();
                        }
                    }
                    if(playerNumber == PLAYER_O)
                    {
                        gameLock.lock();
                        try
                        {
                            while(!dealerScoreCalculatedFlag)
                            {
                                dealerScoreCalculated.await();
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
                    }
                    if(playerNumber == PLAYER_X)
                    {
                        gameLock.lock();
                        try
                            {
                            Card dealerFirst = dealerHand.get(0);
                            displayMessage("Dealer's first card was " + dealerFirst.toString() + "\n");
                            for(Player player : players)
                            {
                                player.output.format("Dealer's first card was %s\n", dealerFirst);
                                player.output.flush();
                            }
                            boolean dealerAceFound = false;
                            int dealerAceCount = 0;
                            for(Card card : dealerHand)
                            {
                                if(card.geFace() == Face.Ace)
                                {
                                    dealerAceCount++;
                                    dealerAceFound = true;
                                }
                                else
                                {
                                    dealerScore += faceValues.get(card.geFace());
                                }
                            }

                            if(dealerAceFound)
                            {
                                    if(dealerAceCount == 1)
                                    {
                                        dealerScore += 11;
                                    }
                                    else if(dealerAceCount == 2)
                                    {
                                        dealerScore = 12;
                                    }
                            }

                            int dealerScoreAceTemp = dealerScore; 

                            while(dealerScore <= 17 || dealerScoreAceTemp <= 17)
                            {
                                Card added = addCardToDealerHand();
                                if(added.geFace() == Face.Ace)
                                {
                                    dealerScore += 11;
                                    dealerScoreAceTemp += 1;
                                }
                            }
                            dealerScoreCalculatedFlag = true;
                            dealerScoreCalculated.signal();
                        }
                        finally
                        {
                            gameLock.unlock();
                        }
                    }
                }
            }
            finally
            {
                try
                {
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
            while(playerToDeal != playerNumber)
            {
                gameLock.lock();

                try
                {
                    otherPlayerTurn.await();
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
            if(playerNumber == PLAYER_X)
            {
                addCardToDealerHand();
                displayMessage("Dealer started with first card");
                for(Player player : players)
                {
                    player.output.format("Dealer started with first card");
                    player.output.flush();
                }
            }
            Card cardAdded = players[playerNumber].addCardToHand();
            displayMessage("Player " + mark + " dealt with " + cardAdded);
            output.format("You got %s",cardAdded);
            output.flush();
            if(playerNumber == PLAYER_O)
            {
                Card cardToDealer = addCardToDealerHand();
                displayMessage("Dealer drawn " + cardToDealer);
                for(Player player : players)
                {
                    player.output.format("Dealer drawn %s", cardToDealer);
                    player.output.flush();
                }
            }
            playerToDeal = (playerNumber + 1) % 2; 

            gameLock.lock();
            try
            {
                otherPlayerTurn.signal();
            }
            finally
            {
                gameLock.unlock();
            }
        }

        public void dealAdditionalCards()
        {
            while(playerToDeal != playerNumber)
            {
                gameLock.lock();

                try
                {
                    otherPlayerTurn.await();
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

            Card cardAdded = players[playerNumber].addCardToHand();
            displayMessage("Player " + mark + " dealt with " + cardAdded);
            output.format("You got %s",cardAdded);
            output.flush();
            
            playerToDeal = (playerNumber + 1) % 2; 
            gameLock.lock();
            try
            {
                otherPlayerTurn.signal();
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
            boolean aceFound = false;
            int aceCount = 0;
            for(Card card : playerHand)
            {
                if(card.geFace() == Face.Ace)
                {
                    aceFound = true;
                    aceCount++;
                }
                else
                {
                    playerScore += faceValues.get(card.geFace());
                }
            }

            if(aceFound)
            {
                if(aceCount == 1)
                {
                    if((playerScore + 11) > 21)
                        playerScore += 1;
                    else
                        playerScore += 11;
                }
                else if(aceCount == 2)
                {
                    if((playerScore + 12) > 21)
                        playerScore += 2;
                    else 
                        playerScore += 12;
                }
                else 
                {
                    playerScore += (aceCount*1);
                }
            }

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
