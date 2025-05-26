package whack.a.mole.game.main;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

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

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connected = false;

    public WhackAMoleClient() {
        initializeGUI();
        connectToServer();
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

    private void connectToServer() {
        String name = JOptionPane.showInputDialog(this, "Enter your name:", "Player Name",
                JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            System.exit(0);
        }
        playerName = name.trim();

        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(playerName);

            String response = in.readLine();
            if ("NAME_TAKEN".equals(response)) {
                JOptionPane.showMessageDialog(this, "Name already taken!", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            } else if ("CONNECTED".equals(response)) {
                connected = true;
                statusLabel.setText("Connected as: " + playerName);
                setTitle("Whack a Mole - " + playerName);

                new Thread(this::listenToServer).start();
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void listenToServer() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                // TODO: Handle server messages (Maharani)
                System.out.println("Server message: " + message);
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Disconnected from server");
                });
            }
        }
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