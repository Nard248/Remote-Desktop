# Remote Desktop Application

A complete client-server remote desktop application implemented in pure Java that enables remote control of a computer over a network.

## Features

- **Screen Sharing**: Real-time screen broadcasting with JPEG compression (10 FPS)
- **Mouse Control**: Full mouse movement and click forwarding (left, middle, right buttons)
- **Keyboard Control**: Complete keyboard input forwarding including special keys and modifiers
- **Multi-User Support**: Supports 3+ simultaneous client connections
- **Efficient Compression**: JPEG compression with quality control (50% quality setting)
- **Responsive GUI**: Swing-based client with automatic screen scaling and aspect ratio preservation

## Technology Stack

- **Language**: Pure Java (JDK 8+)
- **GUI Framework**: javax.swing
- **Networking**: java.net.Socket (TCP)
- **Screen Capture**: java.awt.Robot
- **Image Compression**: javax.imageio.ImageIO (JPEG)

## System Requirements

- Java Development Kit (JDK) 8 or higher
- Operating System: Windows, macOS, or Linux
- Network connectivity between client and server
- Sufficient permissions for screen capture and input control

## Project Structure

```
remote-desktop/
├── RemoteDesktopServer.java    # Server implementation (~350 lines)
├── RemoteDesktopClient.java    # Client implementation (~450 lines)
├── README.md                    # This file
└── TEST_RESULTS.md              # Test documentation
```

## Configuration

### Server Configuration (RemoteDesktopServer.java)

```java
private static final int PORT = 5900;                // Server port
private static final int SCREEN_CAPTURE_FPS = 10;    // Frames per second
private static final int JPEG_QUALITY = 50;          // JPEG quality (0-100)
```

### Protocol Specification

Binary protocol format: `[Type:1byte][Length:4bytes][SessionID:4bytes][Payload:variable]`

**Message Types:**
- `0x00` - SCREEN_INFO (server → client): screen dimensions
- `0x01` - SCREEN_FRAME (server → client): JPEG compressed frame
- `0x02` - MOUSE_MOVE (client → server): mouse position
- `0x03` - MOUSE_CLICK (client → server): mouse button event
- `0x04` - KEY_PRESS (client → server): key down event
- `0x05` - KEY_RELEASE (client → server): key up event
- `0x09` - DISCONNECT (client → server): disconnect notification

## Compilation

Compile both Java files using the standard Java compiler:

```bash
javac RemoteDesktopServer.java RemoteDesktopClient.java
```

This will generate the following class files:
- RemoteDesktopServer.class
- RemoteDesktopServer$ClientHandler.class
- RemoteDesktopServer$MessageType.class
- RemoteDesktopClient.class
- RemoteDesktopClient$DisplayPanel.class
- RemoteDesktopClient$MessageType.class
- Additional inner class files

## Usage

### Starting the Server

On the machine you want to control remotely:

```bash
java RemoteDesktopServer
```

**Expected Output:**
```
Remote Desktop Server started on port 5900
Screen size: 1920x1080
```

The server will:
- Listen on port 5900 for incoming connections
- Capture the screen at 10 FPS
- Accept multiple client connections simultaneously
- Display connection messages when clients connect/disconnect

### Starting the Client

On the remote machine (can be the same machine for testing):

```bash
java RemoteDesktopClient
```

1. A dialog will appear asking for the server address
2. Enter the server IP address or hostname (use `localhost` for local testing)
3. Click OK to connect

**Client Window:**
- Displays the remote screen in real-time
- Status bar shows connection status and remote screen dimensions
- Click the window to ensure keyboard focus
- All mouse and keyboard events are forwarded to the server

### Localhost Testing

For testing on a single machine:

**Terminal 1 (Server):**
```bash
java RemoteDesktopServer
```

**Terminal 2 (Client):**
```bash
java RemoteDesktopClient
# Enter: localhost
```

### Multi-Client Testing

To test multiple simultaneous connections:

**Terminal 1 (Server):**
```bash
java RemoteDesktopServer
```

**Terminals 2-4 (Clients):**
```bash
java RemoteDesktopClient
# Enter: localhost (or server IP)
```

All clients will see the same screen and can all control the server simultaneously.

## Controls

### Mouse Control
- **Move mouse**: Move the cursor within the client window
- **Left click**: Click normally in the client window
- **Right click**: Right-click in the client window
- **Middle click**: Middle-click in the client window (if available)

### Keyboard Control
- **Text input**: Click the client window to focus, then type normally
- **Special keys**: Enter, Backspace, Delete, Arrow keys all work
- **Modifiers**: Shift, Ctrl/Cmd, Alt/Option work correctly
- **All standard keys**: Function keys, Tab, Escape, etc.

**Important**: Click the client window to ensure keyboard focus before typing.

## Architecture

### Server Architecture

1. **Main Thread**: Accepts client connections, manages client handlers
2. **Broadcast Thread**: Captures screen every 100ms, compresses to JPEG, broadcasts to all clients
3. **ClientHandler Threads**: One per client, receives input events and executes them using Robot

**Thread Safety:**
- CopyOnWriteArrayList for thread-safe client list
- Synchronized blocks for socket output operations
- AtomicInteger for session ID generation

### Client Architecture

1. **Main Thread (EDT)**: Swing GUI, event handling, user interaction
2. **Receiver Thread**: Background daemon receiving and processing screen frames

**Key Components:**
- DisplayPanel: Custom JPanel with mouse/keyboard listeners and coordinate transformation
- Image scaling: Maintains aspect ratio while scaling to window size
- Coordinate transformation: Maps client coordinates to server coordinates

## Troubleshooting

### Server Issues

**Problem**: Server fails to start
- **Solution**: Ensure port 5900 is not already in use
- **Solution**: Check that you have screen capture permissions (required on macOS)

**Problem**: No clients can connect
- **Solution**: Check firewall settings, ensure port 5900 is open
- **Solution**: Verify server is running and listening

### Client Issues

**Problem**: Cannot connect to server
- **Solution**: Verify server address and network connectivity
- **Solution**: Check firewall rules on both client and server
- **Solution**: Ensure server is running before starting client

**Problem**: Keyboard input not working
- **Solution**: Click the client window to ensure keyboard focus
- **Solution**: Check that the client window is the active window

**Problem**: Mouse coordinates seem off
- **Solution**: This may occur if screen scaling is incorrect; restart the client

**Problem**: Low frame rate
- **Solution**: Check network bandwidth between client and server
- **Solution**: Adjust JPEG_QUALITY constant (lower = smaller files, faster transfer)
- **Solution**: Adjust SCREEN_CAPTURE_FPS constant (lower = less bandwidth)

### Performance Tuning

To adjust performance:

1. **Lower bandwidth usage**: Decrease JPEG_QUALITY (25-40)
2. **Higher quality**: Increase JPEG_QUALITY (60-80)
3. **Lower latency**: Decrease SCREEN_CAPTURE_FPS (5-8)
4. **Smoother updates**: Increase SCREEN_CAPTURE_FPS (15-20)

Edit the constants in `RemoteDesktopServer.java` and recompile.

## Network Configuration

### Local Network
- Ensure both machines are on the same network
- Use the server's local IP address (e.g., 192.168.1.100)
- Check firewall rules on both machines

### Internet (Advanced)
- Set up port forwarding on the server's router (port 5900)
- Use the server's public IP address
- Consider security implications (encryption not implemented)

## Security Considerations

This is an educational implementation and does **not** include:
- Authentication
- Encryption
- Access control

**Do not use over untrusted networks without additional security measures.**

For production use, consider:
- Adding SSL/TLS encryption
- Implementing user authentication
- Using a VPN or SSH tunnel
- Restricting access by IP address

## Limitations

- No encryption (data sent in plain text)
- No authentication (anyone can connect)
- No clipboard sharing
- No file transfer
- No audio forwarding
- JPEG compression artifacts at 50% quality
- Network latency affects responsiveness

## Performance Metrics

Typical performance on localhost:
- Frame rate: 8-10 FPS
- Latency: 50-100ms
- Bandwidth: 1-5 Mbps (depends on screen content and resolution)

Performance over network depends on:
- Network bandwidth and latency
- Screen resolution
- Screen content (static vs. dynamic)
- Number of connected clients

## License

This is an educational project created for a network architecture class.

## Author

Created as a demonstration of client-server architecture, network protocols, and remote desktop technology in Java.

## Acknowledgments

- Uses Java AWT Robot for screen capture and input control
- Uses Java Swing for GUI
- JPEG compression via ImageIO
# Remote-Desktop
