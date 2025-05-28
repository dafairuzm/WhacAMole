// package whack.a.mole.game.main;

// WhackAMoleClient.java
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.*;

public class WhackAMoleClient extends JFrame {
    private static final String SERVER_HOST = "192.168.100.22";
    private static final int SERVER_PORT = 12345;
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    
    // GUI Components
    private JButton[][] gameButtons;
    private JLabel statusLabel;
    private JLabel scoreLabel;
    private JLabel timeLabel;
    private JTextArea scoresArea;
    private JPanel gamePanel;
    
    // Game state
    private int playerScore = 0;
    private int timeRemaining = 0;
    private boolean gameActive = false;
    private boolean canPlay = true; // Whether this player can actively play
    private boolean isExtraTime = false;
    private Timer gameTimer;
    private Timer moleTimer;
    private boolean connected = false;
    
    public WhackAMoleClient() {
        initializeGUI();
        connectToServer();
    }
    
    private void initializeGUI() {
        setTitle("Whack a Mole - Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Add window closing listener to properly disconnect
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });
        
        // Top panel with status info
        JPanel topPanel = new JPanel(new GridLayout(3, 1));
        statusLabel = new JLabel("Connecting to server...", JLabel.CENTER);
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
                final int row = i;
                final int col = j;
                
                gameButtons[i][j] = new JButton();
                gameButtons[i][j].setPreferredSize(new Dimension(120, 120));
                gameButtons[i][j].setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                gameButtons[i][j].setBackground(Color.GREEN);
                gameButtons[i][j].setFocusPainted(false);
                gameButtons[i][j].setEnabled(false);
                
                gameButtons[i][j].addActionListener(e -> hitMole(row, col));
                
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
        // Get player name
        String name = JOptionPane.showInputDialog(this, "Enter your name:", "Player Name", JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            System.exit(0);
        }
        playerName = name.trim();
        
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // Send player name
            out.println(playerName);
            
            // Check connection response
            String response = in.readLine();
            if ("NAME_TAKEN".equals(response)) {
                JOptionPane.showMessageDialog(this, "Name already taken!", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            } else if ("CONNECTED".equals(response)) {
                connected = true;
                statusLabel.setText("Connected as: " + playerName);
                setTitle("Whack a Mole - " + playerName);
                
                // Start listening for server messages
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
                handleServerMessage(message);
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Disconnected from server");
                    JOptionPane.showMessageDialog(this, "Lost connection to server", "Connection Lost", JOptionPane.WARNING_MESSAGE);
                });
            }
        }
    }
    
    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.startsWith("GAME_START:")) {
                int duration = Integer.parseInt(message.split(":")[1]);
                isExtraTime = false;
                canPlay = true; // Everyone can play in normal game
                startGame(duration);
            } else if (message.startsWith("MOLE_SPAWN:")) {
                String[] parts = message.split(":");
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                showMole(x, y);
            } else if (message.startsWith("SCORES:")) {
                updateScores(message);
            } else if (message.startsWith("GAME_END:")) {
                String[] parts = message.split(":");
                String winner = parts[1];
                int winningScore = Integer.parseInt(parts[2]);
                endGame(winner, winningScore);
            } else if (message.equals("GAME_STOPPED")) {
                stopGame();
            } else if (message.startsWith("EXTRA_TIME:")) {
                String[] parts = message.split(":");
                int extraTime = Integer.parseInt(parts[1]);
                String activePlayersStr = parts[2];
                String[] activePlayers = activePlayersStr.split(",");
                
                // Check if this player is in the active players list
                canPlay = false;
                for (String activePlayer : activePlayers) {
                    if (activePlayer.equals(playerName)) {
                        canPlay = true;
                        break;
                    }
                }
                
                isExtraTime = true;
                
                if (canPlay) {
                    JOptionPane.showMessageDialog(this, 
                        "🔥 SERI! Anda masuk Extra Time!\nHanya pemain dengan skor tertinggi yang bisa bermain!", 
                        "Extra Time", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "⏱ Extra Time dimulai!\nAnda hanya bisa menonton karena skor Anda tidak tertinggi.", 
                        "Watching Mode", JOptionPane.INFORMATION_MESSAGE);
                }
                
                startGame(extraTime);
            }
        });
    }
    
    private void startGame(int duration) {
        gameActive = true;
        timeRemaining = duration;
        
        if (isExtraTime) {
            if (canPlay) {
                statusLabel.setText("🔥 EXTRA TIME - Anda bisa bermain!");
                statusLabel.setForeground(Color.RED);
            } else {
                statusLabel.setText("👀 EXTRA TIME - Mode Menonton");
                statusLabel.setForeground(Color.BLUE);
            }
        } else {
            statusLabel.setText("Game in progress!");
            statusLabel.setForeground(Color.BLACK);
        }
        
        timeLabel.setText("Time: " + timeRemaining);
        
        // Enable/disable buttons based on whether player can play
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                gameButtons[i][j].setEnabled(canPlay && gameActive);
                gameButtons[i][j].setText("");
                
                if (canPlay) {
                    gameButtons[i][j].setBackground(Color.GREEN);
                } else {
                    // Gray out buttons for spectators
                    gameButtons[i][j].setBackground(Color.LIGHT_GRAY);
                }
            }
        }
        
        // Update game panel border to show status
        if (isExtraTime) {
            if (canPlay) {
                gamePanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(Color.RED, 2), 
                    "EXTRA TIME - Anda Bermain!"));
            } else {
                gamePanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(Color.BLUE, 2), 
                    "EXTRA TIME - Mode Menonton"));
            }
        } else {
            gamePanel.setBorder(BorderFactory.createTitledBorder("Game Board"));
        }
        
        // Start countdown timer
        if (gameTimer != null) {
            gameTimer.cancel();
        }
        gameTimer = new Timer();
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    timeRemaining--;
                    timeLabel.setText("Time: " + timeRemaining);
                    if (timeRemaining <= 0) {
                        gameTimer.cancel();
                    }
                });
            }
        }, 1000, 1000);
    }
    
    private void showMole(int x, int y) {
        if (!gameActive) return;
        
        // Clear all moles first
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                gameButtons[i][j].setText("");
                if (canPlay) {
                    gameButtons[i][j].setBackground(Color.GREEN);
                } else {
                    gameButtons[i][j].setBackground(Color.LIGHT_GRAY);
                }
            }
        }
        
        // Show new mole
        gameButtons[x][y].setText("🐹");
        if (canPlay) {
            gameButtons[x][y].setBackground(Color.YELLOW);
        } else {
            // Show mole but with different color for spectators
            gameButtons[x][y].setBackground(Color.ORANGE);
        }
        
        // Hide mole after 2 seconds
        if (moleTimer != null) {
            moleTimer.cancel();
        }
        moleTimer = new Timer();
        moleTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (gameActive) {
                        gameButtons[x][y].setText("");
                        if (canPlay) {
                            gameButtons[x][y].setBackground(Color.GREEN);
                        } else {
                            gameButtons[x][y].setBackground(Color.LIGHT_GRAY);
                        }
                    }
                });
            }
        }, 2000);
    }
    
    private void hitMole(int x, int y) {
        // Don't allow hitting if player can't play
        if (!gameActive || !canPlay) {
            if (!canPlay && isExtraTime) {
                // Show message to spectator
                JOptionPane.showMessageDialog(this, 
                    "Anda sedang dalam mode menonton!\nHanya pemain dengan skor tertinggi yang bisa bermain di Extra Time.", 
                    "Mode Menonton", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        
        // Send hit to server
        if (connected && out != null) {
            out.println("HIT:" + x + ":" + y + ":" + System.currentTimeMillis());
        }
        
        // Check if there's a mole at this position
        if ("🐹".equals(gameButtons[x][y].getText())) {
            // Hit successful - show explosion effect
            gameButtons[x][y].setText("💥");
            gameButtons[x][y].setBackground(Color.RED);
            
            // Reset button after short delay
            Timer resetTimer = new Timer();
            resetTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        if (gameActive) {
                            gameButtons[x][y].setText("");
                            gameButtons[x][y].setBackground(Color.GREEN);
                        }
                    });
                }
            }, 500);
        } else {
            // Miss - show miss effect
            gameButtons[x][y].setText("❌");
            gameButtons[x][y].setBackground(Color.ORANGE);
            
            // Reset button after short delay
            Timer resetTimer = new Timer();
            resetTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        if (gameActive) {
                            gameButtons[x][y].setText("");
                            gameButtons[x][y].setBackground(Color.GREEN);
                        }
                    });
                }
            }, 300);
        }
    }
    
    private void updateScores(String message) {
        // Parse scores message: SCORES:player1,score1:player2,score2:...
        String[] parts = message.split(":");
        StringBuilder scoresText = new StringBuilder();
        
        if (isExtraTime) {
            scoresText.append("🔥 EXTRA TIME 🔥\n");
            scoresText.append("=".repeat(20)).append("\n");
        } else {
            scoresText.append("LEADERBOARD\n");
            scoresText.append("=".repeat(20)).append("\n");
        }
        
        Map<String, Integer> scores = new HashMap<>();
        
        for (int i = 1; i < parts.length; i++) {
            String[] playerScoreParts = parts[i].split(",");
            if (playerScoreParts.length == 2) {
                String player = playerScoreParts[0];
                int score = Integer.parseInt(playerScoreParts[1]);
                scores.put(player, score);
                
                if (player.equals(playerName)) {
                    playerScore = score;
                    scoreLabel.setText("Your Score: " + score);
                }
            }
        }
        
        // Sort and display scores
        scores.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String name = entry.getKey();
                int score = entry.getValue();
                
                if (name.equals(playerName)) {
                    if (isExtraTime && canPlay) {
                        scoresText.append("🔥 ").append(name).append(": ").append(score).append(" (ACTIVE) 🔥\n");
                    } else if (isExtraTime && !canPlay) {
                        scoresText.append("👀 ").append(name).append(": ").append(score).append(" (WATCHING) 👀\n");
                    } else {
                        scoresText.append("► ").append(name).append(": ").append(score).append(" ◄\n");
                    }
                } else {
                    if (isExtraTime) {
                        // Check if this player is active in extra time by checking if they have recent score updates
                        // This is a simple heuristic - in a real implementation, you might want the server to send this info
                        scoresText.append("  ").append(name).append(": ").append(score).append("\n");
                    } else {
                        scoresText.append("  ").append(name).append(": ").append(score).append("\n");
                    }
                }
            });
        
        scoresArea.setText(scoresText.toString());
    }
    
    private void endGame(String winner, int winningScore) {
        gameActive = false;
        isExtraTime = false;
        canPlay = true;
        
        if (gameTimer != null) gameTimer.cancel();
        if (moleTimer != null) moleTimer.cancel();
        
        // Disable all buttons
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                gameButtons[i][j].setEnabled(false);
                gameButtons[i][j].setText("");
                gameButtons[i][j].setBackground(Color.LIGHT_GRAY);
            }
        }
        
        statusLabel.setText("Game Over!");
        statusLabel.setForeground(Color.BLACK);
        timeLabel.setText("Time: 0");
        gamePanel.setBorder(BorderFactory.createTitledBorder("Game Board"));
        
        String message = winner.equals(playerName) ? 
            "🎉 Congratulations! You won with " + winningScore + " points!" :
            "Game Over! Winner: " + winner + " (" + winningScore + " points)";
            
        JOptionPane.showMessageDialog(this, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void stopGame() {
        gameActive = false;
        isExtraTime = false;
        canPlay = true;
        
        if (gameTimer != null) gameTimer.cancel();
        if (moleTimer != null) moleTimer.cancel();
        
        // Disable all buttons
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                gameButtons[i][j].setEnabled(false);
                gameButtons[i][j].setText("");
                gameButtons[i][j].setBackground(Color.LIGHT_GRAY);
            }
        }
        
        statusLabel.setText("Game stopped by server");
        statusLabel.setForeground(Color.BLACK);
        timeLabel.setText("Time: --");
        gamePanel.setBorder(BorderFactory.createTitledBorder("Game Board"));
    }
    
    private void disconnect() {
        connected = false;
        try {
            if (out != null) {
                out.println("DISCONNECT");
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore errors during disconnect
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