// WhackAMoleServer.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WhackAMoleServer extends JFrame {
    private static final int PORT = 12345;
    private static final int GAME_DURATION = 60; // seconds
    private static final int MOLE_APPEAR_INTERVAL = 2; // seconds
    
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Integer> playerScores;
    private boolean gameRunning;
    private int currentMoleX, currentMoleY;
    private long moleAppearTime;
    private ScheduledExecutorService gameScheduler;
    
    // GUI Components
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel playersLabel;
    private JButton startButton;
    private JButton stopButton;
    
    public WhackAMoleServer() {
        clients = new ConcurrentHashMap<>();
        playerScores = new ConcurrentHashMap<>();
        gameScheduler = Executors.newScheduledThreadPool(2);
        
        initializeGUI();
        startServer();
    }
    
    private void initializeGUI() {
        setTitle("Whack a Mole - Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Top panel
        JPanel topPanel = new JPanel(new GridLayout(3, 1));
        statusLabel = new JLabel("Server Status: Running on port " + PORT);
        playersLabel = new JLabel("Connected Players: 0");
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        startButton = new JButton("Start Game");
        stopButton = new JButton("Stop Game");
        stopButton.setEnabled(false);
        
        startButton.addActionListener(e -> startGame());
        stopButton.addActionListener(e -> stopGame());
        
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        
        topPanel.add(statusLabel);
        topPanel.add(playersLabel);
        topPanel.add(buttonPanel);
        
        // Log area
        logArea = new JTextArea(20, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        
        pack();
        setLocationRelativeTo(null);
        
        logMessage("Server initialized successfully");
    }
    
    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            logMessage("Server started on port " + PORT);
            
            // Accept clients in background thread
            new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(() -> handleNewClient(clientSocket)).start();
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            logMessage("Error accepting client: " + e.getMessage());
                        }
                    }
                }
            }).start();
            
        } catch (IOException e) {
            logMessage("Failed to start server: " + e.getMessage());
        }
    }
    
    private void handleNewClient(Socket clientSocket) {
        String playerName = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            
            // Get player name
            playerName = in.readLine();
            
            if (playerName == null) {
                clientSocket.close();
                return;
            }
            
            if (clients.containsKey(playerName)) {
                out.println("NAME_TAKEN");
                clientSocket.close();
                return;
            }
            
            ClientHandler clientHandler = new ClientHandler(clientSocket, in, out, playerName);
            clients.put(playerName, clientHandler);
            playerScores.put(playerName, 0);
            
            out.println("CONNECTED");
            logMessage("Player connected: " + playerName);
            updatePlayersLabel();
            
            // Handle client messages
            String message;
            while ((message = in.readLine()) != null) {
                handleClientMessage(playerName, message);
            }
            
        } catch (IOException e) {
            // Connection lost - client disconnected unexpectedly
            if (playerName != null) {
                logMessage("Client connection lost: " + playerName + " - " + e.getMessage());
            } else {
                logMessage("Client connection error before name received: " + e.getMessage());
            }
        } finally {
            // Clean up disconnected client
            if (playerName != null) {
                cleanupClient(playerName);
            }
        }
    }
    
    private void handleClientMessage(String playerName, String message) {
        if (message.startsWith("HIT:")) {
            String[] parts = message.split(":");
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            long hitTime = Long.parseLong(parts[3]);
            
            // Check if hit is valid (within time window and correct position)
            if (gameRunning && Math.abs(hitTime - moleAppearTime) < 3000 && 
                x == currentMoleX && y == currentMoleY) {
                
                int currentScore = playerScores.get(playerName);
                playerScores.put(playerName, currentScore + 10);
                
                logMessage(playerName + " scored! New score: " + (currentScore + 10));
                broadcastScores();
            }
        } else if (message.equals("DISCONNECT")) {
            // Client is disconnecting gracefully
            logMessage("Player disconnecting gracefully: " + playerName);
            cleanupClient(playerName);
        }
    }
    
    private void cleanupClient(String playerName) {
        ClientHandler client = clients.remove(playerName);
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                // Ignore error when closing already broken connection
            }
        }
        playerScores.remove(playerName);
        logMessage("Player removed from server: " + playerName);
        updatePlayersLabel();
        
        // If game is running and no players left, stop the game
        if (gameRunning && clients.isEmpty()) {
            logMessage("No players left, stopping game");
            stopGame();
        }
    }
    
    private void startGame() {
        if (clients.size() < 1) {
            JOptionPane.showMessageDialog(this, "Need at least 1 player to start!");
            return;
        }
        
        gameRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        
        // Reset scores
        playerScores.replaceAll((k, v) -> 0);
        
        logMessage("Game started with " + clients.size() + " players");
        broadcastMessage("GAME_START:" + GAME_DURATION);
        broadcastScores();
        
        // Schedule mole appearances
        gameScheduler.scheduleAtFixedRate(this::spawnMole, 1, MOLE_APPEAR_INTERVAL, TimeUnit.SECONDS);
        
        // Schedule game end
        gameScheduler.schedule(this::endGame, GAME_DURATION, TimeUnit.SECONDS);
    }
    
    private void spawnMole() {
        if (!gameRunning) return;
        
        currentMoleX = (int) (Math.random() * 3);
        currentMoleY = (int) (Math.random() * 3);
        moleAppearTime = System.currentTimeMillis();
        
        broadcastMessage("MOLE_SPAWN:" + currentMoleX + ":" + currentMoleY);
        logMessage("Mole spawned at (" + currentMoleX + ", " + currentMoleY + ")");
    }
    
    private void endGame() {
        gameRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        
        // Find winner
        String winner = playerScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("No one");
        
        int winningScore = playerScores.getOrDefault(winner, 0);
        
        logMessage("Game ended. Winner: " + winner + " with score: " + winningScore);
        broadcastMessage("GAME_END:" + winner + ":" + winningScore);
    }
    
    private void stopGame() {
        gameRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        
        broadcastMessage("GAME_STOPPED");
        logMessage("Game stopped by server");
    }
    
    private void broadcastMessage(String message) {
        // Create a copy of the clients map to avoid concurrent modification
        Map<String, ClientHandler> clientsCopy = new HashMap<>(clients);
        
        for (Map.Entry<String, ClientHandler> entry : clientsCopy.entrySet()) {
            try {
                entry.getValue().sendMessage(message);
            } catch (Exception e) {
                // If sending fails, the client is probably disconnected
                logMessage("Failed to send message to " + entry.getKey() + ", removing client");
                cleanupClient(entry.getKey());
            }
        }
    }
    
    private void broadcastScores() {
        StringBuilder scoreMsg = new StringBuilder("SCORES");
        for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
            scoreMsg.append(":").append(entry.getKey()).append(",").append(entry.getValue());
        }
        broadcastMessage(scoreMsg.toString());
    }
    
    private void updatePlayersLabel() {
        SwingUtilities.invokeLater(() -> 
            playersLabel.setText("Connected Players: " + clients.size()));
    }
    
    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new Date().toString();
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    private static class ClientHandler {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String playerName;
        
        public ClientHandler(Socket socket, BufferedReader in, PrintWriter out, String playerName) {
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.playerName = playerName;
        }
        
        public void sendMessage(String message) throws IOException {
            if (socket.isClosed() || !socket.isConnected()) {
                throw new IOException("Socket is closed or not connected");
            }
            out.println(message);
            if (out.checkError()) {
                throw new IOException("Error writing to output stream");
            }
        }
        
        public void close() throws IOException {
            if (socket != null && !socket.isClosed()) {
                socket.close();
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
            new WhackAMoleServer().setVisible(true);
        });
    }
}