package ai.apptest.xpcoco;
import android.content.pm.*;
import android.os.Environment;
import android.util.*;
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


public class MethodTracer implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private String TAG_EXECUTION = "ExCoCo_E";
    private String TAG_INJECTION = "ExCoCo_I";
    public static final String THIS_PACKAGE_NAME = MethodTracer.class.getPackage().getName();
    public static XSharedPreferences sPrefs;
    HashMap<String, Boolean> InjectedHash;

    public void initZygote(StartupParam startupParam) throws Throwable {
        InjectedHash = new HashMap<String, Boolean>();
        sPrefs = new XSharedPreferences(THIS_PACKAGE_NAME, "Configuration");
        sPrefs.makeWorldReadable();
    }

    private void deleteLogFile(){
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ExCoCo/coco_e.txt";
        File file = new File(path);
        if (file.exists())
            if(!file.delete())
                XposedBridge.log("Can't delete file: " + path );
            else
                XposedBridge.log("Log file is deleted : " + path );
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ExCoCo/coco_e.txt";
        file = new File(path);
        if (file.exists())
            if(!file.delete())
                XposedBridge.log("Can't delete file: " + path );
            else
                XposedBridge.log("Log file is deleted : " + path );
    }

    private Boolean isAbstractMethod(Method method){
        String[] methodDescription = method.toString().split(" ");
        for ( String Description : methodDescription)
            if( Description.equals("abstract")) return true;
        return false;
    }

    private Boolean isjnected(String className, String MethodName) {
        if(InjectedHash.containsKey(className + "::" + MethodName ))
            return true;
        else
            return false;
    }

    private void CheckAsInjected (String className, String MethodName) {
        InjectedHash.put(className + "::" + MethodName, true);
    }

    private Boolean writeToFile(String tag, String log){
        String absolutePath;
        if(tag.equals(TAG_EXECUTION)){
            absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ExCoCo/coco_e.txt";
        }else{
            absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ExCoCo/coco_i.txt";
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

        String injectionTarget = sPrefs.getString("InjectionTarget", null );

        if (!lpparam.packageName.equals(injectionTarget)){
            return;
        }

        findAndHookMethod("android.util.Log", lpparam.classLoader, "e", String.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String tag = (String) param.args[0];
                String log = (String) param.args[1];
                if (tag.equals(TAG_EXECUTION) || tag.equals(TAG_INJECTION))
                    writeToFile(tag, log);
            }
        });

        ApplicationInfo applicationInfo = AndroidAppHelper.currentApplicationInfo();
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

            deleteLogFile();
            for (String className : classes){
                if (className.startsWith(injectionTarget)){
                    try{
                        Class clazz = lpparam.classLoader.loadClass(className);
                        for (Method method : clazz.getDeclaredMethods()){
                            if(!method.getDeclaringClass().getName().equals(className)) continue;
                            if(this.isAbstractMethod(method)) continue;

                            String methodName = method.getName() ;
                            if(isjnected(className, methodName)) continue;
                            final String traceLog = className  + '\t' + methodName ;
                            CheckAsInjected(className, methodName);
                            Log.e(TAG_INJECTION, traceLog);

                            XposedBridge.hookAllMethods(clazz, method.getName(), new XC_MethodHook() {
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