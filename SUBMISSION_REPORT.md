# Remote Desktop Application
## Network Architecture Course - Project Submission Report

---

## Executive Summary

This project implements a complete **Remote Desktop Application** from scratch using pure Java, demonstrating fundamental network programming concepts including TCP socket communication, custom binary protocol design, multi-threaded client-server architecture, and real-time data streaming.

**Key Achievement:** Successfully tested remote desktop control between two physical laptops over a local network, proving the implementation uses genuine network communication.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Network Architecture](#2-network-architecture)
3. [Protocol Specification](#3-protocol-specification)
4. [Implementation Details](#4-implementation-details)
5. [Code Walkthrough](#5-code-walkthrough)
6. [Testing Environment & Results](#6-testing-environment--results)
7. [Proof of Network Implementation](#7-proof-of-network-implementation)
8. [Challenges & Solutions](#8-challenges--solutions)
9. [Conclusion](#9-conclusion)
10. [Appendix: Source Code](#10-appendix-source-code)

---

## 1. Project Overview

### 1.1 Objective

Develop a client-server remote desktop application that enables:
- Real-time screen sharing from server to client(s)
- Remote mouse and keyboard control from client to server
- Multiple simultaneous client connections

### 1.2 Scope

| Feature | Implementation |
|---------|----------------|
| Screen Capture | java.awt.Robot |
| Image Compression | JPEG via javax.imageio (50% quality) |
| Networking | Raw TCP Sockets (java.net.Socket) |
| GUI | javax.swing |
| Multi-client | Thread-per-client model |
| Frame Rate | 10 FPS |

### 1.3 What We Did NOT Use

We explicitly avoided using any existing remote desktop libraries or protocols:

| NOT Used | Why |
|----------|-----|
| ❌ VNC libraries (TightVNC, RealVNC) | Would bypass network learning |
| ❌ RDP protocol libraries | Would bypass protocol design |
| ❌ Java RMI | Would abstract away socket programming |
| ❌ HTTP/WebSocket frameworks | Not raw socket programming |
| ❌ Third-party screen sharing APIs | Would bypass implementation |

**Everything is implemented from scratch using only:**
- `java.net.Socket` / `java.net.ServerSocket` - Raw TCP sockets
- `java.io.DataInputStream` / `java.io.DataOutputStream` - Binary I/O
- `java.awt.Robot` - Screen capture and input injection
- `javax.swing` - GUI components
- `javax.imageio` - JPEG compression

---

## 2. Network Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         NETWORK (TCP/IP)                        │
│                        Port 5900 (TCP)                          │
└─────────────────────────────────────────────────────────────────┘
                    ▲                           │
                    │ Input Events              │ Screen Frames
                    │ (Mouse, Keyboard)         │ (JPEG compressed)
                    │                           ▼
┌───────────────────┴───────────┐   ┌──────────┴────────────────┐
│         CLIENT(S)             │   │         SERVER            │
│  ┌─────────────────────────┐  │   │  ┌─────────────────────┐  │
│  │      GUI (Swing)        │  │   │  │   Screen Capture    │  │
│  │   - DisplayPanel        │  │   │  │   (Robot API)       │  │
│  │   - Mouse Listeners     │  │   │  └──────────┬──────────┘  │
│  │   - Keyboard Listeners  │  │   │             │             │
│  └──────────┬──────────────┘  │   │             ▼             │
│             │                 │   │  ┌──────────────────────┐ │
│             ▼                 │   │  │  JPEG Compression    │ │
│  ┌─────────────────────────┐  │   │  │  (ImageIO)           │ │
│  │    Network Layer        │  │   │  └──────────┬───────────┘ │
│  │  - Socket Connection    │  │   │             │             │
│  │  - DataInputStream      │  │   │             ▼             │
│  │  - DataOutputStream     │  │   │  ┌──────────────────────┐ │
│  └─────────────────────────┘  │   │  │   Broadcast Thread   │ │
│                               │   │  │   (to all clients)   │ │
│  IP: 192.168.1.106            │   │  └──────────────────────┘ │
│  (Control FROM here)          │   │                           │
└───────────────────────────────┘   │  ┌──────────────────────┐ │
                                    │  │  Client Handlers     │ │
┌───────────────────────────────┐   │  │  (one per client)    │ │
│       CLIENT 2 (optional)     │   │  │  - Receive input     │ │
└───────────────────────────────┘   │  │  - Execute via Robot │ │
                                    │  └──────────────────────┘ │
┌───────────────────────────────┐   │                           │
│       CLIENT 3 (optional)     │   │  IP: 192.168.1.108        │
└───────────────────────────────┘   │  (Being controlled)       │
                                    └───────────────────────────┘
```

### 2.2 Thread Architecture

**Server Threads:**
```
Main Thread
    │
    ├── Accept client connections (blocking)
    │
    ├── Screen Broadcast Thread (daemon)
    │       └── Loop: capture → compress → broadcast to all clients
    │
    ├── ClientHandler Thread #1
    │       └── Loop: receive input → execute with Robot
    │
    ├── ClientHandler Thread #2
    │       └── Loop: receive input → execute with Robot
    │
    └── ClientHandler Thread #N
            └── Loop: receive input → execute with Robot
```

**Client Threads:**
```
Main Thread (EDT - Event Dispatch Thread)
    │
    ├── Swing GUI Event Handling
    │       ├── Mouse events → send to server
    │       └── Keyboard events → send to server
    │
    └── Receiver Thread (daemon)
            └── Loop: receive frames → decompress → display
```

### 2.3 Data Flow

**Screen Sharing (Server → Client):**
```
Robot.createScreenCapture()
        │
        ▼
BufferedImage (raw pixels)
        │
        ▼
JPEG Compression (50% quality)
        │
        ▼
byte[] (compressed data, ~50-200KB per frame)
        │
        ▼
DataOutputStream.write() ──────► TCP Socket ──────► DataInputStream.read()
                                                            │
                                                            ▼
                                                    ImageIO.read()
                                                            │
                                                            ▼
                                                    BufferedImage
                                                            │
                                                            ▼
                                                    DisplayPanel.paintComponent()
```

**Input Forwarding (Client → Server):**
```
MouseEvent / KeyEvent (on client)
        │
        ▼
Event Listener captures
        │
        ▼
Coordinate transformation (for mouse)
        │
        ▼
Protocol message construction
        │
        ▼
DataOutputStream.write() ──────► TCP Socket ──────► DataInputStream.read()
                                                            │
                                                            ▼
                                                    Parse message type
                                                            │
                                                            ▼
                                                    Robot.mouseMove() /
                                                    Robot.mousePress() /
                                                    Robot.keyPress()
```

---

## 3. Protocol Specification

### 3.1 Binary Protocol Format

All messages follow this structure:

```
┌──────────┬──────────────┬───────────────┬─────────────────┐
│  Type    │   Length     │   Session ID  │    Payload      │
│ (1 byte) │  (4 bytes)   │   (4 bytes)   │   (variable)    │
└──────────┴──────────────┴───────────────┴─────────────────┘
```

| Field | Size | Description |
|-------|------|-------------|
| Type | 1 byte | Message type identifier (0x00 - 0x09) |
| Length | 4 bytes (int) | Length of payload in bytes |
| Session ID | 4 bytes (int) | Unique client session identifier |
| Payload | Variable | Message-specific data |

### 3.2 Message Types

```java
class MessageType {
    public static final byte SCREEN_INFO   = 0x00;  // Server → Client
    public static final byte SCREEN_FRAME  = 0x01;  // Server → Client
    public static final byte MOUSE_MOVE    = 0x02;  // Client → Server
    public static final byte MOUSE_CLICK   = 0x03;  // Client → Server
    public static final byte KEY_PRESS     = 0x04;  // Client → Server
    public static final byte KEY_RELEASE   = 0x05;  // Client → Server
    public static final byte DISCONNECT    = 0x09;  // Client → Server
}
```

### 3.3 Message Payload Specifications

**SCREEN_INFO (0x00)** - Server sends screen dimensions to client
```
┌─────────────┬──────────────┐
│   Width     │    Height    │
│  (4 bytes)  │   (4 bytes)  │
└─────────────┴──────────────┘
```

**SCREEN_FRAME (0x01)** - Server sends JPEG compressed screen capture
```
┌────────────────────────────────────────┐
│         JPEG Image Data                │
│    (variable length, specified in      │
│     message header Length field)       │
└────────────────────────────────────────┘
```

**MOUSE_MOVE (0x02)** - Client sends mouse position
```
┌─────────────┬──────────────┐
│      X      │      Y       │
│  (4 bytes)  │   (4 bytes)  │
└─────────────┴──────────────┘
```

**MOUSE_CLICK (0x03)** - Client sends mouse button event
```
┌─────────────┬──────────────┐
│   Button    │   Pressed    │
│  (4 bytes)  │   (1 byte)   │
│  1=L,2=M,3=R│  true/false  │
└─────────────┴──────────────┘
```

**KEY_PRESS (0x04)** / **KEY_RELEASE (0x05)** - Client sends keyboard event
```
┌─────────────┐
│   KeyCode   │
│  (4 bytes)  │
│ (AWT VK_*)  │
└─────────────┘
```

**DISCONNECT (0x09)** - Client notifies server of disconnect
```
(no payload)
```

### 3.4 Protocol Sequence Diagram

```
    CLIENT                                          SERVER
       │                                               │
       │◄──────────── TCP Connection ─────────────────►│
       │                                               │
       │              SCREEN_INFO (0x00)               │
       │◄──────────── width=1920, height=1080 ────────│
       │                                               │
       │              SCREEN_FRAME (0x01)              │
       │◄──────────── [JPEG data ~100KB] ─────────────│
       │                                               │
       │              MOUSE_MOVE (0x02)                │
       │─────────────► x=500, y=300 ──────────────────►│
       │                                               │
       │              SCREEN_FRAME (0x01)              │
       │◄──────────── [JPEG data ~100KB] ─────────────│
       │                                               │
       │              MOUSE_CLICK (0x03)               │
       │─────────────► button=1, pressed=true ────────►│
       │                                               │
       │              MOUSE_CLICK (0x03)               │
       │─────────────► button=1, pressed=false ───────►│
       │                                               │
       │              KEY_PRESS (0x04)                 │
       │─────────────► keyCode=65 (A) ────────────────►│
       │                                               │
       │              KEY_RELEASE (0x05)               │
       │─────────────► keyCode=65 (A) ────────────────►│
       │                                               │
       │              SCREEN_FRAME (0x01)              │
       │◄──────────── [JPEG data ~100KB] ─────────────│
       │                                               │
       │                    ...                        │
       │              (continues at ~10 FPS)           │
       │                                               │
       │              DISCONNECT (0x09)                │
       │─────────────► (client closing) ──────────────►│
       │                                               │
       │◄──────────── TCP Connection Closed ──────────►│
```

---

## 4. Implementation Details

### 4.1 Server Implementation (RemoteDesktopServer.java)

**Key Components:**

| Component | Purpose |
|-----------|---------|
| `ServerSocket` | Listens on port 5900 for incoming connections |
| `Robot` | Captures screen and simulates input events |
| `CopyOnWriteArrayList<ClientHandler>` | Thread-safe list of connected clients |
| `AtomicInteger` | Thread-safe session ID generator |
| Broadcast Thread | Captures and sends screen frames to all clients |
| ClientHandler | Per-client thread handling input events |

**Configuration Constants:**
```java
private static final int PORT = 5900;
private static final int SCREEN_CAPTURE_FPS = 10;    // 10 frames per second
private static final int JPEG_QUALITY = 50;           // 50% JPEG quality
```

### 4.2 Client Implementation (RemoteDesktopClient.java)

**Key Components:**

| Component | Purpose |
|-----------|---------|
| `Socket` | TCP connection to server |
| `JFrame` | Main application window |
| `DisplayPanel` | Custom JPanel showing remote screen |
| Receiver Thread | Receives and displays screen frames |
| Event Listeners | Capture and forward mouse/keyboard events |

**Coordinate Transformation:**

The client window may be a different size than the remote screen. We must transform coordinates:

```java
private Point clientToServerCoords(Point clientPoint) {
    // Get scaled image dimensions
    int scaledWidth = scaledImage.getWidth(null);
    int scaledHeight = scaledImage.getHeight(null);

    // Calculate offset (image is centered in panel)
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
```

### 4.3 JPEG Compression

We use ImageIO with explicit quality control for efficient bandwidth usage:

```java
private byte[] compressImage(BufferedImage image) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Get JPEG writer
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();

    // Configure compression quality
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(0.5f);  // 50% quality

    // Write compressed image
    ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
    writer.setOutput(ios);
    writer.write(null, new IIOImage(image, null, null), param);

    writer.dispose();
    ios.close();

    return baos.toByteArray();
}
```

**Compression Results:**
- Raw frame (1920x1080): ~6 MB
- JPEG compressed (50%): ~50-200 KB
- Compression ratio: ~30-120x

### 4.4 Thread Safety Mechanisms

| Mechanism | Usage |
|-----------|-------|
| `CopyOnWriteArrayList` | Safe iteration while clients connect/disconnect |
| `synchronized (out)` | Prevents interleaved writes to output stream |
| `volatile boolean` | Connection state flags visible across threads |
| `AtomicInteger` | Lock-free session ID generation |
| Daemon threads | Background threads don't prevent JVM exit |

---

## 5. Code Walkthrough

### 5.1 Server Startup Sequence

```java
// 1. Create Robot for screen capture and input control
this.robot = new Robot();

// 2. Get screen dimensions
Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
this.screenRect = new Rectangle(screenSize);

// 3. Initialize thread-safe client list
this.clients = new CopyOnWriteArrayList<>();
this.sessionIdGenerator = new AtomicInteger(1);

// 4. Create server socket
this.serverSocket = new ServerSocket(PORT);  // Port 5900

// 5. Start broadcast thread (daemon)
Thread broadcastThread = new Thread(this::broadcastScreen);
broadcastThread.setDaemon(true);
broadcastThread.start();

// 6. Accept client connections (main loop)
while (running) {
    Socket clientSocket = serverSocket.accept();  // Blocks until client connects
    int sessionId = sessionIdGenerator.getAndIncrement();
    ClientHandler handler = new ClientHandler(clientSocket, sessionId);
    clients.add(handler);
    new Thread(handler).start();
}
```

### 5.2 Screen Broadcast Loop

```java
private void broadcastScreen() {
    long frameDelay = 1000 / SCREEN_CAPTURE_FPS;  // 100ms for 10 FPS

    while (running) {
        long startTime = System.currentTimeMillis();

        // Capture screen
        BufferedImage screenshot = robot.createScreenCapture(screenRect);

        // Compress to JPEG
        byte[] imageData = compressImage(screenshot);

        // Broadcast to ALL connected clients
        for (ClientHandler client : clients) {
            client.sendScreenFrame(imageData);
        }

        // Maintain frame rate
        long elapsed = System.currentTimeMillis() - startTime;
        long sleepTime = frameDelay - elapsed;
        if (sleepTime > 0) {
            Thread.sleep(sleepTime);
        }
    }
}
```

### 5.3 Client Handler - Receiving Input

```java
public void run() {
    // Send initial screen info
    sendScreenInfo();

    // Process client messages
    while (connected && running) {
        byte messageType = in.readByte();
        int length = in.readInt();
        int clientSessionId = in.readInt();

        switch (messageType) {
            case MessageType.MOUSE_MOVE:
                int x = in.readInt();
                int y = in.readInt();
                robot.mouseMove(x, y);  // Execute on server!
                break;

            case MessageType.MOUSE_CLICK:
                int button = in.readInt();
                boolean pressed = in.readBoolean();
                int robotButton = convertToRobotButton(button);
                if (pressed) {
                    robot.mousePress(robotButton);
                } else {
                    robot.mouseRelease(robotButton);
                }
                break;

            case MessageType.KEY_PRESS:
                int keyCode = in.readInt();
                robot.keyPress(keyCode);  // Execute on server!
                break;

            case MessageType.KEY_RELEASE:
                int keyCode = in.readInt();
                robot.keyRelease(keyCode);
                break;

            case MessageType.DISCONNECT:
                connected = false;
                break;
        }
    }
}
```

### 5.4 Client - Sending Input Events

```java
// Mouse movement
private void sendMouseMove(int x, int y) {
    synchronized (out) {
        out.writeByte(MessageType.MOUSE_MOVE);
        out.writeInt(8);  // payload length: 2 ints
        out.writeInt(sessionId);
        out.writeInt(x);
        out.writeInt(y);
        out.flush();
    }
}

// Keyboard input
private void sendKeyPress(int keyCode) {
    synchronized (out) {
        out.writeByte(MessageType.KEY_PRESS);
        out.writeInt(4);  // payload length: 1 int
        out.writeInt(sessionId);
        out.writeInt(keyCode);
        out.flush();
    }
}
```

---

## 6. Testing Environment & Results

### 6.1 Test Environment

**Physical Setup:**

| Machine | Role | OS | IP Address |
|---------|------|-----|------------|
| Laptop A | Server (controlled) | Windows | 192.168.1.108 |
| Laptop B | Client (controller) | macOS | 192.168.1.106 |

**Network:**
- Connection: WiFi (same local network)
- Protocol: TCP
- Port: 5900

```
┌─────────────────────────────────────────────────────────┐
│                    WiFi Router                          │
│                   192.168.1.1                           │
└─────────────────────────────────────────────────────────┘
           │                              │
           │                              │
    ┌──────┴──────┐                ┌──────┴──────┐
    │  Windows    │                │   macOS     │
    │  Laptop     │  ◄──────────   │   Laptop    │
    │192.168.1.108│   Port 5900    │192.168.1.106│
    │  (SERVER)   │                │  (CLIENT)   │
    └─────────────┘                └─────────────┘
```

### 6.2 Test Results

| Test | Description | Result |
|------|-------------|--------|
| Compilation | Both files compile without errors | ✅ PASS |
| Server Startup | Server starts and listens on port 5900 | ✅ PASS |
| Cross-Network Connection | Client connects from different machine | ✅ PASS |
| Screen Display | Remote screen visible on client | ✅ PASS |
| Mouse Movement | Cursor moves on server when moved on client | ✅ PASS |
| Left Click | Left click registers on server | ✅ PASS |
| Right Click | Right click registers on server | ✅ PASS |
| Keyboard Input | Typing on client appears on server | ✅ PASS |
| Special Keys | Enter, Backspace, Arrow keys work | ✅ PASS |
| Modifier Keys | Shift, Ctrl work correctly | ✅ PASS |
| Multi-Client | Multiple clients can connect | ✅ PASS |
| Disconnect | Clean disconnect without errors | ✅ PASS |


### 6.3 Server Console Output

```
Remote Desktop Server started on port 5900
Screen size: 1920x1080
Client connected - Session ID: 1
```

### 6.4 Client Status

```
Connected - Remote screen: 1920x1080
```

---

## 7. Proof of Network Implementation

### 7.1 Evidence of Raw Socket Usage

**Server Socket Creation (RemoteDesktopServer.java:56):**
```java
this.serverSocket = new ServerSocket(PORT);  // Raw TCP ServerSocket
```

**Client Socket Creation (RemoteDesktopClient.java:46):**
```java
this.socket = new Socket(serverAddress, 5900);  // Raw TCP Socket
```

**No External Libraries:**
- No VNC libraries
- No RDP libraries
- No remote desktop frameworks
- Only standard Java SE classes

### 7.2 Network Traffic Evidence

The fact that we successfully tested between **two different physical machines** on different IP addresses proves genuine network communication:

- Server IP: 192.168.1.108 (Windows)
- Client IP: 192.168.1.106 (macOS)

This cannot work with localhost tricks or shared memory - it requires real TCP/IP networking.

### 7.3 Binary Protocol Evidence

Our custom protocol sends raw bytes:
```java
out.writeByte(MessageType.SCREEN_FRAME);  // 1 byte
out.writeInt(imageData.length);            // 4 bytes
out.writeInt(sessionId);                   // 4 bytes
out.write(imageData);                      // variable
out.flush();
```

This is low-level binary socket programming, not using any abstraction libraries.

### 7.4 Dependencies Analysis

**pom.xml / build.gradle:** None - this is pure Java SE

**Imports used:**
```java
// All standard Java SE - no external dependencies
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
```

---

## 8. Challenges & Solutions

### 8.1 Challenge: Thread Safety

**Problem:** Multiple clients sending/receiving data simultaneously caused race conditions.

**Solution:**
- Used `CopyOnWriteArrayList` for client list (safe iteration during modification)
- Used `synchronized` blocks when writing to output streams
- Used `volatile` for connection state flags

### 8.2 Challenge: Coordinate Transformation

**Problem:** Client window size differs from server screen size; mouse coordinates were wrong.

**Solution:** Implemented proper coordinate transformation:
1. Account for image centering in panel
2. Calculate scale factor based on aspect ratio
3. Transform client coordinates to server coordinates

### 8.3 Challenge: Bandwidth Optimization

**Problem:** Raw screen captures (~6MB each) would saturate network bandwidth.

**Solution:** JPEG compression with quality control:
- Reduced frame size to ~50-200KB (30-120x compression)
- Adjustable quality parameter (50% default)
- Achieves 10 FPS on typical home networks

### 8.4 Challenge: Input Event Translation

**Problem:** Java mouse button numbers differ from Robot API masks.

**Solution:** Button conversion function:
```java
private int convertToRobotButton(int button) {
    switch (button) {
        case 1: return InputEvent.BUTTON1_DOWN_MASK;  // Left
        case 2: return InputEvent.BUTTON2_DOWN_MASK;  // Middle
        case 3: return InputEvent.BUTTON3_DOWN_MASK;  // Right
        default: return InputEvent.BUTTON1_DOWN_MASK;
    }
}
```

---

## 9. Conclusion

### 9.1 Summary

This project successfully implements a functional remote desktop application demonstrating:

1. **TCP/IP Socket Programming** - Raw socket communication between client and server
2. **Custom Protocol Design** - Binary message format with defined message types
3. **Multi-threaded Architecture** - Concurrent handling of screen broadcast and input processing
4. **Real-time Streaming** - Continuous screen sharing at 10 FPS
5. **Cross-Platform Compatibility** - Works between Windows and macOS

### 9.2 Future Improvements

| Feature | Description |
|---------|-------------|
| Encryption | Add SSL/TLS for secure communication |
| Authentication | Add username/password login |
| Adaptive Quality | Adjust JPEG quality based on bandwidth |
| Clipboard Sharing | Share clipboard between client and server |
| File Transfer | Add file upload/download capability |
| Audio Forwarding | Stream audio from server to client |
---

## 10. Appendix: Source Code

### File: RemoteDesktopServer.java
- Key classes: `RemoteDesktopServer`, `ClientHandler`, `MessageType`

### File: RemoteDesktopClient.java
- Key classes: `RemoteDesktopClient`, `DisplayPanel`, `MessageType`

### Compilation Command
```bash
javac RemoteDesktopServer.java RemoteDesktopClient.java
```

### Execution Commands
```bash
# On server machine
java RemoteDesktopServer

# On client machine
java RemoteDesktopClient
# Enter server IP when prompted
```


---
## AI was used for documentation and research.
*End of Submission Report*
