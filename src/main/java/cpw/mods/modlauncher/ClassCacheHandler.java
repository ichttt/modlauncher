package cpw.mods.modlauncher;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

class ClassCacheHandler
{
    private static ClassCacheReader configReaderInstance;
    private static ClassCacheFileWriter classCacheFileWriter;
    private static Thread readerThread, writerThread;

    static void init(TransformationServicesHandler servicesHandler)
    {
        Optional<File> mcDir = Launcher.INSTANCE.environment().getProperty(Environment.Keys.GAMEDIR.get());
        Optional<String> ver = Launcher.INSTANCE.environment().getProperty(Environment.Keys.VERSION.get());
        if (!mcDir.isPresent() || !ver.isPresent())
        {
            Logging.launcherLog.warn("Cannot use class cache as the game dir / version is absent!");
            return;
        }
        ClassCache.init(new File(mcDir.get() + "/classcache/" + ver.get() + "/"));
        configReaderInstance = new ClassCacheReader(servicesHandler);
        readerThread = new Thread(configReaderInstance);
        readerThread.setDaemon(true);
        readerThread.setName("ClassCache Reader Thread");
        readerThread.start();
    }

    static void launchClassCacheWriter(TransformationServicesHandler servicesHandler)
    {
        if (configReaderInstance == null)
            return;
        try
        {
            configReaderInstance.latch.await(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            //Shrug
        }
        if (configReaderInstance.latch.getCount() > 0 && readerThread.isAlive()) //in case we timed out
        {
            //Slow IO, disable class cache...
            Logging.launcherLog.warn("VERY slow IO detected. Disabling class cache.");
            readerThread.interrupt();
            ClassCache.invalidate();
        }
        else
        {
            classCacheFileWriter = new ClassCacheFileWriter(servicesHandler);
            writerThread = new Thread(classCacheFileWriter);
            writerThread.setDaemon(true);
            writerThread.setName("ClassCache Writer Thread");
            writerThread.start();
            Runtime.getRuntime().addShutdownHook(new Thread(ClassCacheHandler::finishWriting));
        }
        configReaderInstance = null;
    }

    private static void finishWriting()
    {
        if (classCacheFileWriter == null)
        {
            return;
        }
        classCacheFileWriter.writeLast(writerThread);
        if (writerThread.isAlive())
        {
            try
            {
                classCacheFileWriter.latch.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                //Shrug
            }
        }
    }
}
