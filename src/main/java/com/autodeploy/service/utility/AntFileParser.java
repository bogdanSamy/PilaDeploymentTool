package com.autodeploy.service.utility;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parsează fișiere Ant build.xml pentru a extrage target-urile disponibile.
 * <p>
 * Complexitate:
 * <ul>
 *   <li>Suportă {@code <import file="..."/>} — parcurge recursiv fișierele importate</li>
 *   <li>Rezolvă proprietăți {@code ${...}} — din build.properties, din system properties,
 *       și din {@code <property>} tags definite în XML</li>
 *   <li>Respectă semantica Ant: prima definiție a unei proprietăți câștigă (immutabilitate)</li>
 *   <li>Protecție anti-buclă: fișierele deja vizitate nu sunt re-parsate,
 *       rezolvarea proprietăților are limită de adâncime</li>
 * </ul>
 * <p>
 * Folosit de UI-ul de configurare proiect pentru a popula dropdown-ul de target-uri Ant.
 */
public final class AntFileParser {

    private static final Logger LOGGER = Logger.getLogger(AntFileParser.class.getName());

    /** Limită de adâncime pentru rezolvarea recursivă a proprietăților (ex: ${${nested}}). */
    private static final int MAX_PROPERTY_RESOLVE_DEPTH = 10;

    private AntFileParser() {}

    /**
     * Punctul de intrare principal. Parsează un build.xml și returnează toate target-urile
     * (inclusiv din fișierele importate), sortate alfabetic case-insensitive.
     */
    public static List<String> parseTargets(String antFilePath) {
        if (antFilePath == null || antFilePath.trim().isEmpty()) {
            return List.of();
        }

        File file = new File(antFilePath);
        if (!file.exists() || !file.isFile()) {
            LOGGER.warning("Ant file not found: " + antFilePath);
            return List.of();
        }

        Properties props = loadBuildProperties(file.getParentFile());
        props.setProperty("basedir", file.getParentFile().getAbsolutePath());

        // LinkedHashSet: O(1) contains + păstrează ordinea de inserție
        Set<String> targets = new LinkedHashSet<>();
        // Previne parsarea ciclică a fișierelor importate (A imports B imports A)
        Set<String> visitedFiles = new HashSet<>();

        parseTargetsRecursive(file, targets, props, visitedFiles);

        List<String> sortedTargets = new ArrayList<>(targets);
        sortedTargets.sort(String::compareToIgnoreCase);

        LOGGER.info("Found " + sortedTargets.size() + " targets in " + antFilePath);
        return sortedTargets;
    }

    public static String getDefaultTarget(String antFilePath) {
        if (antFilePath == null || antFilePath.trim().isEmpty()) {
            return null;
        }

        File file = new File(antFilePath);
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        try {
            Document document = parseXml(file);
            Element root = document.getDocumentElement();

            if ("project".equals(root.getTagName())) {
                String defaultTarget = root.getAttribute("default");
                return defaultTarget.isEmpty() ? null : defaultTarget;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting default target from: " + antFilePath, e);
        }

        return null;
    }

    /**
     * Parsare recursivă: extrage proprietăți, target-uri și urmează import-urile.
     * Ordinea contează — proprietățile trebuie extrase ÎNAINTE de a procesa import-urile
     * deoarece căile din {@code <import file="${basedir}/..."/>} pot conține variabile.
     */
    private static void parseTargetsRecursive(File file, Set<String> targets,
                                              Properties props, Set<String> visitedFiles) {
        String absolutePath = getCanonicalPath(file);

        if (!visitedFiles.add(absolutePath)) return;

        if (!file.exists() || !file.isFile()) {
            LOGGER.warning("Imported file not found: " + file.getPath());
            return;
        }

        try {
            Document document = parseXml(file);

            extractProperties(document, props, file.getParentFile());
            extractTargets(document, targets);
            processImports(document, file.getParentFile(), targets, props, visitedFiles);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing Ant file: " + file.getPath(), e);
        }
    }

    private static void extractTargets(Document document, Set<String> targets) {
        NodeList targetNodes = document.getElementsByTagName("target");
        for (int i = 0; i < targetNodes.getLength(); i++) {
            Element targetElement = (Element) targetNodes.item(i);
            String targetName = targetElement.getAttribute("name");

            if (targetName != null && !targetName.trim().isEmpty()) {
                targets.add(targetName);
            }
        }
    }

    /**
     * Procesează tag-urile {@code <import file="..."/>} — rezolvă proprietățile din cale
     * și parsează recursiv fișierul importat.
     */
    private static void processImports(Document document, File baseDir,
                                       Set<String> targets, Properties props,
                                       Set<String> visitedFiles) {
        NodeList importNodes = document.getElementsByTagName("import");
        for (int i = 0; i < importNodes.getLength(); i++) {
            Element importElement = (Element) importNodes.item(i);
            String importFilePath = importElement.getAttribute("file");

            if (importFilePath != null && !importFilePath.trim().isEmpty()) {
                String resolvedPath = resolveProperties(importFilePath, props);
                File importFile = resolveFile(resolvedPath, baseDir);

                LOGGER.fine("Processing import: " + resolvedPath);
                parseTargetsRecursive(importFile, targets, props, visitedFiles);
            }
        }
    }

    /**
     * Încarcă proprietățile în ordinea de prioritate Ant:
     * <ol>
     *   <li>System properties (bază — cea mai mică prioritate)</li>
     *   <li>build.properties din directorul build-ului (suprascrie system props)</li>
     * </ol>
     * Proprietățile din XML ({@code <property>}) sunt adăugate ulterior în
     * {@link #extractProperties} cu regula "prima definiție câștigă".
     */
    private static Properties loadBuildProperties(File directory) {
        Properties props = new Properties();

        props.putAll(System.getProperties());

        File propsFile = new File(directory, "build.properties");
        if (propsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                Properties fileProps = new Properties();
                fileProps.load(fis);
                props.putAll(fileProps);
                LOGGER.fine("Loaded properties from: " + propsFile.getPath());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error loading build.properties", e);
            }
        }

        return props;
    }

    /**
     * Extrage proprietățile din tag-urile {@code <property>} ale documentului.
     * <p>
     * Respectă semantica Ant: o proprietate odată setată NU poate fi suprascrisă
     * ({@code !props.containsKey(name)}). Suportă două forme:
     * <ul>
     *   <li>{@code <property name="x" value="y"/>} — valoare directă</li>
     *   <li>{@code <property file="extra.properties"/>} — încărcare din fișier extern</li>
     * </ul>
     */
    private static void extractProperties(Document document, Properties props, File baseDir) {
        NodeList propertyNodes = document.getElementsByTagName("property");

        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element propElement = (Element) propertyNodes.item(i);

            String name = propElement.getAttribute("name");
            String value = propElement.getAttribute("value");
            String fileAttr = propElement.getAttribute("file");

            if (!name.isEmpty() && !value.isEmpty() && !props.containsKey(name)) {
                props.setProperty(name, resolveProperties(value, props));
            }

            if (!fileAttr.isEmpty()) {
                loadPropertiesFromFile(resolveProperties(fileAttr, props), baseDir, props);
            }
        }
    }

    private static void loadPropertiesFromFile(String filePath, File baseDir, Properties props) {
        File propFile = resolveFile(filePath, baseDir);
        if (!propFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(propFile)) {
            Properties fileProps = new Properties();
            fileProps.load(fis);

            for (String key : fileProps.stringPropertyNames()) {
                if (!props.containsKey(key)) {
                    props.setProperty(key, fileProps.getProperty(key));
                }
            }
            LOGGER.fine("Loaded properties from: " + propFile.getPath());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error loading property file: " + propFile.getPath(), e);
        }
    }

    /**
     * Rezolvă recursiv proprietățile {@code ${...}} într-un string.
     * <p>
     * Suportă referințe nested (ex: {@code ${path.${env}}}), cu protecție
     * anti-buclă infinită prin {@link #MAX_PROPERTY_RESOLVE_DEPTH}.
     * Proprietățile nerezolvabile sunt lăsate ca atare (ex: {@code ${unknown}}).
     */
    static String resolveProperties(String value, Properties props) {
        if (value == null || !value.contains("${")) return value;

        String result = value;

        for (int iteration = 0; iteration < MAX_PROPERTY_RESOLVE_DEPTH; iteration++) {
            if (!result.contains("${")) break;

            boolean replaced = false;
            int startIndex = 0;

            while ((startIndex = result.indexOf("${", startIndex)) != -1) {
                int endIndex = result.indexOf("}", startIndex);
                if (endIndex == -1) break;

                String propName = result.substring(startIndex + 2, endIndex);
                String propValue = props.getProperty(propName);

                if (propValue != null) {
                    result = result.substring(0, startIndex) + propValue + result.substring(endIndex + 1);
                    replaced = true;
                } else {
                    startIndex = endIndex + 1;
                }
            }

            if (!replaced) break;
        }

        return result;
    }

    /**
     * Parser XML securizat — dezactivează DTD-uri externe și entități externe
     * pentru a preveni atacuri XXE (XML External Entity).
     */
    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        document.getDocumentElement().normalize();
        return document;
    }

    private static File resolveFile(String path, File baseDir) {
        File file = new File(path);
        return file.isAbsolute() ? file : new File(baseDir, path);
    }

    private static String getCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}