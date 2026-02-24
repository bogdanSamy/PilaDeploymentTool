package com.autodeploy.infrastructure.sftp;

import com.autodeploy.domain.model.Server;
import com.jcraft.jsch.*;

import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper thread-safe peste JSch pentru operații SSH/SFTP.
 * <p>
 * Funcționalitate:
 * <ul>
 *   <li>Upload/download fișiere prin SFTP</li>
 *   <li>Execuție comenzi remote prin SSH (canal exec separat)</li>
 *   <li>Monitoring activ al conexiunii — detectează pierderea și notifică</li>
 * </ul>
 * <p>
 * <b>Thread safety:</b> Toate operațiile SFTP (put, get, cd, mkdir) sunt serializate
 * prin {@code sftpLock}. JSch {@link ChannelSftp} NU este thread-safe — accesul
 * concurent corup starea internă a canalului. Comenzile SSH (exec) folosesc
 * canale separate per-execuție și nu necesită lock.
 * <p>
 * <b>Lifecycle:</b> Instanțele sunt create și distruse de {@link com.autodeploy.infrastructure.connection.ConnectionManager}.
 * La reconectare se creează un SftpManager complet nou.
 */
public class SftpManager {

    private static final Logger LOGGER = Logger.getLogger(SftpManager.class.getName());

    private static final int CONNECTION_TIMEOUT_MS = 30_000;
    private static final int KEEP_ALIVE_INTERVAL_MS = 5_000;
    private static final int KEEP_ALIVE_MAX_FAILURES = 3;
    private static final int MONITOR_INTERVAL_MS = 5_000;
    private static final int COMMAND_TIMEOUT_MS = 30_000;

    private Session session;
    private ChannelSftp sftpChannel;
    private final Server server;
    private Thread monitorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ConnectionStatusListener statusListener;

    /**
     * Lock care serializează TOATE operațiile pe sftpChannel.
     * JSch ChannelSftp NU e thread-safe — orice acces concurent
     * (put, get, pwd, cd, mkdir) corup starea internă.
     */
    private final ReentrantLock sftpLock = new ReentrantLock();

    public SftpManager(Server server) {
        this.server = server;
    }

    /**
     * Deschide sesiunea SSH + canalul SFTP și pornește monitoring-ul.
     * <p>
     * Configurare notabilă:
     * <ul>
     *   <li>StrictHostKeyChecking=no — necesar pentru servere interne fără known_hosts</li>
     *   <li>GSSAPI dezactivat complet — evită timeout-uri pe servere fără Kerberos</li>
     *   <li>Keep-alive la {KEEP_ALIVE_INTERVAL_MS}ms cu max {KEEP_ALIVE_MAX_FAILURES} eșecuri</li>
     * </ul>
     */
    public void connect() throws JSchException {
        JSch jsch = new JSch();
        JSch.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey");

        session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
        session.setPassword(server.getPassword());

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password,keyboard-interactive");
        config.put("userauth.gssapi-with-mic", "no");
        config.put("GSSAPIAuthentication", "no");
        config.put("GSSAPIDelegateCredentials", "no");
        config.put("GSSAPIKeyExchange", "no");
        config.put("GSSAPITrustDNS", "no");
        session.setConfig(config);

        session.setTimeout(CONNECTION_TIMEOUT_MS);
        session.setServerAliveInterval(KEEP_ALIVE_INTERVAL_MS);
        session.setServerAliveCountMax(KEEP_ALIVE_MAX_FAILURES);

        LOGGER.info("Connecting to: " + server.getHost() + ":" + server.getPort());
        session.connect();

        Channel channel = session.openChannel("sftp");
        channel.connect();
        sftpChannel = (ChannelSftp) channel;

        LOGGER.info("SFTP connected to: " + server.getHost());
        startConnectionMonitoring();
    }

    public void disconnect() {
        stopConnectionMonitoring();

        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }

        sftpChannel = null;
        session = null;
        LOGGER.info("SFTP disconnected from: " + server.getHost());
    }

    public boolean isConnected() {
        return session != null && session.isConnected()
                && sftpChannel != null && sftpChannel.isConnected();
    }

    /**
     * Verifică conexiunea cu un test real de rețea (pwd pe canalul SFTP).
     * <p>
     * Folosește tryLock() în loc de lock(): dacă alt thread execută un upload/download,
     * nu blochează — faptul că canalul e ocupat dovedește că conexiunea e activă.
     */
    private boolean isConnectionAlive() {
        if (!sftpLock.tryLock()) {
            return true;
        }

        try {
            if (!isConnected()) return false;
            sftpChannel.pwd();
            return true;
        } catch (Exception e) {
            LOGGER.warning("Connection alive check failed: " + e.getMessage());
            return false;
        } finally {
            sftpLock.unlock();
        }
    }

    /**
     * Thread daemon care verifică periodic (la fiecare {MONITOR_INTERVAL_MS}ms)
     * dacă conexiunea e activă. La prima detectare de pierdere, notifică listener-ul
     * și se oprește — reconectarea e responsabilitatea ConnectionManager-ului.
     */
    private void startConnectionMonitoring() {
        stopConnectionMonitoring();
        running.set(true);

        monitorThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(MONITOR_INTERVAL_MS);
                    if (!running.get()) break;

                    if (!isConnectionAlive()) {
                        LOGGER.warning("Connection lost to: " + server.getHost());
                        if (statusListener != null) {
                            statusListener.onConnectionLost();
                        }
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error in connection monitoring", e);
                }
            }
        }, "SFTP-Monitor-" + server.getHost());

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void stopConnectionMonitoring() {
        running.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
    }

    /**
     * Upload atomic: creează directoarele remote dacă nu există,
     * apoi transferă fișierul. Operația e serializată prin sftpLock.
     */
    public void uploadFile(String localPath, String remotePath) throws SftpException {
        ensureConnected();

        sftpLock.lock();
        try {
            ensureRemoteDirectory(remotePath.substring(0, remotePath.lastIndexOf('/')));
            sftpChannel.put(localPath, remotePath);
            LOGGER.info("Uploaded: " + localPath + " → " + remotePath);
        } finally {
            sftpLock.unlock();
        }
    }

    public void downloadFile(String remotePath, String localPath) throws SftpException {
        ensureConnected();
        LOGGER.info("Downloading: " + remotePath + " → " + localPath);

        sftpLock.lock();
        try {
            sftpChannel.get(remotePath, localPath);
            LOGGER.info("Downloaded successfully");
        } finally {
            sftpLock.unlock();
        }
    }

    /**
     * Execută o comandă SSH remote pe un canal exec dedicat (separat de canalul SFTP).
     * <p>
     * Nu necesită sftpLock — fiecare execuție deschide și închide propriul canal.
     * Așteaptă finalizarea comenzii cu polling la 100ms, cu timeout de {COMMAND_TIMEOUT_MS}ms.
     *
     * @throws RuntimeException dacă comanda depășește timeout-ul sau returnează exit code != 0
     * @throws IllegalStateException dacă sesiunea SSH nu e conectată
     */
    public String executeCommand(String command) throws Exception {
        LOGGER.fine("Executing command: " + command);

        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("SSH session not connected");
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

            long deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new RuntimeException("Command timed out after " + COMMAND_TIMEOUT_MS + "ms");
                }
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            String output = outputStream.toString("UTF-8");
            String error = errorStream.toString("UTF-8");

            LOGGER.fine("Command exit code: " + exitCode);

            if (exitCode != 0 && !error.isEmpty()) {
                throw new RuntimeException("Command failed (exit " + exitCode + "): " + error);
            }

            return output;

        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    private void ensureConnected() throws SftpException {
        if (!isConnected()) {
            throw new SftpException(ChannelSftp.SSH_FX_NO_CONNECTION, "Not connected to server");
        }
    }

    /**
     * Creează recursiv directoarele remote dacă nu există.
     * <p>
     * <b>IMPORTANT:</b> Apelat doar din metode care deja dețin sftpLock!
     * Nu achiziționează lock-ul singur — ar cauza deadlock.
     */
    private void ensureRemoteDirectory(String path) {
        try {
            sftpChannel.cd(path);
        } catch (SftpException e) {
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
                    } catch (SftpException ignored) {}
                }
            }
        }
    }

    public ChannelSftp getSftpChannel() { return sftpChannel; }
    public Server getServer() { return server; }

    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        this.statusListener = listener;
    }

    public interface ConnectionStatusListener {
        void onConnectionLost();
    }
}