package me.funtime.live;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A local bridge: EasTok webhook -> configurable gift map -> Paper RCON command. */
public final class FunTimeLive {
    private static final int WEBHOOK_PORT = 4782;
    private static final Color BACKGROUND = new Color(10, 13, 18);
    private static final Color SURFACE = new Color(22, 27, 35);
    private static final Color SURFACE_SOFT = new Color(29, 35, 45);
    private static final Color TEXT = new Color(239, 243, 248);
    private static final Color MUTED = new Color(153, 165, 180);
    private static final Color RED = new Color(218, 65, 60);
    private static final Color GOLD = new Color(244, 179, 61);
    private static final Font DISPLAY_FONT = new Font("Bahnschrift", Font.BOLD, 25);
    private static final Font UI_FONT = new Font("Bahnschrift", Font.PLAIN, 13);
    private static final Path DEFAULT_SERVER_PROPERTIES = Paths.get("H:\\MinecraftServerEastok\\server.properties");
    private static final Path APP_SETTINGS_FILE = Paths.get("FunTimeLive.properties");
    private static final Path MAP_FILE = Paths.get("FunTimeLive-gifts.properties");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern JSON_VALUE = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    private final Map<String, String> giftMap = new LinkedHashMap<>();
    private final Random random = new Random();
    private JFrame frame;
    private JLabel status;
    private JLabel connection;
    private JTextField playerField;
    private JTextField serverPropertiesField;
    private JTextField testGiftField;
    private JTextField testViewerField;
    private JTextArea mapArea;
    private JTextArea activity;
    private volatile String rconPassword;
    private volatile int rconPort = 25575;
    private volatile Path serverPropertiesPath = DEFAULT_SERVER_PROPERTIES;
    private volatile String configuredPlayer = "motyasm";
    private volatile HttpServer webhook;

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--self-test")) {
            new FunTimeLive().runSelfTest();
            return;
        }
        if (args.length == 1 && args[0].equals("--set-safe-spawn")) {
            new FunTimeLive().setSafeSpawn();
            return;
        }
        SwingUtilities.invokeLater(() -> new FunTimeLive().start());
    }

    private void runSelfTest() {
        loadGiftMap();
        loadServerConnection();
        startWebhook();
        try {
            HttpURLConnection request = (HttpURLConnection) new URL("http://127.0.0.1:" + WEBHOOK_PORT + "/eastok/gift").openConnection();
            request.setRequestMethod("POST");
            request.setDoOutput(true);
            request.getOutputStream().write("{\"value1\":\"test\"}".getBytes(StandardCharsets.UTF_8));
            int statusCode = request.getResponseCode();
            if (statusCode != 400) {
                throw new IOException("Expected HTTP 400, received " + statusCode);
            }
            System.out.println("FunTime Live self-test passed: webhook returned HTTP 400 for an incomplete event.");
            try {
                new RconClient("127.0.0.1", rconPort, rconPassword).command("list");
                System.out.println("FunTime Live self-test passed: RCON command completed.");
            } catch (IOException rconException) {
                System.out.println("FunTime Live RCON check skipped: server is not running or RCON is unavailable.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("FunTime Live self-test failed", exception);
        } finally {
            if (webhook != null) {
                webhook.stop(0);
            }
        }
    }

    private void setSafeSpawn() {
        loadServerConnection();
        String[] commands = {
                "execute in minecraft:overworld run forceload add 9992 9992 10008 10008",
                "execute in minecraft:overworld run fill 9992 99 9992 10008 99 10008 stone",
                "execute in minecraft:overworld run fill 9992 100 9992 10008 100 10008 grass_block",
                "execute in minecraft:overworld run fill 9992 101 9992 10008 105 10008 air",
                "execute in minecraft:overworld run setworldspawn 10000 101 10000",
                "execute in minecraft:overworld run forceload remove 9992 9992 10008 10008"
        };
        try {
            RconClient client = new RconClient("127.0.0.1", rconPort, rconPassword);
            for (String command : commands) {
                System.out.println(command + " -> " + client.command(command));
            }
            System.out.println("Safe spawn was created at 10000 101 10000.");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not set safe spawn", exception);
        }
    }

    private void start() {
        loadAppSettings();
        loadGiftMap();
        loadServerConnection();
        createWindow();
        startWebhook();
    }

    private void createWindow() {
        frame = new JFrame("FunTime Live");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1020, 700));

        JPanel root = new AnimatedBackgroundPanel();
        root.setLayout(new BorderLayout(0, 14));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        frame.setContentPane(root);

        GradientPanel header = new GradientPanel();
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel brand = new JPanel(new BorderLayout(0, 3));
        brand.setOpaque(false);
        JLabel eyebrow = label("MINECRAFT x TIKTOK LIVE", 11, GOLD);
        JLabel heading = label("FUN TIME LIVE", DISPLAY_FONT.getSize(), TEXT);
        brand.add(eyebrow, BorderLayout.NORTH);
        brand.add(heading, BorderLayout.CENTER);
        status = pill("STARTING LOCAL WEBHOOK", new Color(80, 140, 100));
        JPanel headerRight = transparentFlow();
        headerRight.add(status);
        headerRight.add(windowButton("—", "Minimize", new Color(66, 77, 94), () -> frame.setState(Frame.ICONIFIED)));
        headerRight.add(windowButton("□", "Maximize", new Color(66, 77, 94), this::toggleMaximize));
        headerRight.add(windowButton("×", "Close", new Color(185, 57, 57), () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
        header.add(brand, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);
        makeDraggable(header);
        root.add(header, BorderLayout.NORTH);

        RoundedPanel left = card();
        left.setLayout(new BorderLayout(10, 10));
        left.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        left.add(sectionHeader("GIFT ROUTING", "Choose what every gift does"), BorderLayout.NORTH);
        mapArea = new JTextArea();
        mapArea.setFont(new Font("Cascadia Mono", Font.PLAIN, 13));
        mapArea.setText(mapText());
        styleEditor(mapArea);
        left.add(scroll(mapArea), BorderLayout.CENTER);
        JPanel mapButtons = transparentFlow();
        JButton saveMap = accentButton("Save map", RED);
        saveMap.addActionListener(event -> saveGiftMap());
        JButton resetMap = secondaryButton("Restore defaults");
        resetMap.addActionListener(event -> {
            loadDefaultMap();
            mapArea.setText(mapText());
            log("Default gift map restored in the editor.");
        });
        mapButtons.add(saveMap);
        mapButtons.add(resetMap);
        left.add(mapButtons, BorderLayout.SOUTH);

        RoundedPanel right = card();
        right.setLayout(new BorderLayout(10, 10));
        right.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        right.add(sectionHeader("LIVE CONTROL", "Local webhook and Minecraft server"), BorderLayout.NORTH);
        JPanel controlStack = new JPanel(new BorderLayout(10, 10));
        controlStack.setOpaque(false);
        JPanel connectionBlock = new JPanel(new BorderLayout(0, 8));
        connectionBlock.setOpaque(false);
        JPanel serverControls = transparentFlow();
        serverControls.add(label("SERVER.PROPERTIES", 11, MUTED));
        serverPropertiesField = new JTextField(serverPropertiesPath.toString(), 25);
        styleField(serverPropertiesField);
        serverControls.add(serverPropertiesField);
        JButton reloadServer = secondaryButton("Reload server");
        reloadServer.addActionListener(event -> reloadServerSettings());
        serverControls.add(reloadServer);
        connectionBlock.add(serverControls, BorderLayout.NORTH);

        JPanel controls = transparentFlow();
        controls.add(label("MINECRAFT PLAYER", 11, MUTED));
        playerField = new JTextField(configuredPlayer, 12);
        styleField(playerField);
        controls.add(playerField);
        connection = pill("RCON: LOADING", new Color(105, 120, 140));
        controls.add(connection);
        JButton rconTest = secondaryButton("Test RCON");
        rconTest.addActionListener(event -> testRcon());
        controls.add(rconTest);
        connectionBlock.add(controls, BorderLayout.SOUTH);
        controlStack.add(connectionBlock, BorderLayout.NORTH);

        RoundedPanel test = new RoundedPanel(SURFACE_SOFT);
        test.setLayout(new FlowLayout(FlowLayout.LEFT, 9, 10));
        test.add(label("LOCAL GIFT TEST", 11, GOLD));
        test.add(label("GIFT", 11, MUTED));
        testGiftField = new JTextField("Donut", 14);
        styleField(testGiftField);
        test.add(testGiftField);
        test.add(label("VIEWER", 11, MUTED));
        testViewerField = new JTextField("testuser", 12);
        styleField(testViewerField);
        test.add(testViewerField);
        JButton sendTest = accentButton("Run effect", GOLD);
        sendTest.addActionListener(event -> handleGift(testGiftField.getText(), testViewerField.getText(), "local test"));
        test.add(sendTest);
        controlStack.add(test, BorderLayout.CENTER);
        right.add(controlStack, BorderLayout.CENTER);

        activity = new JTextArea();
        activity.setEditable(false);
        activity.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        styleEditor(activity);
        activity.setRows(13);
        JPanel activityPanel = new JPanel(new BorderLayout(0, 7));
        activityPanel.setOpaque(false);
        activityPanel.add(sectionHeader("ACTIVITY FEED", "Webhook events and server replies"), BorderLayout.NORTH);
        activityPanel.add(scroll(activity), BorderLayout.CENTER);
        right.add(activityPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.48);
        split.setBorder(null);
        split.setDividerSize(8);
        split.setBackground(BACKGROUND);
        root.add(split, BorderLayout.CENTER);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        log("Webhook URL: http://127.0.0.1:" + WEBHOOK_PORT + "/eastok/gift");
        log("Use EasTok HTTP Webhook with value1={username}, value3={giftname}.");
    }

    private JButton windowButton(String symbol, String tooltip, Color hoverColor, Runnable action) {
        WindowControlButton button = new WindowControlButton(symbol, hoverColor);
        button.setToolTipText(tooltip);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void toggleMaximize() {
        if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            frame.setExtendedState(Frame.NORMAL);
        } else {
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    private void makeDraggable(JPanel header) {
        final Point[] pressPoint = new Point[1];
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                pressPoint[0] = event.getPoint();
            }
        });
        header.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (pressPoint[0] == null || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                    return;
                }
                Point window = frame.getLocation();
                frame.setLocation(window.x + event.getX() - pressPoint[0].x, window.y + event.getY() - pressPoint[0].y);
            }
        });
    }

    private RoundedPanel card() {
        return new RoundedPanel(SURFACE);
    }

    private JPanel sectionHeader(String title, String description) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.add(label(title, 13, TEXT), BorderLayout.NORTH);
        panel.add(label(description, 11, MUTED), BorderLayout.SOUTH);
        return panel;
    }

    private JLabel label(String text, int size, Color color) {
        JLabel value = new JLabel(text);
        value.setFont(new Font("Bahnschrift", Font.BOLD, size));
        value.setForeground(color);
        return value;
    }

    private JLabel pill(String text, Color color) {
        return new PulsingPill(text, color);
    }

    private JPanel transparentFlow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        return panel;
    }

    private JScrollPane scroll(JTextArea area) {
        JScrollPane pane = new JScrollPane(area);
        pane.setBorder(BorderFactory.createLineBorder(new Color(51, 61, 75), 1, true));
        pane.getViewport().setBackground(new Color(16, 20, 27));
        pane.getVerticalScrollBar().setUnitIncrement(14);
        return pane;
    }

    private void styleEditor(JTextArea area) {
        area.setBackground(new Color(16, 20, 27));
        area.setForeground(TEXT);
        area.setCaretColor(GOLD);
        area.setSelectionColor(new Color(105, 43, 45));
        area.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));
        area.setLineWrap(false);
    }

    private void styleField(JTextField field) {
        field.setFont(UI_FONT);
        field.setBackground(new Color(16, 20, 27));
        field.setForeground(TEXT);
        field.setCaretColor(GOLD);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(62, 73, 89), 1, true),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
    }

    private JButton accentButton(String text, Color color) {
        return new GlowButton(text, color, BACKGROUND);
    }

    private JButton secondaryButton(String text) {
        return new GlowButton(text, new Color(75, 90, 109), TEXT);
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private float hover;
        private float hoverTarget;
        private final Timer animation;

        private RoundedPanel(Color fill) {
            this.fill = fill;
            setOpaque(false);
            animation = new Timer(16, event -> {
                hover += (hoverTarget - hover) * 0.18f;
                repaint();
            });
            animation.start();
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hoverTarget = 1f;
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hoverTarget = 0f;
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(mix(fill, new Color(42, 48, 61), hover * 0.45f));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.setColor(mix(new Color(66, 76, 92), RED, hover * 0.35f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class GradientPanel extends JPanel {
        private GradientPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, new Color(55, 23, 28), getWidth(), getHeight(), new Color(30, 34, 48)));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g2.setColor(new Color(130, 57, 54));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class AnimatedBackgroundPanel extends JPanel {
        private float phase;

        private AnimatedBackgroundPanel() {
            setOpaque(true);
            new Timer(16, event -> {
                phase += 0.012f;
                repaint();
            }).start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < 12; i++) {
                float x = (float) ((Math.sin(phase + i * 1.71) * 0.44 + 0.5) * getWidth());
                float y = (float) ((Math.cos(phase * 0.72 + i * 1.31) * 0.42 + 0.5) * getHeight());
                int size = 3 + (i % 4) * 2;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f + (i % 3) * 0.02f));
                g2.setColor(i % 2 == 0 ? RED : GOLD);
                g2.fillOval((int) x, (int) y, size, size);
            }
            g2.dispose();
        }
    }

    private static final class PulsingPill extends JLabel {
        private float phase;

        private PulsingPill(String text, Color color) {
            super(text);
            setFont(new Font("Bahnschrift", Font.BOLD, 11));
            setForeground(color);
            setBorder(BorderFactory.createEmptyBorder(7, 22, 7, 11));
            setOpaque(false);
            new Timer(16, event -> {
                phase += 0.08f;
                repaint();
            }).start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            float pulse = (float) ((Math.sin(phase) + 1.0) / 2.0);
            Color foreground = getForeground();
            g2.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 28 + (int) (pulse * 20)));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 115 + (int) (pulse * 60)));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.fillOval(10, getHeight() / 2 - 3, 6, 6);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class GlowButton extends JButton {
        private final Color accent;
        private final Color textColor;
        private float hover;
        private float hoverTarget;

        private GlowButton(String text, Color accent, Color textColor) {
            super(text);
            this.accent = accent;
            this.textColor = textColor;
            setFont(new Font("Bahnschrift", Font.BOLD, 12));
            setForeground(textColor);
            setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hoverTarget = 1f;
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hoverTarget = 0f;
                }
            });
            new Timer(16, event -> {
                hover += (hoverTarget - hover) * 0.22f;
                repaint();
            }).start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color start = mix(accent, Color.WHITE, hover * 0.18f);
            Color end = mix(accent.darker(), BACKGROUND, hover * 0.28f);
            g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(new Color(TEXT.getRed(), TEXT.getGreen(), TEXT.getBlue(), 55 + (int) (hover * 110)));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class WindowControlButton extends JButton {
        private final Color hoverColor;
        private float hover;
        private float hoverTarget;

        private WindowControlButton(String symbol, Color hoverColor) {
            super(symbol);
            this.hoverColor = hoverColor;
            setFont(new Font("Segoe UI Symbol", Font.PLAIN, 17));
            setForeground(TEXT);
            setPreferredSize(new Dimension(34, 30));
            setBorder(BorderFactory.createEmptyBorder());
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hoverTarget = 1f;
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hoverTarget = 0f;
                }
            });
            new Timer(16, event -> {
                hover += (hoverTarget - hover) * 0.26f;
                repaint();
            }).start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(hoverColor.getRed(), hoverColor.getGreen(), hoverColor.getBlue(), (int) (hover * 215)));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static Color mix(Color from, Color to, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        return new Color(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * value),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * value),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * value)
        );
    }

    private void startWebhook() {
        try {
            webhook = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), WEBHOOK_PORT), 0);
            webhook.createContext("/eastok/gift", this::handleWebhook);
            webhook.createContext("/eastok/chat", this::handleChatWebhook);
            webhook.start();
            setStatus("Webhook ready on port " + WEBHOOK_PORT, new Color(40, 130, 70));
            log("Webhook is listening for EasTok events.");
        } catch (IOException exception) {
            setStatus("Webhook failed: " + exception.getMessage(), new Color(185, 45, 45));
            log("Webhook could not start: " + exception.getMessage());
        }
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendHttp(exchange, 405, "POST only");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = parseJsonValues(body);
        String viewer = firstValue(values, "username", "user", "value1");
        String gift = firstValue(values, "giftname", "gift", "value3");
        if (gift.isBlank()) {
            sendHttp(exchange, 400, "Gift name is missing");
            log("Webhook rejected: gift name is missing. Body: " + trim(body, 180));
            return;
        }
        if (isTemplateValue(gift)) {
            sendHttp(exchange, 200, "test accepted");
            log("EasTok template test received (" + gift + "). No Minecraft effect was run. Use Local gift test for a real effect.");
            return;
        }
        sendHttp(exchange, 200, "ok");
        handleGift(gift, viewer.isBlank() ? "TikTok viewer" : viewer, "EasTok webhook");
    }

    private void handleChatWebhook(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendHttp(exchange, 405, "POST only");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = parseJsonValues(body);
        String viewer = firstValue(values, "username", "user", "value1", "nickname");
        String message = firstValue(values, "message", "comment", "value2");
        if (message.isBlank()) {
            sendHttp(exchange, 400, "Chat message is missing");
            log("Chat webhook rejected: message is missing. Body: " + trim(body, 180));
            return;
        }
        if (isTemplateValue(message)) {
            sendHttp(exchange, 200, "test accepted");
            log("EasTok chat template test received (" + message + "). No Minecraft chat message was sent.");
            return;
        }
        sendHttp(exchange, 200, "ok");
        handleChat(viewer.isBlank() ? "TikTok viewer" : viewer, message);
    }

    private void handleGift(String giftName, String viewer, String source) {
        String effect = resolveEffect(giftName);
        String player = sanitizePlayer(playerField == null ? "motyasm" : playerField.getText());
        String sender = sanitizeViewer(viewer);
        String command = "fti app " + effect + " " + player + " " + sender + " \"" + escapeCommand(giftName) + "\"";
        log(source + ": " + giftName + " from " + viewer + " -> " + effect);
        new Thread(() -> {
            try {
                String response = new RconClient("127.0.0.1", rconPort, rconPassword).command(command);
                log("Minecraft: " + trim(response, 180));
                setConnection("RCON: connected", new Color(40, 130, 70));
            } catch (Exception exception) {
                log("Minecraft command failed: " + exception.getMessage());
                setConnection("RCON: failed", new Color(185, 45, 45));
            }
        }, "funtime-rcon").start();
    }

    private void handleChat(String viewer, String message) {
        String command = "fti chat \"" + escapeCommand(viewer) + "\" \"" + escapeCommand(message) + "\"";
        log("EasTok chat: " + viewer + " » " + trim(message, 140));
        new Thread(() -> {
            try {
                String response = new RconClient("127.0.0.1", rconPort, rconPassword).command(command);
                log("Minecraft chat: " + trim(response, 180));
                setConnection("RCON: connected", new Color(40, 130, 70));
            } catch (Exception exception) {
                log("Minecraft chat failed: " + exception.getMessage());
                setConnection("RCON: failed", new Color(185, 45, 45));
            }
        }, "funtime-chat").start();
    }

    private void testRcon() {
        new Thread(() -> {
            try {
                String response = new RconClient("127.0.0.1", rconPort, rconPassword).command("list");
                log("RCON test: " + trim(response, 180));
                setConnection("RCON: connected", new Color(40, 130, 70));
            } catch (Exception exception) {
                log("RCON test failed: " + exception.getMessage());
                setConnection("RCON: failed", new Color(185, 45, 45));
            }
        }, "funtime-rcon-test").start();
    }

    private void loadServerConnection() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(serverPropertiesPath)) {
            properties.load(input);
            rconPassword = properties.getProperty("rcon.password", "");
            rconPort = Integer.parseInt(properties.getProperty("rcon.port", "25575"));
        } catch (Exception exception) {
            rconPassword = "";
            log("Cannot load RCON settings: " + exception.getMessage());
        }
    }

    private void loadAppSettings() {
        if (!Files.exists(APP_SETTINGS_FILE)) {
            return;
        }
        try (InputStream input = Files.newInputStream(APP_SETTINGS_FILE)) {
            Properties properties = new Properties();
            properties.load(input);
            String path = properties.getProperty("server.properties.path", "").trim();
            if (!path.isBlank()) {
                serverPropertiesPath = Paths.get(path);
            }
            configuredPlayer = properties.getProperty("minecraft.player", configuredPlayer).trim();
        } catch (IOException exception) {
            log("Could not load app settings: " + exception.getMessage());
        }
    }

    private void reloadServerSettings() {
        try {
            serverPropertiesPath = Paths.get(serverPropertiesField.getText().trim());
            configuredPlayer = sanitizePlayer(playerField.getText());
            loadServerConnection();
            saveAppSettings();
            setConnection("RCON: settings loaded", new Color(105, 150, 205));
            log("Server settings loaded from " + serverPropertiesPath);
        } catch (Exception exception) {
            log("Could not load server settings: " + exception.getMessage());
            setConnection("RCON: settings error", new Color(185, 45, 45));
        }
    }

    private void saveAppSettings() {
        Properties properties = new Properties();
        properties.setProperty("server.properties.path", serverPropertiesPath.toString());
        properties.setProperty("minecraft.player", configuredPlayer);
        try (OutputStream output = Files.newOutputStream(APP_SETTINGS_FILE)) {
            properties.store(output, "FunTime Live local settings");
        } catch (IOException exception) {
            log("Could not save app settings: " + exception.getMessage());
        }
    }

    private void loadGiftMap() {
        loadDefaultMap();
        if (!Files.exists(MAP_FILE)) {
            return;
        }
        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(MAP_FILE)) {
                properties.load(input);
            }
            for (String key : properties.stringPropertyNames()) {
                giftMap.put(normalize(key), properties.getProperty(key).trim());
            }
        } catch (IOException exception) {
            log("Could not load gift map: " + exception.getMessage());
        }
    }

    private void loadDefaultMap() {
        giftMap.clear();
        if (loadRegionalGiftMap()) {
            return;
        }
        giftMap.put("rose", "rose");
        giftMap.put("роза", "rose");
        // Exact English names from the installed EasTok/TikTok gift catalogue.
        giftMap.put("doughnut", "donut");
        giftMap.put("doughut", "donut");
        giftMap.put("donut", "donut");
        giftMap.put("пончик", "donut");
        giftMap.put("heart me", "heal");
        giftMap.put("сердце", "heal");
        giftMap.put("gg", "diamond");
        giftMap.put("coffee", "speed");
        giftMap.put("кофе", "speed");
        giftMap.put("ice cream cone", "freeze");
        giftMap.put("ice cream", "freeze");
        giftMap.put("мороженое", "freeze");
        giftMap.put("sunglasses", "blind");
        giftMap.put("glasses", "blind");
        giftMap.put("очки", "blind");
        giftMap.put("gold microphone", "raid");
        giftMap.put("microphone", "raid");
        giftMap.put("микрофон", "raid");
        giftMap.put("panda", "animal");
        giftMap.put("панда", "animal");
        giftMap.put("cupcake", "creeper");
        giftMap.put("капкейк", "creeper");
        giftMap.put("rocket", "rocket");
        giftMap.put("ракета", "rocket");
        giftMap.put("crown", "crusher");
        giftMap.put("корона", "crusher");
        giftMap.put("whale", "trap");
        giftMap.put("кит", "trap");
        giftMap.put("lion", "dragon");
        giftMap.put("лев", "dragon");
        giftMap.put("galaxy", "chaos");
        giftMap.put("planet", "meteor");
        giftMap.put("планета", "meteor");
        giftMap.put("universe", "chaos");
        giftMap.put("вселенная", "chaos");
        giftMap.put("tornado", "tornado");
        giftMap.put("торнадо", "tornado");
        giftMap.put("firework", "fireworks");
        giftMap.put("фейерверк", "fireworks");
        giftMap.put("bone", "wolves");
        giftMap.put("кость", "wolves");
        giftMap.put("ghost", "ghost");
        giftMap.put("призрак", "ghost");
        giftMap.put("skull", "wither");
        giftMap.put("череп", "wither");
    }

    private boolean loadRegionalGiftMap() {
        // Real gift names from the user's current EasTok regional catalogue.
        giftMap.put("heart me", "heal");
        giftMap.put("gg", "diamond");
        giftMap.put("coffee", "speed");
        giftMap.put("ice cream cone", "freeze");
        giftMap.put("rose", "rose");
        giftMap.put("creeper", "creeper");
        giftMap.put("wave firework", "fireworks");
        giftMap.put("choc chip cookie", "animal");
        giftMap.put("doughnut", "donut");
        giftMap.put("single strike", "rocket");
        giftMap.put("little crown", "crusher");
        giftMap.put("cursed kick", "trap");
        giftMap.put("fairy hide", "ghost");
        giftMap.put("mishka bear", "wolves");
        giftMap.put("sunglasses", "blind");
        giftMap.put("gold microphone", "raid");
        giftMap.put("triple thunder", "tornado");
        giftMap.put("meteor shower", "meteor");
        giftMap.put("dragon crown", "dragon");
        giftMap.put("viking hammer", "wither");
        giftMap.put("galaxy", "chaos");
        return true;
    }

    private void saveGiftMap() {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String line : mapArea.getText().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            String gift = normalize(parts[0]);
            String effect = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!gift.isBlank() && effect.matches("[a-z_]+")) {
                parsed.put(gift, effect);
            }
        }
        if (parsed.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Add at least one line: gift name=effect", "Empty map", JOptionPane.WARNING_MESSAGE);
            return;
        }
        giftMap.clear();
        giftMap.putAll(parsed);
        Properties properties = new Properties();
        properties.putAll(giftMap);
        try (OutputStream output = Files.newOutputStream(MAP_FILE)) {
            properties.store(output, "FunTime Live gift map");
            mapArea.setText(mapText());
            log("Gift map saved: " + giftMap.size() + " entries.");
        } catch (IOException exception) {
            log("Could not save gift map: " + exception.getMessage());
        }
    }

    private String resolveEffect(String giftName) {
        String normalized = normalize(giftName);
        for (Map.Entry<String, String> entry : giftMap.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        String[] fallback = {"loot", "diamond", "heal", "speed", "fireworks", "wolves"};
        return fallback[Math.floorMod(normalized.hashCode() ^ random.nextInt(1), fallback.length)];
    }

    private String mapText() {
        StringBuilder text = new StringBuilder("# One line: TikTok gift name=FunTime effect\n");
        giftMap.forEach((gift, effect) -> text.append(gift).append('=').append(effect).append('\n'));
        return text.toString();
    }

    private Map<String, String> parseJsonValues(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = JSON_VALUE.matcher(body);
        while (matcher.find()) {
            values.put(matcher.group(1).toLowerCase(Locale.ROOT), unescapeJson(matcher.group(2)));
        }
        return values;
    }

    private String firstValue(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void sendHttp(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void setStatus(String text, Color color) {
        if (status != null) {
            SwingUtilities.invokeLater(() -> {
                status.setText(text);
                status.setForeground(color);
            });
        }
    }

    private void setConnection(String text, Color color) {
        if (connection != null) {
            SwingUtilities.invokeLater(() -> {
                connection.setText(text);
                connection.setForeground(color);
            });
        }
    }

    private void log(String message) {
        String line = "[" + LocalTime.now().format(CLOCK) + "] " + message;
        if (activity == null) {
            System.out.println(line);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            activity.append(line + "\n");
            activity.setCaretPosition(activity.getDocument().getLength());
        });
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    private static String sanitizePlayer(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? "motyasm" : sanitized;
    }

    private static String sanitizeViewer(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]", "");
        return sanitized.isBlank() ? "TikTok" : sanitized;
    }

    private static String escapeCommand(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\");
    }

    private static boolean isTemplateValue(String value) {
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("{{") && trimmed.endsWith("}}"));
    }

    private static String trim(String value, int maxLength) {
        String oneLine = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
        return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength) + "...";
    }

    private static final class RconClient {
        private static final int AUTH = 3;
        private static final int COMMAND = 2;
        private final String host;
        private final int port;
        private final String password;

        private RconClient(String host, int port, String password) {
            this.host = host;
            this.port = port;
            this.password = password;
        }

        private String command(String command) throws IOException {
            if (password == null || password.isBlank()) {
                throw new IOException("RCON password is missing in server.properties");
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(5000);
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                int authId = 101;
                sendPacket(output, authId, AUTH, password);
                Packet auth = readPacket(input);
                if (auth.id != authId) {
                    throw new IOException("RCON authentication failed");
                }
                int commandId = 102;
                sendPacket(output, commandId, COMMAND, command);
                Packet response = readPacket(input);
                if (response.id != commandId) {
                    throw new IOException("Unexpected RCON response");
                }
                return response.body;
            }
        }

        private static void sendPacket(OutputStream output, int id, int type, String body) throws IOException {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(bodyBytes.length + 14).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(bodyBytes.length + 10);
            buffer.putInt(id);
            buffer.putInt(type);
            buffer.put(bodyBytes);
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            output.write(buffer.array());
            output.flush();
        }

        private static Packet readPacket(InputStream input) throws IOException {
            byte[] lengthBytes = input.readNBytes(4);
            if (lengthBytes.length != 4) {
                throw new IOException("RCON connection closed");
            }
            int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (length < 10 || length > 1024 * 1024) {
                throw new IOException("Invalid RCON packet length");
            }
            byte[] payload = input.readNBytes(length);
            if (payload.length != length) {
                throw new IOException("Incomplete RCON packet");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            int id = buffer.getInt();
            buffer.getInt();
            byte[] body = new byte[length - 10];
            buffer.get(body);
            return new Packet(id, new String(body, StandardCharsets.UTF_8));
        }

        private record Packet(int id, String body) {
        }
    }
}
