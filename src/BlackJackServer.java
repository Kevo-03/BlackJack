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

public class BlackJackServer extends JFrame
{
    private Card[] deck = new Card[52];
    private List <Card> dealerHand = new ArrayList <Card>();
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
    private boolean start = false; 
    private boolean isGameOver = false;
    private boolean isRoundOver = false;
    
    public BlackJackServer()
    {
        super("Black-Jack-Server");

        runGame = Executors.newFixedThreadPool(2);

        gameLock = new ReentrantLock();
        gameStarted = gameLock.newCondition();
        otherPlayerTurn = gameLock.newCondition();

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
        add(outputArea,BorderLayout.CENTER);
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
        Card cardToAdd = dealerHand.get(deckIndex);
        dealerHand.add(cardToAdd);
        deckIndex--;
        return cardToAdd;
    }

    public void setStartGame(boolean status)
    {
        start = status;
    }

    private class Player implements Runnable
    {
        private Socket connection;
        private Scanner input;
        private Formatter output;
        private int playerNumber;
        private String mark;
        private List <Card> playerHand = new ArrayList<Card>();

        public Player(Socket socket, int number)
        {
            playerNumber = number;
            mark = MARKS[playerNumber];
            connection = socket;

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
                    dealCards(playerNumber);
                    dealSecondCards(playerNumber);
                    while(!isRoundOver)
                    {

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

        public void dealCards(int playerNumber)
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
            otherPlayerTurn.signal(); 
        }

        public void dealSecondCards(int playerNumber)
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
            otherPlayerTurn.signal(); 
        }


        public Card addCardToHand()
        {
            Card cardToAdd = dealerHand.get(deckIndex);
            playerHand.add(cardToAdd);
            deckIndex--;
            return cardToAdd;
        }
    }
}
