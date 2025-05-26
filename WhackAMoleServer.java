// package whack.a.mole.game;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WhackAMoleServer extends JFrame {
    private static final int PORT = 12345;
    private static final int GAME_DURATION = 60;
    private static final int MIN_MOLE_INTERVAL = 1;
    private static final int MAX_MOLE_INTERVAL = 3;

    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Integer> playerScores;

    private boolean gameRunning;
    private int currentMoleX, currentMoleY;
    private long moleAppearTime;
    private ScheduledExecutorService gameScheduler;
    private ScheduledFuture<?> moleSpawnTask;
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
        gameScheduler = Executors.newScheduledThreadPool(2);
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

            String message;
            while ((message = in.readLine()) != null) {
                // Update handleNewClient to use handleClientMessage
                // Replace the TODO comment in the while loop with
                handleClientMessage(playerName, message);
                logMessage("Message from " + playerName + ": " + message);
            }

        } catch (IOException e) {
            logMessage("Client connection error for " + (playerName != null ? playerName : "unknown"));
        } finally {
            if (playerName != null) {
                disconnectClient(playerName);
            }
        }
    }

    public void broadcastMessage(String message) {
        Map<String, ClientHandler> clientsCopy = new HashMap<>(clients);
        for (Map.Entry<String, ClientHandler> entry : clientsCopy.entrySet()) {
            try {
                entry.getValue().sendMessage(message);
            } catch (Exception e) {
                disconnectClient(entry.getKey());
            }
        }
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
        logMessage("Player disconnected: " + playerName);
        updatePlayersLabel();
    }

    private void updatePlayersLabel() {
        SwingUtilities.invokeLater(() -> playersLabel.setText("Connected Players: " + clients.size()));
    }

    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new Date().toString();
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startGame() {
        if (clients.size() < 1) {
            JOptionPane.showMessageDialog(this, "Need at least 1 players to start!");
            return;
        }

        gameRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);

        playerScores.replaceAll((k, v) -> 0);

        logMessage("Game started with " + clients.size() + " players");
        broadcastMessage("GAME_START:" + GAME_DURATION);
        broadcastScores();

        scheduleNextMole();
        gameScheduler.schedule(this::endGame, GAME_DURATION, TimeUnit.SECONDS);
    }

    private void scheduleNextMole() {
        if (!gameRunning)
            return;

        int randomInterval = MIN_MOLE_INTERVAL + random.nextInt(MAX_MOLE_INTERVAL - MIN_MOLE_INTERVAL + 1);

        moleSpawnTask = gameScheduler.schedule(() -> {
            spawnMole();
            scheduleNextMole();
        }, randomInterval, TimeUnit.SECONDS);
    }

    private void spawnMole() {
        if (!gameRunning)
            return;

        currentMoleX = random.nextInt(3);
        currentMoleY = random.nextInt(3);
        moleAppearTime = System.currentTimeMillis();

        broadcastMessage("MOLE_SPAWN:" + currentMoleX + ":" + currentMoleY);
        logMessage("Mole spawned at (" + currentMoleX + ", " + currentMoleY + ")");
    }

    private void handleClientMessage(String playerName, String message) {
        if (message.startsWith("HIT:")) {
            String[] parts = message.split(":");
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            long hitTime = Long.parseLong(parts[3]);

            if (gameRunning) {
                int currentScore = playerScores.get(playerName);

                if (Math.abs(hitTime - moleAppearTime) < 3000 &&
                        x == currentMoleX && y == currentMoleY) {

                    playerScores.put(playerName, currentScore + 10);
                    logMessage(playerName + " scored! New score: " + (currentScore + 10));

                } else {
                    int newScore = Math.max(0, currentScore - 5);
                    playerScores.put(playerName, newScore);
                    logMessage(playerName + " missed! Score reduced to: " + newScore);
                }

                broadcastScores();
            }
        } else if (message.equals("DISCONNECT")) {
            disconnectClient(playerName);
        }
    }

    private void broadcastScores() {
        StringBuilder scoreMsg = new StringBuilder("SCORES");
        for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
            scoreMsg.append(":").append(entry.getKey()).append(",").append(entry.getValue());
        }
        broadcastMessage(scoreMsg.toString());
    }

    private void endGame() {
        gameRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        if (moleSpawnTask != null && !moleSpawnTask.isDone()) {
            moleSpawnTask.cancel(false);
        }

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

        if (moleSpawnTask != null && !moleSpawnTask.isDone()) {
            moleSpawnTask.cancel(false);
        }

        broadcastMessage("GAME_STOPPED");
        logMessage("Game stopped by server");
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