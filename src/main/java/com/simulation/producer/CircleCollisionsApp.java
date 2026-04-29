package com.simulation.producer;

import com.simulation.producer.emitter.EventEmitter;
import com.simulation.producer.events.CollisionEvent;
import com.simulation.producer.events.PositionEvent;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Geometry;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.dyn4j.world.listener.ContactListenerAdapter;
import org.dyn4j.world.ContactCollisionData;
import org.dyn4j.dynamics.contact.Contact;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class CircleCollisionsApp extends Application {
    private static final DateTimeFormatter SESSION_RUN_STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final String WINDOW_TITLE_PREFIX = "JavaFX + dyn4j Circle Collisions";

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final double SCALE = 60.0; // pixels per meter

    private static final int INITIAL_CIRCLES = 40;
    private static final double MIN_RADIUS_PX = 8;
    private static final double MAX_RADIUS_PX = 22;
    private static final double FLOOR_VISUAL_HEIGHT_PX = 26;
    private static final long POSITION_EMIT_INTERVAL_MS = 100;
    private static final double MIN_SPEED_TO_EMIT = 0.06; // m/s
    private static final double MIN_POSITION_DELTA_TO_EMIT = 0.015; // meters
    private static final long IDLE_HEARTBEAT_EMIT_INTERVAL_MS = 3000;

    private final Random random = new Random();
    private final List<Body> circles = new ArrayList<>();
    private final List<Color> circleColors = new ArrayList<>();
    private final Map<Body, String> ballIds = new HashMap<>();
    private final Map<String, Vector2> lastEmittedPositions = new HashMap<>();
    private final Map<String, Long> lastEmittedAtMs = new HashMap<>();
    private final Set<String> activePairContacts = new HashSet<>();
    private EventEmitter eventEmitter;
    private ConfigurableApplicationContext springContext;

    private int nextBallId = 1;
    private long sessionSequence = 0;
    private String sessionId;
    private String sessionRunStamp;
    private long lastPositionEmitMs = 0;

    private World<Body> world;
    private boolean paused;
    private Slider gravitySlider;

    @Override
    public void start(Stage stage) {
        springContext = new SpringApplicationBuilder(MotionEventProducerApplication.class)
                .headless(false)
                .run();
        eventEmitter = springContext.getBean("eventEmitter", EventEmitter.class);
        sessionRunStamp = LocalDateTime.now().format(SESSION_RUN_STAMP_FORMATTER);

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Button pauseResume = new Button("Pause");
        Button reset = new Button("Reset");
        Button addCircle = new Button("Add Circle");
        gravitySlider = new Slider(2.0, 25.0, 9.8);
        gravitySlider.setShowTickLabels(true);
        gravitySlider.setPrefWidth(200);

        HBox controls = new HBox(10,
                pauseResume,
                reset,
                addCircle,
                new Label("Gravity (m/s²)"),
                gravitySlider
        );
        controls.setPadding(new Insets(10));

        BorderPane root = new BorderPane(canvas);
        root.setTop(controls);

        Scene scene = new Scene(root, WIDTH, HEIGHT + 55);
        stage.setTitle(WINDOW_TITLE_PREFIX);
        stage.setScene(scene);
        stage.show();

        createWorld();
        stage.setTitle(WINDOW_TITLE_PREFIX + " — " + sessionId);
        spawnInitialCircles(INITIAL_CIRCLES);

        pauseResume.setOnAction(e -> {
            paused = !paused;
            pauseResume.setText(paused ? "Resume" : "Pause");
        });

        reset.setOnAction(e -> {
            paused = false;
            pauseResume.setText("Pause");
            createWorld();
            stage.setTitle(WINDOW_TITLE_PREFIX + " — " + sessionId);
            spawnInitialCircles(INITIAL_CIRCLES);
        });

        addCircle.setOnAction(e -> spawnCircle(random.nextDouble() * (WIDTH - 100) + 50, 20));

        AnimationTimer timer = new AnimationTimer() {
            private long lastNanos = -1;

            @Override
            public void handle(long now) {
                if (lastNanos < 0) {
                    lastNanos = now;
                    return;
                }

                double dt = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                dt = Math.min(dt, 1.0 / 30.0); // avoid giant jumps

                world.setGravity(new Vector2(0, gravitySlider.getValue()));

                if (!paused) {
                    world.step(1, dt);
                    emitPositionEventsIfDue();
                }

                render(gc);
            }
        };
        timer.start();
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
    }

    private void createWorld() {
        world = new World<>();
        world.setGravity(new Vector2(0, 9.8));
        world.addContactListener(new ContactListenerAdapter<Body>() {
            @Override
            public void begin(ContactCollisionData<Body> collision, Contact contact) {
                Object aData = collision.getBody1().getUserData();
                Object bData = collision.getBody2().getUserData();
                if (!(aData instanceof String idA) || !(bData instanceof String idB)) {
                    return;
                }

                String pairKey = pairKey(idA, idB);
                if (activePairContacts.contains(pairKey)) {
                    return;
                }
                activePairContacts.add(pairKey);

                Vector2 va = collision.getBody1().getLinearVelocity();
                Vector2 vb = collision.getBody2().getLinearVelocity();
                double rvx = va.x - vb.x;
                double rvy = va.y - vb.y;
                double relativeSpeed = Math.sqrt(rvx * rvx + rvy * rvy);
                Vector2 p = contact.getPoint();

                eventEmitter.emitCollision(new CollisionEvent(
                        sessionId,
                        idA,
                        idB,
                        System.currentTimeMillis(),
                        p.x,
                        p.y,
                        relativeSpeed
                ));
            }

            @Override
            public void end(ContactCollisionData<Body> collision, Contact contact) {
                Object aData = collision.getBody1().getUserData();
                Object bData = collision.getBody2().getUserData();
                if (!(aData instanceof String idA) || !(bData instanceof String idB)) {
                    return;
                }

                activePairContacts.remove(pairKey(idA, idB));
            }
        });
        circles.clear();
        circleColors.clear();
        ballIds.clear();
        lastEmittedPositions.clear();
        lastEmittedAtMs.clear();
        activePairContacts.clear();
        nextBallId = 1;
        sessionId = nextSessionId();
        lastPositionEmitMs = 0;

        double thicknessPx = 30;
        double floorTopPx = HEIGHT - FLOOR_VISUAL_HEIGHT_PX;

        // floor
        addStaticWall(WIDTH / 2.0, floorTopPx + thicknessPx / 2.0, WIDTH, thicknessPx);
        // ceiling
        addStaticWall(WIDTH / 2.0, -thicknessPx / 2.0, WIDTH, thicknessPx);
        // left wall
        addStaticWall(-thicknessPx / 2.0, HEIGHT / 2.0, thicknessPx, HEIGHT);
        // right wall
        addStaticWall(WIDTH + thicknessPx / 2.0, HEIGHT / 2.0, thicknessPx, HEIGHT);
    }

    private void addStaticWall(double cxPx, double cyPx, double widthPx, double heightPx) {
        Body wall = new Body();
        wall.addFixture(Geometry.createRectangle(widthPx / SCALE, heightPx / SCALE));
        wall.translate(cxPx / SCALE, cyPx / SCALE);
        wall.setMass(MassType.INFINITE);
        world.addBody(wall);
    }

    private void spawnInitialCircles(int amount) {
        for (int i = 0; i < amount; i++) {
            double x = 70 + random.nextDouble() * (WIDTH - 140);
            double y = 25 + random.nextDouble() * 200;
            spawnCircle(x, y);
        }
    }

    private void spawnCircle(double xPx, double yPx) {
        double radiusPx = MIN_RADIUS_PX + random.nextDouble() * (MAX_RADIUS_PX - MIN_RADIUS_PX);
        double radiusM = radiusPx / SCALE;

        Body body = new Body();
        BodyFixture fixture = body.addFixture(Geometry.createCircle(radiusM));
        fixture.setDensity(1.0);
        fixture.setFriction(0.2);
        fixture.setRestitution(0.75);

        body.translate(xPx / SCALE, yPx / SCALE);
        body.setLinearVelocity(new Vector2((random.nextDouble() - 0.5) * 2.5, random.nextDouble() * 0.5));
        body.setAngularVelocity((random.nextDouble() - 0.5) * 2.0);
        body.setLinearDamping(0.05);
        body.setAngularDamping(0.03);
        body.setMass(MassType.NORMAL);

        world.addBody(body);
        circles.add(body);
        circleColors.add(Color.hsb(random.nextDouble() * 360, 0.75, 0.95));
        String ballId = "ball-" + nextBallId++;
        ballIds.put(body, ballId);
        body.setUserData(ballId);
    }

    private void emitPositionEventsIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPositionEmitMs < POSITION_EMIT_INTERVAL_MS) {
            return;
        }
        lastPositionEmitMs = now;

        for (Body body : circles) {
            String ballId = ballIds.get(body);
            if (ballId == null) {
                continue;
            }

            Vector2 p = body.getWorldCenter();
            Vector2 v = body.getLinearVelocity();

            double speed = Math.hypot(v.x, v.y);
            Vector2 lastEmittedPosition = lastEmittedPositions.get(ballId);
            long lastEmittedAt = lastEmittedAtMs.getOrDefault(ballId, 0L);
            boolean heartbeatDue = now - lastEmittedAt >= IDLE_HEARTBEAT_EMIT_INTERVAL_MS;
            boolean movedEnough = lastEmittedPosition == null
                    || p.distance(lastEmittedPosition) >= MIN_POSITION_DELTA_TO_EMIT;
            boolean movingEnough = speed >= MIN_SPEED_TO_EMIT;

            if (!movingEnough && !movedEnough && !heartbeatDue) {
                continue;
            }

            eventEmitter.emitPosition(new PositionEvent(
                    sessionId,
                    ballId,
                    now,
                    p.x,
                    p.y,
                    v.x,
                    v.y
            ));
            lastEmittedPositions.put(ballId, new Vector2(p.x, p.y));
            lastEmittedAtMs.put(ballId, now);
        }
    }

    private String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private String nextSessionId() {
        sessionSequence++;
        return "session-" + sessionRunStamp + "-" + sessionSequence;
    }

    private void render(GraphicsContext gc) {
        gc.setFill(Color.rgb(18, 22, 28));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // visible ground (physics floor already exists in the world)
        double floorY = HEIGHT - FLOOR_VISUAL_HEIGHT_PX;
        gc.setFill(Color.rgb(55, 60, 68));
        gc.fillRect(0, floorY, WIDTH, FLOOR_VISUAL_HEIGHT_PX);
        gc.setStroke(Color.rgb(150, 158, 172, 0.75));
        gc.strokeLine(0, floorY, WIDTH, floorY);

        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = 0; i < circles.size(); i++) {
            Body body = circles.get(i);
            double radiusM = body.getFixtures().get(0).getShape().getRadius();
            double radiusPx = radiusM * SCALE;

            Vector2 center = body.getWorldCenter();
            double x = center.x * SCALE;
            double y = center.y * SCALE;

            gc.setFill(circleColors.get(i));
            gc.fillOval(x - radiusPx, y - radiusPx, radiusPx * 2, radiusPx * 2);

            gc.setStroke(Color.rgb(255, 255, 255, 0.2));
            gc.strokeOval(x - radiusPx, y - radiusPx, radiusPx * 2, radiusPx * 2);

            String ballId = ballIds.getOrDefault(body, "");
            String label = ballId.startsWith("ball-") ? ballId.substring("ball-".length()) : ballId;

            double fontSize = Math.max(10, Math.min(16, radiusPx * 0.9));
            gc.setFont(Font.font("System", FontWeight.BOLD, fontSize));

            double baselineY = y + (fontSize * 0.35);

            gc.setFill(Color.rgb(0, 0, 0, 0.45));
            gc.fillText(label, x + 1.5, baselineY + 1.5);

            gc.setFill(Color.WHITE);
            gc.fillText(label, x, baselineY);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}