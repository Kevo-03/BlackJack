
import javax.swing.JFrame;


public class BlackJackClientTest 
{
    public static void main(String[] args) 
    {
        BlackJackClient player;
        
        if(args.length == 0)
            player = new BlackJackClient("127.0.0.1");
        else
            player = new BlackJackClient(args[0]);

        player.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
