package com.dragon.command;

import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.cf.iface.ParseException;
import com.android.dx.command.DxConsole;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author kiva
 * @date 2017/03/17
 */
public class DexConverter {
    private AtomicInteger errors = new AtomicInteger(0);
    private DexConverter.Arguments args;
    private final DexFile outputDex;
    private ExecutorService classTranslatorPool;
    private ExecutorService classDefItemConsumer;
    private List<Future<Boolean>> addToDexFutures = new ArrayList<>();

    public DexConverter() {
        args = new Arguments();
        outputDex = new DexFile(args.dexOptions);
        if (args.dumpWidth != 0) {
            outputDex.setDumpWidth(args.dumpWidth);
        }
    }

    public byte[] convertJavaClass(String fullClassName, byte[] bytes) {
        classTranslatorPool = new ThreadPoolExecutor(args.numThreads, args.numThreads, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2 * args.numThreads, true), new ThreadPoolExecutor.CallerRunsPolicy());
        classDefItemConsumer = Executors.newSingleThreadExecutor();

        processJavaClass(fullClassName, bytes);

        try {
            classTranslatorPool.shutdown();
            classTranslatorPool.awaitTermination(600L, TimeUnit.SECONDS);
            classDefItemConsumer.shutdown();
            classDefItemConsumer.awaitTermination(600L, TimeUnit.SECONDS);

            for (Future<Boolean> addToDexFuture : addToDexFutures) {
                try {
                    addToDexFuture.get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }

            return writeDex(outputDex);

        } catch (Throwable e) {
            classTranslatorPool.shutdownNow();
            classDefItemConsumer.shutdownNow();
        }

        return null;
    }

    private byte[] writeDex(DexFile outputDex) {
        byte[] outArray = null;

        try {
            try {
                outArray = outputDex.toDex(null, false);
            } catch (Throwable ignore) {
            }

            return outArray;
        } catch (Exception var6) {
            DxConsole.err.println("\ntrouble writing output: " + var6.getMessage());
            return null;
        }
    }

    private boolean processJavaClass(String name, byte[] bytes) {
        try {
            DirectClassFileConsumer consumer = new DexConverter.DirectClassFileConsumer(this, name, bytes, null);
            ClassParserTask classParserTask = new DexConverter.ClassParserTask(this, name, bytes);
            consumer.call(classParserTask.call());

            return true;
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Exception parsing classes", e);
        }
    }

    private DirectClassFile parseClass(String name, byte[] bytes) {
        DirectClassFile cf = new DirectClassFile(bytes, name, args.cfOptions.strictNameCheck);
        cf.setAttributeFactory(StdAttributeFactory.THE_ONE);
        cf.getMagic();
        return cf;
    }

    private ClassDefItem translateClass(byte[] bytes, DirectClassFile cf) {
        try {
            return CfTranslator.translate(cf, bytes, args.cfOptions, args.dexOptions, outputDex);
        } catch (ParseException e) {
            e.printStackTrace();
            errors.incrementAndGet();
            return null;
        }
    }

    private boolean addClassToDex(ClassDefItem clazz) {
        System.out.println("adding classes: " + clazz.getThisClass().typeName());
        synchronized (outputDex) {
            outputDex.add(clazz);
            return true;
        }
    }

    private static class ClassDefItemConsumer implements Callable<Boolean> {
        String name;
        Future<ClassDefItem> futureClazz;
        int maxMethodIdsInClass;
        int maxFieldIdsInClass;

        private DexConverter dexConverter;

        private ClassDefItemConsumer(DexConverter converter, String name, Future<ClassDefItem> futureClazz, int maxMethodIdsInClass, int maxFieldIdsInClass) {
            this.dexConverter = converter;
            this.name = name;
            this.futureClazz = futureClazz;
            this.maxMethodIdsInClass = maxMethodIdsInClass;
            this.maxFieldIdsInClass = maxFieldIdsInClass;
        }

        public Boolean call() throws Exception {
            try {
                ClassDefItem ex = this.futureClazz.get();
                if (ex != null) {
                    dexConverter.addClassToDex(ex);
                }
            } catch (ExecutionException var15) {
                Throwable t = var15.getCause();
                throw t instanceof Exception ? (Exception) t : var15;
            }
            return true;
        }
    }

    private static class ClassTranslatorTask implements Callable<ClassDefItem> {
        String name;
        byte[] bytes;
        DirectClassFile classFile;

        private DexConverter dexConverter;

        private ClassTranslatorTask(DexConverter converter, String name, byte[] bytes, DirectClassFile classFile) {
            this.dexConverter = converter;
            this.name = name;
            this.bytes = bytes;
            this.classFile = classFile;
        }

        public ClassDefItem call() {
            return dexConverter.translateClass(this.bytes, this.classFile);
        }
    }

    private static class DirectClassFileConsumer implements Callable<Boolean> {
        String name;
        byte[] bytes;
        Future<DirectClassFile> directClassFileFuture;
        private DexConverter dexConverter;

        private DirectClassFileConsumer(DexConverter converter, String name, byte[] bytes, Future<DirectClassFile> dcff) {
            this.dexConverter = converter;
            this.name = name;
            this.bytes = bytes;
            this.directClassFileFuture = dcff;
        }

        public Boolean call() throws Exception {
            DirectClassFile cf = this.directClassFileFuture.get();
            return this.call(cf);
        }

        private Boolean call(DirectClassFile cf) {
            int maxMethodIdsInClass = 65536;
            int maxFieldIdsInClass = 65536;
            Future<ClassDefItem> classDefItem = dexConverter.classTranslatorPool.submit(new DexConverter.ClassTranslatorTask(dexConverter, this.name, this.bytes, cf));
            Future<Boolean> res = dexConverter.classDefItemConsumer.submit(new DexConverter.ClassDefItemConsumer(dexConverter, this.name, classDefItem, maxMethodIdsInClass, maxFieldIdsInClass));
            dexConverter.addToDexFutures.add(res);
            return Boolean.TRUE;
        }
    }

    private static class ClassParserTask implements Callable<DirectClassFile> {
        String name;
        byte[] bytes;
        private DexConverter dexConverter;

        private ClassParserTask(DexConverter converter, String name, byte[] bytes) {
            this.dexConverter = converter;
            this.name = name;
            this.bytes = bytes;
        }

        public DirectClassFile call() throws Exception {
            return dexConverter.parseClass(this.name, this.bytes);
        }
    }

    private static class Arguments {
        int dumpWidth = 0;
        boolean statistics;
        CfOptions cfOptions;
        DexOptions dexOptions;
        int numThreads = 1;

        Arguments() {
            this.makeOptionsObjects();
        }

        private void makeOptionsObjects() {
            this.cfOptions = new CfOptions();
            this.cfOptions.positionInfo = 2;
            this.cfOptions.localInfo = true;
            this.cfOptions.strictNameCheck = false;
            this.cfOptions.optimize = true;
            this.cfOptions.optimizeListFile = null;
            this.cfOptions.dontOptimizeListFile = null;
            this.cfOptions.statistics = this.statistics;
            this.cfOptions.warn = DxConsole.noop;

            this.dexOptions = new DexOptions();
            this.dexOptions.forceJumbo = false;
        }

    }

}
