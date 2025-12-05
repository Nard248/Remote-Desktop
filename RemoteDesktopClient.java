import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;

/**
 * Remote Desktop Client
 * Connects to server, displays remote screen, and forwards input events
 */
public class RemoteDesktopClient extends JFrame {

    // Message Type Constants (same as server)
    static class MessageType {
        public static final byte SCREEN_INFO = 0x00;
        public static final byte SCREEN_FRAME = 0x01;
        public static final byte MOUSE_MOVE = 0x02;
        public static final byte MOUSE_CLICK = 0x03;
        public static final byte KEY_PRESS = 0x04;
        public static final byte KEY_RELEASE = 0x05;
        public static final byte DISCONNECT = 0x09;
    }

    // Network
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private int sessionId;

    // UI Components
    private DisplayPanel displayPanel;
    private JLabel statusLabel;

    // Remote screen info
    private int remoteWidth;
    private int remoteHeight;

    // Connection state
    private volatile boolean connected = false;

    public RemoteDesktopClient(String serverAddress) throws IOException {
        super("Remote Desktop Client");

        // Connect to server
        this.socket = new Socket(serverAddress, 5900);
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.connected = true;

        // Setup UI
        setupUI();

        // Start receiver thread
        Thread receiverThread = new Thread(this::receiveMessages);
        receiverThread.setDaemon(true);
        receiverThread.start();

        System.out.println("Connected to server: " + serverAddress);
    }

    /**
     * Setup the user interface
     */
    private void setupUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 768);
        setLayout(new BorderLayout());

        // Create display panel
        displayPanel = new DisplayPanel();
        add(displayPanel, BorderLayout.CENTER);

        // Create status bar
        statusLabel = new JLabel("Connecting...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(statusLabel, BorderLayout.SOUTH);

        // Window closing handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Receive and process messages from server
     */
    private void receiveMessages() {
        try {
            while (connected) {
                byte messageType = in.readByte();
                int length = in.readInt();
                sessionId = in.readInt();

                switch (messageType) {
                    case MessageType.SCREEN_INFO:
                        handleScreenInfo();
                        break;

                    case MessageType.SCREEN_FRAME:
                        handleScreenFrame(length);
                        break;

                    default:
                        System.err.println("Unknown message type: " + messageType);
                        // Skip unknown message
                        in.skipBytes(length);
                }
            }
        } catch (EOFException e) {
            System.out.println("Server disconnected");
        } catch (IOException e) {
            if (connected) {
                System.err.println("Connection error: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * Handle screen info message
     */
    private void handleScreenInfo() throws IOException {
        remoteWidth = in.readInt();
        remoteHeight = in.readInt();

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Connected - Remote screen: " + remoteWidth + "x" + remoteHeight);
        });

        System.out.println("Remote screen size: " + remoteWidth + "x" + remoteHeight);
    }

    /**
     * Handle screen frame message
     */
    private void handleScreenFrame(int length) throws IOException {
        // Read image data
        byte[] imageData = new byte[length];
        in.readFully(imageData);

        // Decompress JPEG
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        BufferedImage image = ImageIO.read(bais);

        if (image != null) {
            // Update display on EDT
            SwingUtilities.invokeLater(() -> {
                displayPanel.updateImage(image);
            });
        }
    }

    /**
     * Send mouse move event
     */
    private void sendMouseMove(int x, int y) {
        if (!connected) return;

        try {
            synchronized (out) {
                out.writeByte(MessageType.MOUSE_MOVE);
                out.writeInt(8); // 2 ints
                out.writeInt(sessionId);
                out.writeInt(x);
                out.writeInt(y);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Error sending mouse move: " + e.getMessage());
        }
    }

    /**
     * Send mouse click event
     */
    private void sendMouseClick(int button, boolean pressed) {
        if (!connected) return;

        try {
            synchronized (out) {
                out.writeByte(MessageType.MOUSE_CLICK);
                out.writeInt(5); // int + boolean
                out.writeInt(sessionId);
                out.writeInt(button);
                out.writeBoolean(pressed);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Error sending mouse click: " + e.getMessage());
        }
    }

    /**
     * Send key press event
     */
    private void sendKeyPress(int keyCode) {
        if (!connected) return;

        try {
            synchronized (out) {
                out.writeByte(MessageType.KEY_PRESS);
                out.writeInt(4); // 1 int
                out.writeInt(sessionId);
                out.writeInt(keyCode);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Error sending key press: " + e.getMessage());
        }
    }

    /**
     * Send key release event
     */
    private void sendKeyRelease(int keyCode) {
        if (!connected) return;

        try {
            synchronized (out) {
                out.writeByte(MessageType.KEY_RELEASE);
                out.writeInt(4); // 1 int
                out.writeInt(sessionId);
                out.writeInt(keyCode);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Error sending key release: " + e.getMessage());
        }
    }

    /**
     * Disconnect from server
     */
    private void disconnect() {
        if (!connected) return;

        connected = false;

        try {
            // Send disconnect message
            synchronized (out) {
                out.writeByte(MessageType.DISCONNECT);
                out.writeInt(0);
                out.writeInt(sessionId);
                out.flush();
            }
        } catch (IOException e) {
            // Ignore
        }

        // Close resources
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Disconnected");
        });

        System.out.println("Disconnected from server");
    }

    /**
     * Display Panel - Shows remote screen and handles input
     */
    private class DisplayPanel extends JPanel {
        private BufferedImage currentImage;
        private Image scaledImage;

        public DisplayPanel() {
            setBackground(Color.BLACK);
            setFocusable(true);

            // Mouse motion listener
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    Point serverCoords = clientToServerCoords(e.getPoint());
                    sendMouseMove(serverCoords.x, serverCoords.y);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    Point serverCoords = clientToServerCoords(e.getPoint());
                    sendMouseMove(serverCoords.x, serverCoords.y);
                }
            });

            // Mouse listener
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    sendMouseClick(e.getButton(), true);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    sendMouseClick(e.getButton(), false);
                }
            });

            // Key listener
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    sendKeyPress(e.getKeyCode());
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    sendKeyRelease(e.getKeyCode());
                }
            });
        }

        /**
         * Update the displayed image
         */
        public void updateImage(BufferedImage image) {
            this.currentImage = image;

            if (image != null && getWidth() > 0 && getHeight() > 0) {
                // Calculate scale to maintain aspect ratio
                double scaleX = (double) getWidth() / image.getWidth();
                double scaleY = (double) getHeight() / image.getHeight();
                double scale = Math.min(scaleX, scaleY);

                int scaledWidth = (int) (image.getWidth() * scale);
                int scaledHeight = (int) (image.getHeight() * scale);

                scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_FAST);
            }

            repaint();
        }

        /**
         * Convert client coordinates to server coordinates
         */
        private Point clientToServerCoords(Point clientPoint) {
            if (currentImage == null || scaledImage == null) {
                return clientPoint;
            }

            // Get scaled image dimensions
            int scaledWidth = scaledImage.getWidth(null);
            int scaledHeight = scaledImage.getHeight(null);

            // Calculate offset (image is centered)
            int offsetX = (getWidth() - scaledWidth) / 2;
            int offsetY = (getHeight() - scaledHeight) / 2;

            // Adjust for centering
            int adjustedX = clientPoint.x - offsetX;
            int adjustedY = clientPoint.y - offsetY;

            // Clamp to bounds
            adjustedX = Math.max(0, Math.min(adjustedX, scaledWidth - 1));
            adjustedY = Math.max(0, Math.min(adjustedY, scaledHeight - 1));

            // Scale to server coordinates
            int serverX = (adjustedX * currentImage.getWidth()) / scaledWidth;
            int serverY = (adjustedY * currentImage.getHeight()) / scaledHeight;

            return new Point(serverX, serverY);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (scaledImage != null) {
                // Center the image
                int x = (getWidth() - scaledImage.getWidth(null)) / 2;
                int y = (getHeight() - scaledImage.getHeight(null)) / 2;
                g.drawImage(scaledImage, x, y, null);
            }
        }
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Ask for server address
                String serverAddress = JOptionPane.showInputDialog(
                        null,
                        "Enter server address:",
                        "Remote Desktop Client",
                        JOptionPane.QUESTION_MESSAGE
                );

                if (serverAddress == null || serverAddress.trim().isEmpty()) {
                    System.out.println("No server address provided. Exiting.");
                    return;
                }

                // Create client
                new RemoteDesktopClient(serverAddress.trim());

            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Failed to connect to server:\n" + e.getMessage(),
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE
                );
                System.err.println("Connection failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
