package com.r3cev;

import com.r3cev.costing.RuntimeCostAccounter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.DatatypeConverter;
import static org.junit.Assert.*;

public class TestUtils {

    private static Path jarFSDir = null;
    private static Path tmpdir;

    public static void setPathToTmpJar(final String resourcePathToJar) throws IOException {
        // Copy resource jar to tmp dir
        tmpdir = Files.createTempDirectory(Paths.get("/tmp"), "wlcl-tmp-test");
        final InputStream in = TestUtils.class.getResourceAsStream(resourcePathToJar);
        Path copiedJar = tmpdir.resolve("tmp-resource.jar");
        Files.copy(in, copiedJar, StandardCopyOption.REPLACE_EXISTING);

        final FileSystem fs = FileSystems.newFileSystem(copiedJar, null);
        jarFSDir = fs.getRootDirectories().iterator().next();
    }

    public static Path copySandboxJarToTmpDir(final String resourcePathToJar) throws IOException {
        final InputStream in = TestUtils.class.getResourceAsStream(resourcePathToJar);
        Path sandboxJar = tmpdir.resolve("tmp-sandbox.jar");
        Files.copy(in, sandboxJar, StandardCopyOption.REPLACE_EXISTING);
        final FileSystem sandboxFs = FileSystems.newFileSystem(sandboxJar, null);
        
        return sandboxFs.getRootDirectories().iterator().next();
    }
    
    public static Path getJarFSRoot() {
        return jarFSDir;
    }

    public static void cleanupTmpJar() throws IOException {
        Files.walkFileTree(tmpdir, new Reaper());
    }

    public static void checkAllCosts(final int allocCost, final int jumpCost, final int invokeCost, final int throwCost) {
        assertEquals(allocCost, RuntimeCostAccounter.getAllocationCost());
        assertEquals(jumpCost, RuntimeCostAccounter.getJumpCost());
        assertEquals(invokeCost, RuntimeCostAccounter.getInvokeCost());
        assertEquals(throwCost, RuntimeCostAccounter.getThrowCost());
    }

    public static Class<?> transformClass(final String classFName, final int originalLength, final int newLength) throws IOException, Exception {
        byte[] basic = getBytes(classFName);
        assertEquals(originalLength, basic.length);
        final byte[] tfmd = instrumentWithCosts(basic, new HashSet<>());
        final Path testdir = Files.createTempDirectory(Paths.get("/tmp"), "greymalkin-test-");
        final Path out = testdir.resolve(classFName);
        Files.createDirectories(out.getParent());
        Files.write(out, tfmd);
        if (newLength > 0) {
            assertEquals(newLength, tfmd.length);
        }
        final MyClassloader mycl = new MyClassloader();
        final Class<?> clz = mycl.byPath(out);

        Files.walkFileTree(testdir, new Reaper());

        return clz;
    }

    public static Class<?> transformClass(final String resourceMethodAccessIsRewrittenclass, int i) throws Exception {
        return transformClass(resourceMethodAccessIsRewrittenclass, i, -1);
    }

    public static byte[] getBytes(final String original) throws IOException {
        return Files.readAllBytes(jarFSDir.resolve(original));
    }

    // Helper for finding the correct offsets if they change
    public static void printBytes(byte[] data) {
        byte[] datum = new byte[1];
        for (int i=0; i < data.length; i++) {
            datum[0] = data[i];
            System.out.println(i +" : "+ DatatypeConverter.printHexBinary(datum));
        }
    }

    public static int findOffset(byte[] classBytes, byte[] originalSeq) {
        int offset = 0;
        for (int i=415; i < classBytes.length; i++) {
            if (classBytes[i] != originalSeq[offset]) {
                offset = 0;
                continue;
            }
            if (offset == originalSeq.length - 1) {
                return i - offset;
            }
            offset++;
        }
        
        return -1;
    }

    public static byte[] instrumentWithCosts(byte[] basic, Set<String> hashSet) throws Exception {
        final WhitelistClassLoader wlcl = WhitelistClassLoader.of("/tmp");
        return wlcl.instrumentWithCosts(basic, hashSet);
    }

    
    public static final class MyClassloader extends ClassLoader {

        public Class<?> byPath(Path p) throws IOException {
            final byte[] buffy = Files.readAllBytes(p);
            return defineClass(null, buffy, 0, buffy.length);
        }
    }

    public static final class Reaper extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc == null) {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            } else {
                throw exc;
            }
        }
    }
}
