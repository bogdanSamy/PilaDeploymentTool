package com.autodeploy.helper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class AntFileParser {

    /**
     * Parsează un fișier Ant build.xml și returnează lista de target-uri disponibile,
     * inclusiv cele din fișierele importate.
     *
     * @param antFilePath Calea către fișierul Ant
     * @return Lista de nume ale target-urilor găsite
     */
    public static List<String> parseTargets(String antFilePath) {
        List<String> targets = new ArrayList<>();

        if (antFilePath == null || antFilePath.trim().isEmpty()) {
            return targets;
        }

        File file = new File(antFilePath);
        if (!file.exists() || !file.isFile()) {
            System.err.println("✗ Ant file not found: " + antFilePath);
            return targets;
        }

        // Încarcă proprietățile din build.properties
        Properties props = loadBuildProperties(file.getParentFile());

        // Adaugă basedir ca proprietate
        props.setProperty("basedir", file.getParentFile().getAbsolutePath());

        // Parsează recursiv toate fișierele
        Set<String> visitedFiles = new HashSet<>();
        parseTargetsRecursive(file, targets, props, visitedFiles);

        // Sortează alfabetic pentru o navigare mai ușoară
        targets.sort(String::compareToIgnoreCase);

        System.out.println("✓ Found " + targets.size() + " targets in " + antFilePath);
        targets.forEach(t -> System.out.println("  - " + t));

        return targets;
    }

    /**
     * Parsează recursiv un fișier Ant și toate fișierele importate.
     */
    private static void parseTargetsRecursive(File file, List<String> targets,
                                              Properties props, Set<String> visitedFiles) {
        String absolutePath;
        try {
            absolutePath = file.getCanonicalPath();
        } catch (IOException e) {
            absolutePath = file.getAbsolutePath();
        }

        // Evită parsarea aceluiași fișier de mai multe ori
        if (visitedFiles.contains(absolutePath)) {
            return;
        }
        visitedFiles.add(absolutePath);

        if (!file.exists() || !file.isFile()) {
            System.err.println("✗ Imported file not found: " + file.getPath());
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            document.getDocumentElement().normalize();

            // Extrage proprietățile definite în acest fișier
            extractProperties(document, props, file.getParentFile());

            // Găsește toate elementele <target>
            NodeList targetNodes = document.getElementsByTagName("target");
            for (int i = 0; i < targetNodes.getLength(); i++) {
                Element targetElement = (Element) targetNodes.item(i);
                String targetName = targetElement.getAttribute("name");

                if (targetName != null && !targetName.trim().isEmpty()) {
                    // Evită duplicatele
                    if (!targets.contains(targetName)) {
                        targets.add(targetName);
                    }
                }
            }

            // Procesează fișierele importate
            NodeList importNodes = document.getElementsByTagName("import");
            for (int i = 0; i < importNodes.getLength(); i++) {
                Element importElement = (Element) importNodes.item(i);
                String importFilePath = importElement.getAttribute("file");

                if (importFilePath != null && !importFilePath.trim().isEmpty()) {
                    // Rezolvă proprietățile în calea fișierului
                    String resolvedPath = resolveProperties(importFilePath, props);
                    File importFile = resolveFile(resolvedPath, file.getParentFile());

                    System.out.println("  → Processing import: " + resolvedPath);
                    parseTargetsRecursive(importFile, targets, props, visitedFiles);
                }
            }

        } catch (Exception e) {
            System.err.println("✗ Error parsing Ant file " + file.getPath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Încarcă proprietățile din build.properties dacă există.
     */
    private static Properties loadBuildProperties(File directory) {
        Properties props = new Properties();

        // Încearcă să încarce build.properties din directorul curent
        File propsFile = new File(directory, "build.properties");
        if (propsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                props.load(fis);
                System.out.println("✓ Loaded properties from: " + propsFile.getPath());
            } catch (IOException e) {
                System.err.println("✗ Error loading build.properties: " + e.getMessage());
            }
        }

        // Adaugă și proprietățile sistem
        for (String key : System.getProperties().stringPropertyNames()) {
            if (!props.containsKey(key)) {
                props.setProperty(key, System.getProperty(key));
            }
        }

        return props;
    }

    /**
     * Extrage proprietățile definite într-un document Ant.
     */
    private static void extractProperties(Document document, Properties props, File baseDir) {
        NodeList propertyNodes = document.getElementsByTagName("property");

        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element propElement = (Element) propertyNodes.item(i);

            String name = propElement.getAttribute("name");
            String value = propElement.getAttribute("value");
            String fileAttr = propElement.getAttribute("file");

            // Proprietate cu valoare directă
            if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
                String resolvedValue = resolveProperties(value, props);
                if (!props.containsKey(name)) {
                    props.setProperty(name, resolvedValue);
                }
            }

            // Proprietate cu referință la fișier
            if (fileAttr != null && !fileAttr.isEmpty()) {
                String resolvedFilePath = resolveProperties(fileAttr, props);
                File propFile = resolveFile(resolvedFilePath, baseDir);

                if (propFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(propFile)) {
                        Properties fileProps = new Properties();
                        fileProps.load(fis);

                        for (String key : fileProps.stringPropertyNames()) {
                            if (!props.containsKey(key)) {
                                props.setProperty(key, fileProps.getProperty(key));
                            }
                        }
                        System.out.println("✓ Loaded properties from: " + propFile.getPath());
                    } catch (IOException e) {
                        System.err.println("✗ Error loading property file " + propFile.getPath() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Rezolvă proprietățile ${...} într-un string.
     */
    private static String resolveProperties(String value, Properties props) {
        if (value == null) {
            return null;
        }

        String result = value;
        int maxIterations = 10; // Previne bucle infinite
        int iteration = 0;

        while (result.contains("${") && iteration < maxIterations) {
            iteration++;
            boolean replaced = false;

            int startIndex = 0;
            while ((startIndex = result.indexOf("${", startIndex)) != -1) {
                int endIndex = result.indexOf("}", startIndex);
                if (endIndex == -1) {
                    break;
                }

                String propName = result.substring(startIndex + 2, endIndex);
                String propValue = props.getProperty(propName);

                if (propValue != null) {
                    result = result.substring(0, startIndex) + propValue + result.substring(endIndex + 1);
                    replaced = true;
                } else {
                    // Treci peste această proprietate nerezolvată
                    startIndex = endIndex + 1;
                }
            }

            if (!replaced) {
                break;
            }
        }

        return result;
    }

    /**
     * Rezolvă calea unui fișier relativ la un director de bază.
     */
    private static File resolveFile(String path, File baseDir) {
        File file = new File(path);

        // Dacă calea este absolută, o folosim direct
        if (file.isAbsolute()) {
            return file;
        }

        // Altfel, o rezolvăm relativ la directorul de bază
        return new File(baseDir, path);
    }

    /**
     * Găsește target-ul default din fișierul Ant.
     *
     * @param antFilePath Calea către fișierul Ant
     * @return Numele target-ului default sau null dacă nu există
     */
    public static String getDefaultTarget(String antFilePath) {
        if (antFilePath == null || antFilePath.trim().isEmpty()) {
            return null;
        }

        File file = new File(antFilePath);
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);

            Element root = document.getDocumentElement();
            if ("project".equals(root.getTagName())) {
                return root.getAttribute("default");
            }
        } catch (Exception e) {
            System.err.println("✗ Error getting default target: " + e.getMessage());
        }

        return null;
    }

    /**
     * Metodă de test.
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            List<String> targets = parseTargets(args[0]);
            System.out.println("\n=== Summary ===");
            System.out.println("Total targets found: " + targets.size());
        } else {
            System.out.println("Usage: java AntFileParser <path-to-build.xml>");
        }
    }
}