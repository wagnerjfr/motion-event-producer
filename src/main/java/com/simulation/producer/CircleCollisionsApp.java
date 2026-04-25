package com.simulation.producer;

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
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Geometry;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CircleCollisionsApp extends Application {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final double SCALE = 60.0; // pixels per meter

    private static final int INITIAL_CIRCLES = 40;
    private static final double MIN_RADIUS_PX = 8;
    private static final double MAX_RADIUS_PX = 22;

    private final Random random = new Random();
    private final List<Body> circles = new ArrayList<>();
    private final List<Color> circleColors = new ArrayList<>();

    private World<Body> world;
    private boolean paused;
    private Slider gravitySlider;

    @Override
    public void start(Stage stage) {
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
        stage.setTitle("JavaFX + dyn4j Circle Collisions");
        stage.setScene(scene);
        stage.show();

        createWorld();
        spawnInitialCircles(INITIAL_CIRCLES);

        pauseResume.setOnAction(e -> {
            paused = !paused;
            pauseResume.setText(paused ? "Resume" : "Pause");
        });

        reset.setOnAction(e -> {
            paused = false;
            pauseResume.setText("Pause");
            createWorld();
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
                }

                render(gc);
            }
        };
        timer.start();
    }

    private void createWorld() {
        world = new World<>();
        world.setGravity(new Vector2(0, 9.8));
        circles.clear();
        circleColors.clear();

        double thicknessPx = 30;

        // floor
        addStaticWall(WIDTH / 2.0, HEIGHT + thicknessPx / 2.0, WIDTH, thicknessPx);
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
    }

    private void render(GraphicsContext gc) {
        gc.setFill(Color.rgb(18, 22, 28));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

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
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}