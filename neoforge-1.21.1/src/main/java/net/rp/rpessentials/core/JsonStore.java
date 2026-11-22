package net.rp.rpessentials.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.rp.rpessentials.RpEssentials;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * One JSON data file of the world (world/data/rpessentials/&lt;name&gt;), shared by every data manager:
 * <ul>
 *   <li>the file is located on first use and again after {@link #reset()} (another world in solo);</li>
 *   <li>reading is UTF-8; a file that cannot be read is moved aside ({@code .corrupt-<time>}) so the
 *       next save never overwrites it with empty data;</li>
 *   <li>saves go through {@link RpEssentialsIO} (grouped, written on a background thread, atomic).</li>
 * </ul>
 * The data passed to {@link #save} must be a copy: it is written later on the IO thread.
 */
public final class JsonStore<T> {

    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final String fileName;
    private final Type type;
    private final Gson gson;
    private final String tag;
    private File file;

    /**
     * @param fileName name of the file in the data folder
     * @param type     type of the whole file content (use a TypeToken for generic types)
     * @param tag      name used in the log, for example "MuteManager"
     */
    public JsonStore(String fileName, Type type, String tag) {
        this(fileName, type, tag, PRETTY);
    }

    public JsonStore(String fileName, Type type, String tag, Gson gson) {
        this.fileName = fileName;
        this.type = type;
        this.tag = tag;
        this.gson = gson;
    }

    /** The file in the data folder of the current world. */
    public File file() {
        if (file == null) {
            File folder = RpEssentialsDataPaths.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            file = new File(folder, fileName);
        }
        return file;
    }

    /** Forgets the file location: the next access looks it up again (world change, reload). */
    public void reset() {
        file = null;
    }

    public boolean exists() {
        return file().exists();
    }

    /** Content of the file, or null when it does not exist, is empty, or cannot be read (moved aside). */
    public T load() {
        return read(file(), type, gson, tag);
    }

    /** Saves a copy of the data, grouped with the other saves of this file. */
    public void save(T snapshot) {
        RpEssentialsIO.saveJson(file(), snapshot, gson);
    }

    /** Writes now (server stop, data that must not wait). */
    public void saveNow(T snapshot) {
        RpEssentialsIO.writeJsonNow(file(), snapshot, gson);
    }

    /**
     * Reads any JSON file (also used for files that are not one per manager, such as one note file per
     * player). A file that cannot be read is moved aside and null is returned.
     */
    public static <V> V read(File f, Type type, Gson gson, String tag) {
        if (f == null || !f.exists()) return null;
        try (Reader reader = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[{}] Failed to read {}", tag, f.getName(), e);
            RpEssentialsIO.quarantine(f);
            return null;
        }
    }
}
