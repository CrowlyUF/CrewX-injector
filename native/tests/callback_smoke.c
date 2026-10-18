#include "../dllmain.c"

typedef jint (JNICALL *create_vm_fn)(JavaVM **, void **, void *);
int main(int argc, char **argv) {
    HMODULE jvm;
    create_vm_fn create_vm;
    JNIEnv *env = NULL;
    JavaVM *vm = NULL;
    JavaVMOption option;
    JavaVMInitArgs args;
    jclass probe;
    jmethodID run;
    int result = 0;
    JNINativeMethod natives[] = {
        {"trc", "(Ljava/lang/Class;Ljava/lang/String;)I", (void *)native_trc},
        {"gcb", "(Ljava/lang/Class;)[B", (void *)native_gcb},
        {"scb", "(Ljava/lang/Class;[B)I", (void *)native_scb}
    };
    if (argc != 2) return 2;
    jvm = LoadLibraryA(argv[1]);
    if (!jvm) {fprintf(stderr,"JVM load failed: %lu\n",GetLastError());return 3;}
    create_vm = (create_vm_fn)GetProcAddress(jvm,"JNI_CreateJavaVM");
    memset(&args,0,sizeof(args));
    option.optionString = "-Djava.class.path=.";
    args.version = JNI_VERSION_1_8; args.nOptions = 1; args.options = &option;
    if (!create_vm || create_vm(&vm,(void **)&env,&args) != JNI_OK) return 4;
    if (initialize_jvmti(vm) != JNI_OK) return 5;
    probe = (*env)->FindClass(env,"CallbackSmoke");
    if (!probe || (*env)->RegisterNatives(env,probe,natives,3) != JNI_OK) {
        (*env)->ExceptionDescribe(env);return 6;
    }
    run = (*env)->GetStaticMethodID(env,probe,"run","()V");
    (*env)->CallStaticVoidMethod(env,probe,run);
    if ((*env)->ExceptionCheck(env)) {(*env)->ExceptionDescribe(env);result=7;}
    (*vm)->DestroyJavaVM(vm);
    return result;
}
