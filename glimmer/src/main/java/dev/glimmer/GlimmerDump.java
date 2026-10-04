package dev.glimmer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * /glimmer dump writes the method and field names of Minecraft's item, block and world
 * rendering classes to glimmer-dump.txt. Nothing is sent anywhere; it is only a file on your disk.
 * It lets the real class names be checked before the weapon outline and block shading are written.
 */
public final class GlimmerDump {
    private GlimmerDump() {}

    private static final Pattern WANTED = Pattern.compile(
            "ItemInHand|ItemStackRenderState|ItemModel|SubmitNode|OutlineBuffer|RenderPipelines|RenderTypes|RenderType"
                    + "|BlockRenderDispatcher|BlockStateModel|BlockModelPart|BlockColors|BlockTintSource|BlockTint"
                    + "|LevelRenderer|FeatureRenderDispatcher|ItemFeatureRenderer|GameRenderer|LightTexture|Lightmap"
                    + "|PostChain|PostPass|LevelTargetBundle|SectionCompiler|ModelBlockRenderer|BlockQuad|BakedQuad"
                    + "|CompositeModel|ItemTransform|ItemDisplayContext");

    public static String run() {
        Path out = FabricLoader.getInstance().getGameDir().resolve("glimmer-dump.txt");
        StringBuilder sb = new StringBuilder();
        sb.append("Glimmer class dump\n");
        try {
            ClassLoader cl = Minecraft.class.getClassLoader();
            List<String> names = findClasses();
            Collections.sort(names);
            sb.append("classes matched: ").append(names.size()).append("\n\n");
            for (String n : names) {
                try {
                    describe(Class.forName(n, false, cl), sb);
                } catch (Throwable t) {
                    sb.append("## ").append(n).append("  (could not load: ").append(t).append(")\n\n");
                }
            }
            Files.writeString(out, sb.toString());
            return out.toString();
        } catch (Throwable t) {
            try {
                Files.writeString(out, sb + "\nFAILED: " + t + "\n");
            } catch (Exception ignored) {
            }
            return out + "  (partial: " + t + ")";
        }
    }

    private static List<String> findClasses() throws Exception {
        List<String> found = new ArrayList<>();
        URL loc = Minecraft.class.getProtectionDomain().getCodeSource().getLocation();
        String s = loc.toString();
        if (s.startsWith("jar:")) s = s.substring(4);
        int bang = s.indexOf("!/");
        if (bang >= 0) s = s.substring(0, bang);
        File jar = new File(new URI(s));
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                if (!name.endsWith(".class") || !name.startsWith("net/minecraft/client/")) continue;
                String simple = name.substring(name.lastIndexOf('/') + 1);
                if (simple.indexOf('$') >= 0) continue; // nested classes are listed with their parent
                if (WANTED.matcher(simple).find()) {
                    found.add(name.substring(0, name.length() - 6).replace('/', '.'));
                }
            }
        }
        return found;
    }

    private static void describe(Class<?> c, StringBuilder sb) {
        sb.append("## ").append(Modifier.toString(c.getModifiers())).append(' ')
                .append(c.isInterface() ? "interface " : c.isEnum() ? "enum " : "class ").append(c.getName());
        if (c.getSuperclass() != null && c.getSuperclass() != Object.class) {
            sb.append(" extends ").append(c.getSuperclass().getName());
        }
        sb.append('\n');
        try {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isPrivate(f.getModifiers())) continue;
                sb.append("  F ").append(f.toGenericString()).append('\n');
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (Modifier.isPrivate(k.getModifiers())) continue;
                sb.append("  C ").append(k.toGenericString()).append('\n');
            }
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isPrivate(m.getModifiers()) || m.isSynthetic()) continue;
                sb.append("  M ").append(m.toGenericString()).append('\n');
            }
        } catch (Throwable t) {
            sb.append("  (members unavailable: ").append(t).append(")\n");
        }
        sb.append('\n');
        try {
            for (Class<?> inner : c.getDeclaredClasses()) {
                describe(inner, sb);
            }
        } catch (Throwable ignored) {
        }
    }
}
