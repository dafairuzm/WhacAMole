package whack.a.mole.game.main;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

public class WhackAMoleClient extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    
    // GUI Components
    private JButton[][] gameButtons;
    private JLabel statusLabel;
    private JLabel scoreLabel;
    private JLabel timeLabel;
    private JTextArea scoresArea;
    private JPanel gamePanel;
    
    // Game state variables
    private int playerScore = 0;
    private int timeRemaining = 0;
    private boolean gameActive = false;
    private String playerName;
    
    public WhackAMoleClient() {
        initializeGUI();
        // TODO: Connect to server (will be implemented by Bob)
    }
    
    private void initializeGUI() {
        setTitle("Whack a Mole - Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        
        // Top panel with status info
        JPanel topPanel = new JPanel(new GridLayout(3, 1));
        statusLabel = new JLabel("Ready to connect...", JLabel.CENTER);
        scoreLabel = new JLabel("Your Score: 0", JLabel.CENTER);
        timeLabel = new JLabel("Time: --", JLabel.CENTER);
        
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        scoreLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        timeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        
        topPanel.add(statusLabel);
        topPanel.add(scoreLabel);
        topPanel.add(timeLabel);
        
        // Game panel (3x3 grid)
        gamePanel = new JPanel(new GridLayout(3, 3, 5, 5));
        gamePanel.setBorder(BorderFactory.createTitledBorder("Game Board"));
        gamePanel.setPreferredSize(new Dimension(400, 400));
        
        gameButtons = new JButton[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                gameButtons[i][j] = new JButton();
                gameButtons[i][j].setPreferredSize(new Dimension(120, 120));
                gameButtons[i][j].setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                gameButtons[i][j].setBackground(Color.GREEN);
                gameButtons[i][j].setFocusPainted(false);
                gameButtons[i][j].setEnabled(false);
                
                gamePanel.add(gameButtons[i][j]);
            }
        }
        
        // Scores panel
        scoresArea = new JTextArea(8, 20);
        scoresArea.setEditable(false);
        scoresArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scoresScroll = new JScrollPane(scoresArea);
        scoresScroll.setBorder(BorderFactory.createTitledBorder("Player Scores"));
        
        add(topPanel, BorderLayout.NORTH);
        add(gamePanel, BorderLayout.CENTER);
        add(scoresScroll, BorderLayout.EAST);
        
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Use default look and feel
            }
            new WhackAMoleClient().setVisible(true);
        });
    }
}