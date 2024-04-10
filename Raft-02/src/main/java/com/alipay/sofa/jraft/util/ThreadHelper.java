package com.alipay.sofa.jraft.util;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

public final class ThreadHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadHelper.class);

    private static final Spinner SPINNER;

    static {
        final Object maybeException = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            try {
                // noinspection JavaReflectionMemberAccess
                Thread.class.getDeclaredMethod("onSpinWait");
                return null;
            } catch (final NoSuchMethodException | SecurityException e) {
                return e;
            }
        });
        if (maybeException == null) {
            SPINNER = createSpinner();
        } else {
            SPINNER = new DefaultSpinner();
        }
    }

    public static void onSpinWait() {
        SPINNER.onSpinWait();
    }

    private ThreadHelper() {
    }

    public static abstract class Spinner {

        public abstract void onSpinWait();
    }

    static class DefaultSpinner extends Spinner {

        @Override
        public void onSpinWait() {
            Thread.yield();
        }
    }

    private static Spinner createSpinner() {
        final String superClassName = Spinner.class.getName();
        final String superClassNameInternal = superClassName.replace('.', '/');

        final String spinnerClassName = superClassName + "Impl";
        final String spinnerClassNameInternal = spinnerClassName.replace('.', '/');

        final String threadClassNameInternal = Thread.class.getName().replace('.', '/');

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, spinnerClassNameInternal, null, superClassNameInternal, null);

        MethodVisitor mv;

        // default constructor
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, superClassNameInternal, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // implementation of method: `public abstract void onSpinWait()`
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "onSpinWait", "()V", null, null);
            mv.visitCode();
            mv.visitMethodInsn(INVOKESTATIC, threadClassNameInternal, "onSpinWait", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();

        try {
            final byte[] classBytes = cw.toByteArray();
            final Class<?> spinnerClass = SpinnerClassLoader.INSTANCE.defineClass(spinnerClassName, classBytes);
            return (Spinner) spinnerClass.getDeclaredConstructor().newInstance();
        } catch (final Throwable t) {
            LOG.warn("Error constructing spinner class: {}, will return a default spinner.", spinnerClassName, t);
            return new DefaultSpinner();
        }
    }

    private static class SpinnerClassLoader extends ClassLoader {

        static final SpinnerClassLoader INSTANCE;

        static {
            ClassLoader parent = Spinner.class.getClassLoader();
            if (parent == null) {
                parent = ClassLoader.getSystemClassLoader();
            }
            INSTANCE = new SpinnerClassLoader(parent);
        }

        SpinnerClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineClass(final String name, final byte[] bytes) throws ClassFormatError {
            return defineClass(name, bytes, 0, bytes.length, getClass().getProtectionDomain());
        }
    }
}
