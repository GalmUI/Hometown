package dev.conner.hometown;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.conner.hometown.config.HometownServerConfig;
import net.neoforged.fml.config.IConfigSpec;

/** In-memory server config lifecycle for tests that exercise the real request owner. */
public final class TestServerConfig implements AutoCloseable {
    public final CommentedConfig values=CommentedConfig.inMemory();
    public TestServerConfig() {
        HometownServerConfig.SPEC.correct(values);
        IConfigSpec.ILoadedConfig loaded;
        try {
            // Use the pinned loader's real in-memory configuration carrier, without disk writes.
            var constructor=Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructor(
                CommentedConfig.class,java.nio.file.Path.class,net.neoforged.fml.config.ModConfig.class);
            constructor.setAccessible(true);
            loaded=(IConfigSpec.ILoadedConfig)constructor.newInstance(values,null,null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Pinned loader configuration implementation missing",exception);
        }
        HometownServerConfig.SPEC.acceptConfig(loaded);
    }
    public void set(String path,Object value) {
        values.set(path,value);
        HometownServerConfig.SPEC.afterReload();
    }
    @Override public void close(){HometownServerConfig.SPEC.acceptConfig(null);}
}
