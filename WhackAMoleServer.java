// package whack.a.mole.game;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

public class WhackAMoleServer extends JFrame {
    private static final int PORT = 12345;
    
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Integer> playerScores;
    
    // GUI Components
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel playersLabel;
    private JButton startButton;
    private JButton stopButton;
    
    public WhackAMoleServer() {
        clients = new ConcurrentHashMap<>();
        playerScores = new ConcurrentHashMap<>();
        
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
        
        // Add button actions (Maharani)
        
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
                // Handle client messages (Maharani)
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