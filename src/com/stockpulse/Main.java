package com.stockpulse;

import com.stockpulse.config.DBConnection;
import com.stockpulse.server.ApiHandler;
import com.stockpulse.server.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.Executors;

/**
 * Main entrypoint for StockPulse (Core Java + JDBC + MySQL + HTML/CSS/JS, zero Maven).
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  StockPulse - Java Full Stack Inventory System");
        System.out.println("  Core Java + JDBC + MySQL (No Maven)");
        System.out.println("====================================================");

        try {
            System.out.println("[1/3] Initializing MySQL database & schema via JDBC...");
            DBConnection.initializeDatabase();
            System.out.println("[OK]  MySQL connection verified and schema is ready.");
        } catch (SQLException e) {
            System.err.println("[WARN] Could not initialize MySQL on startup: " + e.getMessage());
            System.err.println("       Check config/db.properties and ensure MySQL Server is running.");
        }

        int port = DBConnection.getServerPort();
        try {
            System.out.println("[2/3] Starting Core Java HTTP server on port " + port + "...");
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/", new ApiHandler());
            server.createContext("/", new StaticFileHandler("web"));
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();

            System.out.println("[3/3] Server is LIVE!");
            System.out.println("      Open in browser: http://localhost:" + port);
            System.out.println("      Press Ctrl+C to stop.");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to start HTTP server on port " + port + ": " + e.getMessage());
        }
    }
}
