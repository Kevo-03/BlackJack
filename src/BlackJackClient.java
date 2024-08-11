import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class BlackJackClient extends JFrame implements Runnable
{
    private JButton drawButton;
    private JButton doneButton;
    private JPanel buttonPanel;
    private JTextArea displayArea;
    private JTextField markField;
    private Formatter output;
    private Scanner input;
    private Socket connection;
    private String dealerServer;
    private String mark;

    public BlackJackClient(String dealer)
    {
        dealerServer = dealer;
        displayArea = new JTextArea();
        displayArea.setEditable(false);
        add(new JScrollPane(displayArea),BorderLayout.CENTER);
        markField = new JTextField();
        markField.setEditable(false);
        add(markField,BorderLayout.NORTH);
        drawButton = new JButton("DRAW");
        drawButton.addActionListener(
            new ActionListener() 
            {
                public void actionPerformed(ActionEvent event)
                {
                    output.format("DRAW\n");
                    output.flush();
                    displayMessage("Draw requested\n");
                }    
            }
        );
        doneButton = new JButton("DONE");
        doneButton.addActionListener(
            new ActionListener() 
            {
                public void actionPerformed(ActionEvent event)
                {
                    output.format("DONE\n");
                    output.flush();
                }    
            }
        );
        
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,10,5));
        buttonPanel.add(drawButton);
        buttonPanel.add(doneButton);
        add(buttonPanel,BorderLayout.SOUTH);

        setSize(300,300);
        setVisible(true);
        startPlayer();
    }

    public void startPlayer()
    {
        try
        {
            connection = new Socket(InetAddress.getByName(dealerServer),12345);
            input = new Scanner(connection.getInputStream());
            output = new Formatter(connection.getOutputStream());
        }
        catch(IOException ioException)
        {
            ioException.printStackTrace();
        }

        ExecutorService worker = Executors.newFixedThreadPool(1);
        worker.execute(this);
    }

    @Override
    public void run()
    {
        mark = input.nextLine();

        SwingUtilities.invokeLater(
            new Runnable()
            {
                public void run()
                {
                    markField.setText("You are player \"" + mark + "\"");
                }
            }
        );

        while(true)
        {
            if(input.hasNextLine())
            {
                displayMessage(input.nextLine() + "\n");
            }
        }
    }

    private void displayMessage(final String message)
    {
        SwingUtilities.invokeLater(
            new Runnable() 
            {
                public void run()
                {
                    displayArea.append(message);
                }   
            }
        );
    }
}
