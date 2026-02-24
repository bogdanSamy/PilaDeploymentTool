package com.autodeploy.domain.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manager generic pentru entități persistate în fișiere JSON.
 * Oferă CRUD + save/load atomic cu backup.
 * <p>
 * Subclasele trebuie să implementeze:
 * <ul>
 *   <li>{@link #isValid(Object)} — reguli de validare</li>
 *   <li>{@link #getDisplayName(Object)} — reprezentare pentru logging</li>
 *   <li>{@link #preserveId(Object, Object)} — transfer ID la update</li>
 * </ul>
 *
 * @param <T> tipul entității (Project, Server, etc.)
 */
public abstract class JsonFileManager<T> {

    private static final Logger LOGGER = Logger.getLogger(JsonFileManager.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private final String filePath;
    private final TypeReference<List<T>> typeReference;
    private final String entityName;

    private List<T> items;

    protected JsonFileManager(String filePath, TypeReference<List<T>> typeReference, String entityName) {
        this.filePath = filePath;
        this.typeReference = typeReference;
        this.entityName = entityName;
        this.items = new ArrayList<>();
        load();
    }

    public List<T> getAll() {
        return new ArrayList<>(items);
    }

    public void add(T item) {
        if (item == null || !isValid(item)) {
            LOGGER.warning("Cannot add invalid " + entityName);
            return;
        }

        items.add(item);
        save();
        LOGGER.info("Added " + entityName + ": " + getDisplayName(item));
    }

    public void update(T oldItem, T newItem) {
        int index = items.indexOf(oldItem);
        if (index == -1) {
            LOGGER.warning("Cannot update " + entityName + " — not found");
            return;
        }

        if (!isValid(newItem)) {
            LOGGER.warning("Cannot update " + entityName + " — new data is invalid");
            return;
        }

        preserveId(oldItem, newItem);
        items.set(index, newItem);
        save();
        LOGGER.info("Updated " + entityName + ": " + getDisplayName(newItem));
    }

    public void delete(T item) {
        if (items.remove(item)) {
            save();
            LOGGER.info("Deleted " + entityName + ": " + getDisplayName(item));
        } else {
            LOGGER.warning("Cannot delete " + entityName + " — not found");
        }
    }

    public void reload() {
        LOGGER.info("Reloading " + entityName + "s from file...");
        load();
    }

    protected abstract boolean isValid(T item);
    protected abstract String getDisplayName(T item);
    protected abstract void preserveId(T oldItem, T newItem);

    private void load() {
        File file = new File(filePath);

        if (!file.exists() || file.length() == 0) {
            LOGGER.info("No existing " + filePath + " found. Starting with empty list.");
            items = new ArrayList<>();
            return;
        }

        try {
            List<T> loaded = OBJECT_MAPPER.readValue(file, typeReference);
            items = (loaded != null) ? new ArrayList<>(loaded) : new ArrayList<>();
            LOGGER.info("Loaded " + items.size() + " " + entityName + "(s) from " + filePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load " + filePath, e);
            items = new ArrayList<>();
        }
    }

    /**
     * Salvează lista curentă în fișier JSON.
     * Creează un backup (.bak) înainte de scriere și restaurează automat
     * dacă serializarea eșuează — previne pierderea datelor.
     */
    private void save() {
        Path target = Path.of(filePath);
        Path backup = Path.of(filePath + ".bak");

        try {
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            OBJECT_MAPPER.writeValue(target.toFile(), items);
            LOGGER.info("Saved " + items.size() + " " + entityName + "(s) to " + filePath);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save " + filePath, e);
            restoreFromBackup(target, backup);
        }
    }

    private void restoreFromBackup(Path target, Path backup) {
        try {
            if (Files.exists(backup)) {
                Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Restored " + filePath + " from backup");
            }
        } catch (IOException restoreError) {
            LOGGER.log(Level.SEVERE, "Failed to restore backup for " + filePath, restoreError);
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}