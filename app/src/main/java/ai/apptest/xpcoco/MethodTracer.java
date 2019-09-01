package ai.apptest.xpcoco;
import android.content.pm.*;
import android.util.Log;
import dalvik.system.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.IXposedHookZygoteInit;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import android.app.*;
import android.os.Handler;
import android.os.Looper;


public class MethodTracer implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private String TAG_EXECUTION = "ExCoCo_E";
    private String TAG_INJECTION = "ExCoCo_I";
    public static final String THIS_PACKAGE_NAME = MethodTracer.class.getPackage().getName();
    public static XSharedPreferences sPrefs;

    public void initZygote(StartupParam startupParam) throws Throwable {
        sPrefs = new XSharedPreferences(THIS_PACKAGE_NAME, "Configuration");
        sPrefs.makeWorldReadable();
    }

    private void deleteLogFile(String logdir ){
        String path = logdir + "/ExCoCo/coco_i.txt";
        File file = new File(path);
        if (file.exists())
            if(!file.delete())
                XposedBridge.log("Can't delete file: " + path );
            else
                XposedBridge.log("Log file is deleted : " + path );
        path = logdir + "/ExCoCo/coco_e.txt";
        file = new File(path);
        if (file.exists())
            if(!file.delete())
                XposedBridge.log("Can't delete file: " + path );
            else
                XposedBridge.log("Log file is deleted : " + path );
    }

    private Boolean isAbstractMethod(String methodFullName){
        String[] methodDescription = methodFullName.split(" ");
        for ( String Description : methodDescription)
            if( Description.equals("abstract")) return true;
        return false;
    }

    private Boolean isExcluded(String className) {
        String[] ExcludeTarget = {"android.", "com.android.", "com.google.android.",
                "com.google.dexmaker.", "org.apache.commons.", "org.apache.http.*",
                "org.apache.log4j.*"};
        for (String excludedClassName : ExcludeTarget){
            if (className.startsWith(excludedClassName)) return true;
        }
        return false;
    }

    private Boolean writeToFile(String logDir, String tag, String log){
        String absolutePath;
        if(tag.equals(TAG_EXECUTION)){
            absolutePath = logDir + "/ExCoCo/coco_e.txt";
        }else{
            absolutePath = logDir + "/ExCoCo/coco_i.txt";
        }

        try {
            File file = new File(absolutePath);
            if (!file.exists()) {
                File path = new File(String.valueOf(file.getParentFile()));
                path.setReadable(true, false);
                path.setExecutable(true, false);
                path.setWritable(true, false);

                path.mkdirs();
                path.setReadable(true, false);
                path.setExecutable(true, false);
                path.setWritable(true, false);

                file.createNewFile();
                file.setReadable(true, false);
                file.setExecutable(true, false);
                file.setWritable(true, false);
            }

            FileOutputStream fOut = new FileOutputStream(file, true);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.write(log + "\n");
            myOutWriter.close();
            fOut.close();

        } catch (Exception e) {
            System.out.println("absolutePath: " + absolutePath);
            e.printStackTrace();
        }

        return true;
    }

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable{
        sPrefs.reload();
        //check if this module is enable
        if (lpparam.packageName.equals("ai.apptest.xpcoco")) {
            //workaround to bypass MODE_PRIVATE of shared_prefs
            findAndHookMethod("android.app.SharedPreferencesImpl.EditorImpl", lpparam.classLoader, "notifyListeners",
                    "android.app.SharedPreferencesImpl.MemoryCommitResult", new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            //workaround to bypass the concurrency (io)
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.postDelayed(new Runnable() {
                                public void run() {
                                    File folder = new File("/data/data/ai.apptest.xpcoco/");
                                    folder.setExecutable(true, false);
                                    String mPrefFile = "/data/data/ai.apptest.xpcoco/shared_prefs/Configuration.xml";
                                    (new File(mPrefFile)).setReadable(true, false);
                                }
                            }, 1000);
                        }
                    });
        }

        if(lpparam.packageName.equals("ai.apptest.xpcoco")) return;

        String injectionTarget = sPrefs.getString("InjectionTarget", null );
        XposedBridge.log("injection Target : " + injectionTarget );
        if (!lpparam.packageName.equals(injectionTarget)) return;

        ApplicationInfo applicationInfo = AndroidAppHelper.currentApplicationInfo();

        final String logDir = applicationInfo.dataDir;
        XposedBridge.log("logdir is: " + logDir );
        File folder = new File(logDir);
        folder.setExecutable(true, false);
        deleteLogFile(logDir);

        findAndHookMethod("android.util.Log", lpparam.classLoader, "e", String.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String tag = (String) param.args[0];
                String log = (String) param.args[1];
                if (tag.equals(TAG_EXECUTION) || tag.equals(TAG_INJECTION))
                    writeToFile(logDir, tag, log);
            }
        });

        if (applicationInfo.processName.equals(injectionTarget)){
            Set<String> classes = new HashSet<>();
            DexFile dex;
            try{
                dex = new DexFile(applicationInfo.sourceDir);
                Enumeration entries = dex.entries();
                while (entries.hasMoreElements()){
                    String entry = (String) entries.nextElement();
                    classes.add(entry);
                }
                dex.close();
            }
            catch (IOException e){
                Log.e(TAG_INJECTION, e.toString());
            }

            String[] PackageNames = injectionTarget.split("\\.");
//            String SubPackageName = PackageNames [0] + "." + PackageNames[1];
            String SubPackageName = PackageNames[0];
            XposedBridge.log("sub Package name is : " + SubPackageName );

            HashMap<String, Boolean> InjectedHash = new HashMap<String, Boolean>();
            for (String className : classes){
//                if (className.startsWith(SubPackageName)){
                if(!isExcluded(className)){
                    try{
                        Class clazz = lpparam.classLoader.loadClass(className);
                        for (Method method : clazz.getDeclaredMethods()){
                            if(!method.getDeclaringClass().getName().equals(className)) continue;

                            String methodDescription = method.toString();
                            if(this.isAbstractMethod(methodDescription)) continue;
                            if(InjectedHash.containsKey(methodDescription)) continue;
                            InjectedHash.put(methodDescription, true);

                            String methodName = method.getName ();
                            final String traceLog = className  + '\t' + methodName ;
                            Log.e(TAG_INJECTION, traceLog);

                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Log.e(TAG_EXECUTION,traceLog);
                                }
                            });
                        }
                    }catch (Exception e) {
                        Log.e(TAG_INJECTION, e.toString());
                    }
                }
            }
        }
    }
}