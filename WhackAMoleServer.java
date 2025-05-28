import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

public class WhackAMoleServer extends JFrame {
    private static final int PORT = 12345;
    private static final int GAME_DURATION = 20; // seconds
    private static final int EXTRA_TIME_DURATION = 15; // seconds
    private static final int MIN_MOLE_INTERVAL = 1; // minimum seconds
    private static final int MAX_MOLE_INTERVAL = 3; // maximum seconds
    private static final int MIN_MOLE_INTERVAL_EXTRA = 500; // minimum seconds extra time
    private static final int MAX_MOLE_INTERVAL_EXTRA = 800; // maximum seconds extra time

    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Integer> playerScores;
    private Set<String> activePlayersInExtraTime; // Players who can play in extra time
    private boolean gameRunning;
    private boolean extraTimeActive;
    private int currentMoleX, currentMoleY;
    private long moleAppearTime;
    private ScheduledExecutorService gameScheduler;
    private ScheduledFuture<?> moleSpawnTask;
    private ScheduledFuture<?> gameEndTask;
    private Random random;
    
    // GUI Components
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel playersLabel;
    private JButton startButton;
    private JButton stopButton;
    
    public WhackAMoleServer() {
        clients = new ConcurrentHashMap<>();
        playerScores = new ConcurrentHashMap<>();
        activePlayersInExtraTime = new HashSet<>();
        gameScheduler = Executors.newScheduledThreadPool(3);
        random = new Random();
        
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
            logMessage("Client connection error for " + (playerName != null ? playerName : "unknown") + ": " + e.getMessage());
        } finally {
            // Clean up disconnected client
            if (playerName != null) {
                disconnectClient(playerName);
            }
        }
    }
    
    private void handleClientMessage(String playerName, String message) {
        if (message.startsWith("HIT:")) {
            String[] parts = message.split(":");
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            long hitTime = Long.parseLong(parts[3]);
            
            if (gameRunning) {
                // Check if player can play (either normal game or in extra time)
                if (!extraTimeActive || activePlayersInExtraTime.contains(playerName)) {
                    int currentScore = playerScores.get(playerName);
                    
                    // Check if hit is valid (within time window and correct position)
                    if (Math.abs(hitTime - moleAppearTime) < 3000 && 
                        x == currentMoleX && y == currentMoleY) {
                        
                        // Correct hit - add 10 points
                        playerScores.put(playerName, currentScore + 10);
                        logMessage(playerName + " scored! New score: " + (currentScore + 10));
                        
                    } else {
                        // Wrong hit - subtract 5 points (but don't go below 0)
                        int newScore = Math.max(0, currentScore - 5);
                        playerScores.put(playerName, newScore);
                        logMessage(playerName + " missed! Score reduced to: " + newScore);
                    }
                    
                    broadcastScores();
                } else {
                    logMessage(playerName + " tried to hit but is not active in extra time");
                }
            }
        } else if (message.equals("DISCONNECT")) {
            disconnectClient(playerName);
        }
    }
    
    private void startGame() {
        if (clients.size() < 1) {
            JOptionPane.showMessageDialog(this, "Need at least 1 player to start!");
            return;
        }
        
        gameRunning = true;
        extraTimeActive = false;
        activePlayersInExtraTime.clear();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        
        // Reset scores
        playerScores.replaceAll((k, v) -> 0);
        
        logMessage("Game started with " + clients.size() + " players");
        broadcastMessage("GAME_START:" + GAME_DURATION);
        broadcastScores();
        
        // Schedule first mole appearance with random delay
        scheduleNextMole();
        
        // Schedule game end
        gameEndTask = gameScheduler.schedule(this::endGame, GAME_DURATION, TimeUnit.SECONDS);
    }
    
    private void scheduleNextMole() {
        if (!gameRunning) return;
        
        // Generate random interval between MIN_MOLE_INTERVAL and MAX_MOLE_INTERVAL
        int randomInterval = MIN_MOLE_INTERVAL + random.nextInt(MAX_MOLE_INTERVAL - MIN_MOLE_INTERVAL + 1);
        
        moleSpawnTask = gameScheduler.schedule(() -> {
            spawnMole();
            scheduleNextMole(); // Schedule next mole with new random interval
        }, randomInterval, TimeUnit.SECONDS);
    }
    private void scheduleNextMoleExtraTime() {
        if (!gameRunning) return;
        
        // Generate random interval between MIN_MOLE_INTERVAL and MAX_MOLE_INTERVAL
        int randomInterval = MIN_MOLE_INTERVAL_EXTRA + random.nextInt(MAX_MOLE_INTERVAL_EXTRA - MIN_MOLE_INTERVAL_EXTRA + 1);
        
        moleSpawnTask = gameScheduler.schedule(() -> {
            spawnMole();
            scheduleNextMoleExtraTime(); // Schedule next mole with new random interval
        }, randomInterval, TimeUnit.MILLISECONDS);
    }
    
    private void spawnMole() {
        if (!gameRunning) return;
        
        currentMoleX = random.nextInt(3);
        currentMoleY = random.nextInt(3);
        moleAppearTime = System.currentTimeMillis();
        
        broadcastMessage("MOLE_SPAWN:" + currentMoleX + ":" + currentMoleY);
        logMessage("Mole spawned at (" + currentMoleX + ", " + currentMoleY + ")");
    }
    
    private void endGame() {
        // Cancel any running tasks first
        if (moleSpawnTask != null && !moleSpawnTask.isDone()) {
            moleSpawnTask.cancel(false);
        }
        
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(playerScores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        if (sorted.size() >= 2 && !extraTimeActive) {
            int topScore = sorted.get(0).getValue();
            int secondScore = sorted.get(1).getValue();

            if (topScore == secondScore && topScore > 0) {
                // Find all players with the top score
                List<String> topPlayers = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : sorted) {
                    if (entry.getValue() == topScore) {
                        topPlayers.add(entry.getKey());
                    } else {
                        break;
                    }
                }
                
                if (topPlayers.size() >= 2) {
                    startExtraTime(topPlayers);
                    return;
                }
            }
        }

        // Normal game end
        finishGame(sorted);
    }
    
    private void startExtraTime(List<String> topPlayers) {
        extraTimeActive = true;
        activePlayersInExtraTime.clear();
        activePlayersInExtraTime.addAll(topPlayers);
        
        // Reset scores only for active players
        for (String player : activePlayersInExtraTime) {
            playerScores.put(player, 0);
        }
        
        logMessage("Extra time started with players: " + String.join(", ", topPlayers));
        
        // Broadcast extra time start with active players list
        String activePlayersStr = String.join(",", activePlayersInExtraTime);
        broadcastMessage("EXTRA_TIME:" + EXTRA_TIME_DURATION + ":" + activePlayersStr);
        broadcastScores();
        
        // Schedule moles for extra time
        scheduleNextMoleExtraTime();
        
        // Schedule extra time end
        gameEndTask = gameScheduler.schedule(this::endExtraTime, EXTRA_TIME_DURATION, TimeUnit.SECONDS);
    }
    
    private void endExtraTime() {
        extraTimeActive = false;
        
        // Cancel mole spawning
        if (moleSpawnTask != null && !moleSpawnTask.isDone()) {
            moleSpawnTask.cancel(false);
        }
        
        // Get results from active players only
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        for (String activePlayer : activePlayersInExtraTime) {
            sorted.add(new AbstractMap.SimpleEntry<>(activePlayer, playerScores.get(activePlayer)));
        }
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        
        logMessage("Extra time ended");
        finishGame(sorted);
    }
    
    private void finishGame(List<Map.Entry<String, Integer>> sorted) {
        gameRunning = false;
        extraTimeActive = false;
        activePlayersInExtraTime.clear();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        
        String winner = sorted.isEmpty() ? "No one" : sorted.get(0).getKey();
        int winningScore = sorted.isEmpty() ? 0 : sorted.get(0).getValue();

        logMessage("Game ended. Winner: " + winner + " with score: " + winningScore);
        broadcastMessage("GAME_END:" + winner + ":" + winningScore);
    }
    
    private void stopGame() {
        gameRunning = false;
        extraTimeActive = false;
        activePlayersInExtraTime.clear();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        
        // Cancel all running tasks
        if (moleSpawnTask != null && !moleSpawnTask.isDone()) {
            moleSpawnTask.cancel(false);
        }
        if (gameEndTask != null && !gameEndTask.isDone()) {
            gameEndTask.cancel(false);
        }
        
        broadcastMessage("GAME_STOPPED");
        logMessage("Game stopped by server");
    }
    
    private void broadcastMessage(String message) {
        // Create a copy of the clients to avoid concurrent modification
        Map<String, ClientHandler> clientsCopy = new HashMap<>(clients);
        
        for (Map.Entry<String, ClientHandler> entry : clientsCopy.entrySet()) {
            try {
                entry.getValue().sendMessage(message);
            } catch (Exception e) {
                // If sending fails, disconnect the client
                logMessage("Failed to send message to " + entry.getKey() + ", disconnecting...");
                disconnectClient(entry.getKey());
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
    
    private void disconnectClient(String playerName) {
        ClientHandler client = clients.remove(playerName);
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                logMessage("Error closing client connection: " + e.getMessage());
            }
        }
        playerScores.remove(playerName);
        activePlayersInExtraTime.remove(playerName);
        logMessage("Player disconnected: " + playerName);
        updatePlayersLabel();
        
        // If game is running and not enough players, stop the game
        if (gameRunning && clients.size() < 1) {
            logMessage("Not enough players to continue game, stopping...");
            stopGame();
        }
        
        // If in extra time and active player disconnects, check if we should end extra time
        if (extraTimeActive && activePlayersInExtraTime.size() < 2) {
            logMessage("Not enough active players in extra time, ending...");
            endExtraTime();
        }
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
        
        public void sendMessage(String message) {
            if (out != null && !socket.isClosed()) {
                out.println(message);
                // Check if the connection is still alive
                if (out.checkError()) {
                    throw new RuntimeException("Failed to send message to client");
                }
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