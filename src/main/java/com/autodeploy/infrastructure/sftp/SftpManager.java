package com.autodeploy.infrastructure.sftp;
import com.autodeploy.domain.model.Server;
import com.jcraft.jsch.*;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

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

        session.connect();

        // Open SFTP channel
        Channel channel = session.openChannel("sftp");
        channel.connect();
        sftpChannel = (ChannelSftp) channel;

        System.out.println("✓ SFTP connected to: " + server.getHost());

        // Start connection monitoring
        startConnectionMonitoring();
    }

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
        System.out.println("SFTP disconnected from: " + server.getHost());
    }

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

                        break;
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

    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        this.statusListener = listener;
    }

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

    public void downloadFile(String remotePath, String localPath) throws SftpException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server");
        }

        System.out.println("📥 Downloading: " + remotePath + " → " + localPath);

        sftpChannel.get(remotePath, localPath);

        System.out.println("✓ Downloaded successfully");
    }

    public String executeCommand(String command) throws Exception {
        System.out.println("SftpManager.executeCommand called with: " + command);

        if (session == null || !session.isConnected()) {
            throw new Exception("SSH session not connected");
        }

        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

            channel.setOutputStream(outputStream);
            channel.setErrStream(errorStream);

            channel.connect();

            // Wait for command to complete
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            String output = outputStream.toString("UTF-8");
            String error = errorStream.toString("UTF-8");

            System.out.println("Command exit code: " + exitCode);
            System.out.println("Command stdout: " + output);
            System.out.println("Command stderr: " + error);

            if (exitCode != 0 && !error.isEmpty()) {
                throw new Exception("Command failed with exit code " + exitCode + ": " + error);
            }

            return output;

        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    public ChannelSftp getSftpChannel() {
        return sftpChannel;
    }

    public Server getServer() {
        return server;
    }

    public interface ConnectionStatusListener {
        void onConnectionLost();
    }
}