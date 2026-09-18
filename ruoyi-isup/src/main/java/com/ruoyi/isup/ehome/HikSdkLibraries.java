package com.ruoyi.isup.ehome;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import java.io.File;
import java.util.*;

/** Platform selection for a complete vendor SDK bundle. Never mix OpenSSL library families. */
public final class HikSdkLibraries {
    private static final List<NativeLibrary> DEPENDENCIES = new ArrayList<>();
    private HikSdkLibraries() { }

    public static boolean linux() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"); }
    public static File directory() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!"amd64".equals(arch) && !"x86_64".equals(arch)) {
            throw new IllegalStateException("Bundled Hikvision SDK requires x86_64; architecture=" + arch);
        }
        String override = System.getProperty("ehome.sdk.path");
        if (override == null || override.trim().isEmpty()) override = System.getenv("EHOME_SDK_PATH");
        File dir = override == null || override.trim().isEmpty()
                ? new File(System.getProperty("user.dir"), "ruoyi-isup/" + (linux() ? "linux-lib" : "win-lib"))
                : new File(override);
        if (!dir.isDirectory()) throw new IllegalStateException("Hikvision SDK directory missing: " + dir.getAbsolutePath());
        return dir.getAbsoluteFile();
    }
    public static File required(String name) {
        File file = new File(directory(), name);
        if (!file.isFile()) throw new IllegalStateException("Hikvision SDK file missing: " + file);
        return file;
    }
    public static String[] cryptoPair() {
        // Preserve the vendor's paired legacy dependencies shipped with the current bundle.
        return linux() ? new String[]{"libcrypto.so", "libssl.so"} : new String[]{"libeay32.dll", "ssleay32.dll"};
    }
    public static byte[] pathBuffer(String path) {
        byte[] bytes = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length >= 256) throw new IllegalArgumentException("Hikvision SDK path must be shorter than 256 bytes");
        return Arrays.copyOf(bytes, 256);
    }
    public static Map<String, Object> options() {
        Map<String, Object> options = new HashMap<>();
        // RTLD_NOW | RTLD_DEEPBIND on Linux: avoid binding vendor OpenSSL to other loaded SDK symbols.
        if (linux()) options.put(Library.OPTION_OPEN_FLAGS, 0x00002 | 0x00008);
        return options;
    }
    public static synchronized <T> T load(String module, Class<T> api) {
        File dir = directory();
        String base = (linux() ? "lib" : "") + "HCISUP" + module + (linux() ? ".so" : ".dll");
        if (DEPENDENCIES.isEmpty()) {
            for (String dependency : cryptoPair()) {
                DEPENDENCIES.add(NativeLibrary.getInstance(required(dependency).getAbsolutePath(), options()));
            }
        }
        NativeLibrary.addSearchPath("HCISUP" + module, dir.getAbsolutePath());
        return api.cast(Native.loadLibrary(required(base).getAbsolutePath(), api, options()));
    }
    public static String version(int version) {
        return ((version >>> 24) & 255) + "." + ((version >>> 16) & 255) + "." + ((version >>> 8) & 255) + "." + (version & 255);
    }
}
