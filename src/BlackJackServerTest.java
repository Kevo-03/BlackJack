
import javax.swing.JFrame;


public class BlackJackServerTest 
{
    public static void main(String[] args) 
    {
        BlackJackServer dealer = new BlackJackServer();
        dealer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        dealer.execute();    
    }
}
