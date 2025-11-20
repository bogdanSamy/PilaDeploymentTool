/*
 * Copyright © 2024. XTREME SOFTWARE SOLUTIONS
 *
 * All rights reserved. Unauthorized use, reproduction, or distribution
 * of this software or any portion of it is strictly prohibited and may
 * result in severe civil and criminal penalties. This code is the sole
 * proprietary of XTREME SOFTWARE SOLUTIONS.
 *
 * Commercialization, redistribution, and use without explicit permission
 * from XTREME SOFTWARE SOLUTIONS, are expressly forbidden.
 */

package com.autodeploy.sftp;

import com.autodeploy.model.Server;
import com.jcraft.jsch.*;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SFTP Manager for handling server connections
 *
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class SftpManager {

    private Session session;
    private ChannelSftp sftpChannel;
    private final Server server;
    private Thread keepAliveThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ConnectionStatusListener statusListener;

    public SftpManager(Server server) {
        this.server = server;
    }

    /**
     * Connect to the server via SFTP
     */
    public void connect() throws JSchException {
        JSch jsch = new JSch();

        // Disable Kerberos globally
        JSch.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey");

        session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
        session.setPassword(server.getPassword());

        // Configuration properties
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password,keyboard-interactive");
        config.put("userauth.gssapi-with-mic", "no");
        config.put("GSSAPIAuthentication", "no");
        config.put("GSSAPIDelegateCredentials", "no");
        config.put("GSSAPIKeyExchange", "no");
        config.put("GSSAPITrustDNS", "no");

        session.setConfig(config);
        session.setTimeout(30000); // 30 seconds

        // Enable keep-alive
        session.setServerAliveInterval(5000); // Send keep-alive every 5 seconds
        session.setServerAliveCountMax(3); // Max 3 failed keep-alives before disconnect

        System.out.println("→ Connecting to: " + server.getHost() + ":" + server.getPort());
        System.out.println("→ Username: " + server.getUsername());

        // Connect
        session.connect();

        // Open SFTP channel
        Channel channel = session.openChannel("sftp");
        channel.connect();
        sftpChannel = (ChannelSftp) channel;

        System.out.println("✓ SFTP connected to: " + server.getHost());

        // Start connection monitoring
        startConnectionMonitoring();
    }

    /**
     * Disconnect from the server
     */
    public void disconnect() {
        running.set(false);

        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
        }

        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        System.out.println("✓ SFTP disconnected from: " + server.getHost());
    }

    /**
     * Check if connected (with actual test)
     */
    public boolean isConnected() {
        try {
            if (session == null || !session.isConnected()) {
                return false;
            }
            if (sftpChannel == null || !sftpChannel.isConnected()) {
                return false;
            }

            // Test connection by checking current directory
            sftpChannel.pwd();
            return true;

        } catch (Exception e) {
            System.err.println("⚠ Connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Start connection monitoring thread
     */
    private void startConnectionMonitoring() {
        running.set(true);

        keepAliveThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(5000); // Check every 5 seconds

                    if (!isConnected()) {
                        System.err.println("⚠ Connection lost to: " + server.getHost());

                        // Notify listener
                        if (statusListener != null) {
                            statusListener.onConnectionLost();
                        }

                        break; // Exit monitoring loop
                    }

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("✗ Error in connection monitoring: " + e.getMessage());
                }
            }
        }, "SFTP-KeepAlive");

        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
    }

    /**
     * Set connection status listener
     */
    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        this.statusListener = listener;
    }

    /**
     * Upload file to server (creates parent directories if needed)
     */
    public void uploadFile(String localPath, String remotePath) throws SftpException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server");
        }

        // Create parent directories if they don't exist
        String remoteDir = remotePath.substring(0, remotePath.lastIndexOf('/'));
        createRemoteDirectory(remoteDir);

        // Upload file
        sftpChannel.put(localPath, remotePath);
        System.out.println("✓ Uploaded: " + localPath + " → " + remotePath);
    }

    /**
     * Create remote directory (recursive)
     */
    private void createRemoteDirectory(String path) {
        try {
            sftpChannel.cd(path);
        } catch (SftpException e) {
            // Directory doesn't exist, create it
            String[] folders = path.split("/");
            StringBuilder currentPath = new StringBuilder();

            for (String folder : folders) {
                if (folder.isEmpty()) continue;

                currentPath.append("/").append(folder);
                try {
                    sftpChannel.cd(currentPath.toString());
                } catch (SftpException ex) {
                    try {
                        sftpChannel.mkdir(currentPath.toString());
                        sftpChannel.cd(currentPath.toString());
                    } catch (SftpException ex2) {
                        // Ignore if directory already exists
                    }
                }
            }
        }
    }

    /**
     * Download file from server
     */
    public void downloadFile(String remotePath, String localPath) throws SftpException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server");
        }

        System.out.println("📥 Downloading: " + remotePath + " → " + localPath);

        // Download file
        sftpChannel.get(remotePath, localPath);

        System.out.println("✓ Downloaded successfully");
    }

    /**
     * Execute remote command via SSH
     */
    public String executeCommand(String command) throws JSchException, java.io.IOException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server");
        }

        ChannelExec execChannel = (ChannelExec) session.openChannel("exec");
        execChannel.setCommand(command);

        InputStream in = execChannel.getInputStream();
        execChannel.connect();

        StringBuilder output = new StringBuilder();
        byte[] tmp = new byte[1024];
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                output.append(new String(tmp, 0, i));
            }
            if (execChannel.isClosed()) {
                if (in.available() > 0) continue;
                break;
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                // Ignore
            }
        }

        execChannel.disconnect();
        return output.toString();
    }

    /**
     * Get SFTP channel
     */
    public ChannelSftp getSftpChannel() {
        return sftpChannel;
    }

    /**
     * Get server info
     */
    public Server getServer() {
        return server;
    }

    /**
     * Connection status listener interface
     */
    public interface ConnectionStatusListener {
        void onConnectionLost();
    }
}