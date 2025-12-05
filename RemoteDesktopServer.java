import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

/**
 * Remote Desktop Server
 * Accepts multiple client connections and broadcasts screen captures
 * while processing remote input events.
 */
public class RemoteDesktopServer {

    // Configuration Constants
    private static final int PORT = 5900;
    private static final int SCREEN_CAPTURE_FPS = 10;
    private static final int JPEG_QUALITY = 50;

    // Server State
    private ServerSocket serverSocket;
    private Robot robot;
    private Rectangle screenRect;
    private CopyOnWriteArrayList<ClientHandler> clients;
    private AtomicInteger sessionIdGenerator;
    private volatile boolean running = true;

    /**
     * Message Type Constants
     */
    static class MessageType {
        public static final byte SCREEN_INFO = 0x00;     // Server→Client: screen dimensions
        public static final byte SCREEN_FRAME = 0x01;    // Server→Client: JPEG frame
        public static final byte MOUSE_MOVE = 0x02;      // Client→Server: mouse position
        public static final byte MOUSE_CLICK = 0x03;     // Client→Server: mouse button
        public static final byte KEY_PRESS = 0x04;       // Client→Server: key down
        public static final byte KEY_RELEASE = 0x05;     // Client→Server: key up
        public static final byte DISCONNECT = 0x09;      // Client→Server: disconnect
    }

    public RemoteDesktopServer() throws AWTException, IOException {
        // Initialize Robot for screen capture and input control
        this.robot = new Robot();

        // Get screen dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.screenRect = new Rectangle(screenSize);

        // Initialize client list
        this.clients = new CopyOnWriteArrayList<>();
        this.sessionIdGenerator = new AtomicInteger(1);

        // Create server socket
        this.serverSocket = new ServerSocket(PORT);

        System.out.println("Remote Desktop Server started on port " + PORT);
        System.out.println("Screen size: " + screenSize.width + "x" + screenSize.height);
    }

    /**
     * Start the server
     */
    public void start() {
        // Start screen broadcast thread
        Thread broadcastThread = new Thread(this::broadcastScreen);
        broadcastThread.setDaemon(true);
        broadcastThread.start();

        // Accept client connections
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                int sessionId = sessionIdGenerator.getAndIncrement();
                System.out.println("Client connected - Session ID: " + sessionId);

                ClientHandler handler = new ClientHandler(clientSocket, sessionId);
                clients.add(handler);

                Thread clientThread = new Thread(handler);
                clientThread.start();

            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Broadcast screen captures to all connected clients
     */
    private void broadcastScreen() {
        long frameDelay = 1000 / SCREEN_CAPTURE_FPS; // 100ms for 10 FPS

        while (running) {
            try {
                long startTime = System.currentTimeMillis();

                // Capture screen
                BufferedImage screenshot = robot.createScreenCapture(screenRect);

                // Compress to JPEG
                byte[] imageData = compressImage(screenshot);

                // Broadcast to all clients
                for (ClientHandler client : clients) {
                    client.sendScreenFrame(imageData);
                }

                // Maintain frame rate
                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = frameDelay - elapsed;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in broadcast thread: " + e.getMessage());
            }
        }
    }

    /**
     * Compress BufferedImage to JPEG with quality control
     */
    private byte[] compressImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY / 100.0f);

        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        ios.close();

        return baos.toByteArray();
    }

    /**
     * Client Handler - One per connected client
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private int sessionId;
        private DataInputStream in;
        private DataOutputStream out;
        private volatile boolean connected = true;

        public ClientHandler(Socket socket, int sessionId) throws IOException {
            this.socket = socket;
            this.sessionId = sessionId;
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public void run() {
            try {
                // Send initial screen info
                sendScreenInfo();

                // Process client messages
                while (connected && running) {
                    byte messageType = in.readByte();
                    int length = in.readInt();
                    int clientSessionId = in.readInt();

                    switch (messageType) {
                        case MessageType.MOUSE_MOVE:
                            handleMouseMove();
                            break;

                        case MessageType.MOUSE_CLICK:
                            handleMouseClick();
                            break;

                        case MessageType.KEY_PRESS:
                            handleKeyPress();
                            break;

                        case MessageType.KEY_RELEASE:
                            handleKeyRelease();
                            break;

                        case MessageType.DISCONNECT:
                            System.out.println("Client disconnected - Session ID: " + sessionId);
                            connected = false;
                            break;

                        default:
                            System.err.println("Unknown message type: " + messageType);
                    }
                }

            } catch (EOFException e) {
                System.out.println("Client disconnected - Session ID: " + sessionId);
            } catch (IOException e) {
                System.err.println("Client error (Session " + sessionId + "): " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        /**
         * Send screen dimensions to client
         */
        private void sendScreenInfo() throws IOException {
            synchronized (out) {
                out.writeByte(MessageType.SCREEN_INFO);
                out.writeInt(8); // Length: 2 ints
                out.writeInt(sessionId);
                out.writeInt(screenRect.width);
                out.writeInt(screenRect.height);
                out.flush();
            }
        }

        /**
         * Send screen frame to client
         */
        public void sendScreenFrame(byte[] imageData) {
            if (!connected) return;

            try {
                synchronized (out) {
                    out.writeByte(MessageType.SCREEN_FRAME);
                    out.writeInt(imageData.length);
                    out.writeInt(sessionId);
                    out.write(imageData);
                    out.flush();
                }
            } catch (IOException e) {
                System.err.println("Error sending frame to Session " + sessionId + ": " + e.getMessage());
                connected = false;
            }
        }

        /**
         * Handle mouse movement
         */
        private void handleMouseMove() throws IOException {
            int x = in.readInt();
            int y = in.readInt();
            robot.mouseMove(x, y);
        }

        /**
         * Handle mouse click
         */
        private void handleMouseClick() throws IOException {
            int button = in.readInt();
            boolean pressed = in.readBoolean();

            int robotButton = convertToRobotButton(button);

            if (pressed) {
                robot.mousePress(robotButton);
            } else {
                robot.mouseRelease(robotButton);
            }
        }

        /**
         * Handle key press
         */
        private void handleKeyPress() throws IOException {
            int keyCode = in.readInt();
            robot.keyPress(keyCode);
        }

        /**
         * Handle key release
         */
        private void handleKeyRelease() throws IOException {
            int keyCode = in.readInt();
            robot.keyRelease(keyCode);
        }

        /**
         * Convert button number to Robot button mask
         */
        private int convertToRobotButton(int button) {
            switch (button) {
                case 1: return InputEvent.BUTTON1_DOWN_MASK;  // Left
                case 2: return InputEvent.BUTTON2_DOWN_MASK;  // Middle
                case 3: return InputEvent.BUTTON3_DOWN_MASK;  // Right
                default: return InputEvent.BUTTON1_DOWN_MASK;
            }
        }

        /**
         * Cleanup resources
         */
        private void cleanup() {
            connected = false;
            clients.remove(this);

            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Error during cleanup: " + e.getMessage());
            }
        }
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        try {
            RemoteDesktopServer server = new RemoteDesktopServer();
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
