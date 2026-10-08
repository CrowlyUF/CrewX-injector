













#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <jni.h>
#include <jvmti.h>

#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>

#define CREWX_PRODUCT_JAR_RESOURCE_ID 421
#define CREWX_NAMED_JAR_RESOURCE_ID 423
#define CREWX_NOTCH_JAR_RESOURCE_ID 424

static HMODULE g_module = NULL;
static JavaVM *g_vm = NULL;
static jvmtiEnv *g_jvmti = NULL;

static SRWLOCK g_capture_lock = SRWLOCK_INIT;
static jclass g_capture_class = NULL;
static unsigned char *g_capture_bytes = NULL;
static jint g_capture_length = 0;

#define CREWX_MAX_PERSISTENT_PATCHES 96
typedef struct {
    jclass klass;
    unsigned char *bytes;
    jint length;
} crewx_persistent_patch;
static crewx_persistent_patch g_patches[CREWX_MAX_PERSISTENT_PATCHES];
static jint g_patch_count = 0;
static SRWLOCK g_patch_lock = SRWLOCK_INIT;

static void crewx_log(const wchar_t *format, ...) {
    wchar_t message[2048];
    wchar_t line[2304];
    wchar_t module_path[MAX_PATH];
    wchar_t log_path[MAX_PATH];
    wchar_t *separator;
    SYSTEMTIME now;
    FILE *file = NULL;
    va_list arguments;

    va_start(arguments, format);
    _vsnwprintf_s(message, sizeof(message) / sizeof(message[0]),
            _TRUNCATE, format, arguments);
    va_end(arguments);
    GetLocalTime(&now);
    _snwprintf_s(line, sizeof(line) / sizeof(line[0]), _TRUNCATE,
            L"[%04u-%02u-%02u %02u:%02u:%02u.%03u] %ls\r\n",
            now.wYear, now.wMonth, now.wDay, now.wHour, now.wMinute,
            now.wSecond, now.wMilliseconds, message);
    OutputDebugStringW(line);

    if (g_module == NULL) {
        return;
    }
    if (GetModuleFileNameW(g_module, module_path,
            (DWORD)(sizeof(module_path) / sizeof(module_path[0]))) == 0) {
        return;
    }
    separator = wcsrchr(module_path, L'\\');
    if (separator == NULL) {
        return;
    }
    *separator = L'\0';
    _snwprintf_s(log_path, sizeof(log_path) / sizeof(log_path[0]), _TRUNCATE,
            L"%ls\\crewx-native.log", module_path);
    if (_wfopen_s(&file, log_path, L"a, ccs=UTF-8") == 0 && file != NULL) {
        fputws(line, file);
        fclose(file);
    }
}

static void crewx_log_pending_exception(JNIEnv *env, const wchar_t *context) {
    jthrowable throwable;
    jclass throwable_class;
    jmethodID to_string;
    jstring text;
    const jchar *characters;
    jsize length;
    wchar_t buffer[1024];
    if (env == NULL || !(*env)->ExceptionCheck(env)) {
        crewx_log(L"%ls failed without a Java exception", context);
        return;
    }
    throwable = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    throwable_class = (*env)->FindClass(env, "java/lang/Throwable");
    to_string = throwable_class == NULL ? NULL : (*env)->GetMethodID(
            env, throwable_class, "toString", "()Ljava/lang/String;");
    text = to_string == NULL ? NULL : (jstring)(*env)->CallObjectMethod(
            env, throwable, to_string);
    if (text == NULL || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        crewx_log(L"%ls raised an unreadable Java exception", context);
        return;
    }
    characters = (*env)->GetStringChars(env, text, NULL);
    length = (*env)->GetStringLength(env, text);
    if (characters != NULL) {
        size_t copied = (size_t)length < 1023 ? (size_t)length : 1023;
        memcpy(buffer, characters, copied * sizeof(wchar_t));
        buffer[copied] = L'\0';
        (*env)->ReleaseStringChars(env, text, characters);
        crewx_log(L"%ls: %ls", context, buffer);
    }
}

static jstring new_wide_string(JNIEnv *env, const wchar_t *value) {
    if (value == NULL) {
        return NULL;
    }
    return (*env)->NewString(env, (const jchar *)value, (jsize)wcslen(value));
}

static int materialize_embedded_product_jar(
        int resource_id, wchar_t *jar_path, size_t jar_capacity) {
    HRSRC resource;
    HGLOBAL loaded_resource;
    const unsigned char *bytes;
    DWORD size;
    wchar_t temp_root[MAX_PATH];
    wchar_t temp_directory[MAX_PATH];
    HANDLE file = INVALID_HANDLE_VALUE;
    DWORD offset = 0;
    int result = 0;

    resource = FindResourceW(g_module,
            MAKEINTRESOURCEW(resource_id),
            MAKEINTRESOURCEW(10));
    if (resource == NULL) {
        crewx_log(L"embedded product JAR resource %d is missing",
                resource_id);
        return 0;
    }
    size = SizeofResource(g_module, resource);
    loaded_resource = LoadResource(g_module, resource);
    bytes = loaded_resource == NULL ? NULL
            : (const unsigned char *)LockResource(loaded_resource);
    if (bytes == NULL || size < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
        crewx_log(L"embedded product JAR resource is invalid");
        return 0;
    }
    if (GetTempPathW((DWORD)(sizeof(temp_root) / sizeof(temp_root[0])),
            temp_root) == 0) {
        crewx_log(L"GetTempPathW failed: %lu", GetLastError());
        return 0;
    }
    _snwprintf_s(temp_directory,
            sizeof(temp_directory) / sizeof(temp_directory[0]), _TRUNCATE,
            L"%lsCrewX", temp_root);
    if (!CreateDirectoryW(temp_directory, NULL)
            && GetLastError() != ERROR_ALREADY_EXISTS) {
        crewx_log(L"CreateDirectoryW failed: %lu", GetLastError());
        return 0;
    }
    if (_snwprintf_s(jar_path, jar_capacity, _TRUNCATE,
            L"%ls\\crewx-payload-%lu.jar", temp_directory,
            GetCurrentProcessId()) < 0) {
        crewx_log(L"temporary product JAR path is too long");
        return 0;
    }
    file = CreateFileW(jar_path, GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_DELETE, NULL, CREATE_ALWAYS,
            FILE_ATTRIBUTE_TEMPORARY, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        crewx_log(L"CreateFileW for embedded product JAR failed: %lu",
                GetLastError());
        return 0;
    }
    while (offset < size) {
        DWORD written = 0;
        DWORD remaining = size - offset;
        if (!WriteFile(file, bytes + offset, remaining, &written, NULL)
                || written == 0) {
            crewx_log(L"WriteFile for embedded product JAR failed: %lu",
                    GetLastError());
            goto cleanup;
        }
        offset += written;
    }
    if (!FlushFileBuffers(file)) {
        crewx_log(L"FlushFileBuffers for embedded product JAR failed: %lu",
                GetLastError());
        goto cleanup;
    }
    result = 1;

cleanup:
    CloseHandle(file);
    if (!result) {
        DeleteFileW(jar_path);
    } else {
        crewx_log(L"materialized embedded product JAR: %ls (%lu bytes)",
                jar_path, size);
    }
    return result;
}

static jobject find_loaded_minecraft_class_loader(JNIEnv *env) {
    jint count = 0;
    jclass *classes = NULL;
    jobject result = NULL;
    jint index;
    if (g_jvmti == NULL || (*g_jvmti)->GetLoadedClasses(g_jvmti, &count, &classes)
            != JVMTI_ERROR_NONE || classes == NULL) {
        return NULL;
    }
    for (index = 0; index < count; ++index) {
        char *signature = NULL;
        if ((*g_jvmti)->GetClassSignature(g_jvmti, classes[index], &signature, NULL)
                == JVMTI_ERROR_NONE && signature != NULL) {
            if (strcmp(signature, "Lnet/minecraft/client/Minecraft;") == 0
                    || strcmp(signature, "Lave;") == 0) {
                jobject class_loader = NULL;
                if ((*g_jvmti)->GetClassLoader(g_jvmti, classes[index], &class_loader)
                        == JVMTI_ERROR_NONE && class_loader != NULL) {
                    result = class_loader;
                    crewx_log(L"game classloader found from loaded Minecraft class");
                }
            }
            (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)signature);
        }
        (*env)->DeleteLocalRef(env, classes[index]);
        if (result != NULL) {
            ++index;
            break;
        }
    }
    for (; index < count; ++index) {
        (*env)->DeleteLocalRef(env, classes[index]);
    }
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)classes);
    return result;
}

static jobject find_client_class_loader(JNIEnv *env) {
    jint thread_count = 0;
    jthread *threads = NULL;
    jobject result = NULL;
    jvmtiError error;
    jint index;
    if (g_jvmti == NULL) {
        return NULL;
    }
    error = (*g_jvmti)->GetAllThreads(g_jvmti, &thread_count, &threads);
    if (error != JVMTI_ERROR_NONE || threads == NULL) {
        crewx_log(L"GetAllThreads failed: %d", error);
        return NULL;
    }
    for (index = 0; index < thread_count; ++index) {
        jvmtiThreadInfo info;
        memset(&info, 0, sizeof(info));
        if ((*g_jvmti)->GetThreadInfo(g_jvmti, threads[index], &info)
                == JVMTI_ERROR_NONE) {
            if (info.name != NULL
                    && (strcmp(info.name, "Client thread") == 0
                            || strcmp(info.name, "Render thread") == 0
                            || strcmp(info.name, "main") == 0)
                    && info.context_class_loader != NULL) {
                result = (*env)->NewLocalRef(env, info.context_class_loader);
            }
            if (info.name != NULL) {
                (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)info.name);
            }
            if (info.thread_group != NULL) {
                (*env)->DeleteLocalRef(env, info.thread_group);
            }
            if (info.context_class_loader != NULL) {
                (*env)->DeleteLocalRef(env, info.context_class_loader);
            }
        }
        (*env)->DeleteLocalRef(env, threads[index]);
        if (result != NULL) {
            break;
        }
    }
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)threads);
    return result;
}


static int payload_resource_for_loader(JNIEnv *env, jobject loader) {
    jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID load_class;
    jstring name;
    jclass minecraft_class;
    jmethodID method;
    if (loader_class == NULL) {
        (*env)->ExceptionClear(env);
        return 0;
    }
    load_class = (*env)->GetMethodID(env, loader_class, "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;");
    name = (*env)->NewStringUTF(env, "net.minecraft.client.Minecraft");
    if (load_class == NULL || name == NULL) {
        (*env)->ExceptionClear(env);
        return 0;
    }
    minecraft_class = (jclass)(*env)->CallObjectMethod(env, loader, load_class, name);
    (*env)->DeleteLocalRef(env, name);
    if (minecraft_class == NULL || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        name = (*env)->NewStringUTF(env, "ave");
        minecraft_class = name == NULL ? NULL
                : (jclass)(*env)->CallObjectMethod(env, loader, load_class, name);
        if (name != NULL) (*env)->DeleteLocalRef(env, name);
        if (minecraft_class == NULL || (*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            crewx_log(L"Minecraft class is not available in the game loader");
            return 0;
        }
        method = (*env)->GetStaticMethodID(env, minecraft_class, "A", "()Lave;");
        if (method != NULL && !(*env)->ExceptionCheck(env)) {
            crewx_log(L"detected vanilla/Notch Minecraft members; selecting Badlion payload");
            return CREWX_NOTCH_JAR_RESOURCE_ID;
        }
        (*env)->ExceptionClear(env);
        crewx_log(L"vanilla Minecraft member mapping is unsupported");
        return 0;
    }
    method = (*env)->GetStaticMethodID(env, minecraft_class, "getMinecraft",
            "()Lnet/minecraft/client/Minecraft;");
    if (method != NULL && !(*env)->ExceptionCheck(env)) {
        crewx_log(L"detected MCP-named Minecraft members; selecting Lunar payload");
        return CREWX_NAMED_JAR_RESOURCE_ID;
    }
    (*env)->ExceptionClear(env);
    method = (*env)->GetStaticMethodID(env, minecraft_class, "func_71410_x",
            "()Lnet/minecraft/client/Minecraft;");
    if (method != NULL && !(*env)->ExceptionCheck(env)) {
        crewx_log(L"detected SRG Minecraft members; selecting Forge payload");
        return CREWX_PRODUCT_JAR_RESOURCE_ID;
    }
    (*env)->ExceptionClear(env);
    crewx_log(L"Minecraft member mapping is unsupported; refusing injection");
    return 0;
}

static int add_jar_to_loader(
        JNIEnv *env, jobject *loader, const wchar_t *jar_path) {
    jclass class_loader_class;
    jmethodID get_system_loader;
    jobject system_loader;
    jclass url_loader_class;
    jclass url_class;
    jclass file_class;
    jclass uri_class;
    jmethodID add_url;
    jmethodID url_loader_init;
    jmethodID file_init;
    jmethodID to_uri;
    jmethodID to_url;
    jstring path;
    jobject file;
    jobject uri;
    jobject url;
    jobjectArray urls;
    jobject child_loader;

    class_loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    get_system_loader = class_loader_class == NULL ? NULL
            : (*env)->GetStaticMethodID(env, class_loader_class,
                    "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    system_loader = get_system_loader == NULL ? NULL
            : (*env)->CallStaticObjectMethod(env, class_loader_class, get_system_loader);
    if ((*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"resolve system ClassLoader");
        return 0;
    }
    if (system_loader != NULL && (*env)->IsSameObject(env, *loader, system_loader)) {
        int bytes = WideCharToMultiByte(CP_UTF8, 0, jar_path, -1, NULL, 0, NULL, NULL);
        char *utf8 = bytes <= 0 ? NULL : (char *)malloc((size_t)bytes);
        jvmtiError error;
        if (utf8 == NULL || WideCharToMultiByte(CP_UTF8, 0, jar_path, -1,
                utf8, bytes, NULL, NULL) == 0) {
            free(utf8);
            crewx_log(L"could not encode JAR path for JVMTI");
            return 0;
        }
        error = (*g_jvmti)->AddToSystemClassLoaderSearch(g_jvmti, utf8);
        free(utf8);
        if (error != JVMTI_ERROR_NONE) {
            crewx_log(L"AddToSystemClassLoaderSearch failed: %d", error);
            return 0;
        }
        crewx_log(L"appended product JAR to system ClassLoader search");
        return 1;
    }

    url_loader_class = (*env)->FindClass(env, "java/net/URLClassLoader");
    url_class = (*env)->FindClass(env, "java/net/URL");
    if (url_loader_class == NULL || url_class == NULL) {
        crewx_log_pending_exception(env, L"resolve URLClassLoader classes");
        return 0;
    }
    file_class = (*env)->FindClass(env, "java/io/File");
    uri_class = (*env)->FindClass(env, "java/net/URI");
    file_init = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "<init>", "(Ljava/lang/String;)V");
    to_uri = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "toURI", "()Ljava/net/URI;");
    to_url = uri_class == NULL ? NULL : (*env)->GetMethodID(
            env, uri_class, "toURL", "()Ljava/net/URL;");
    if (file_init == NULL || to_uri == NULL || to_url == NULL) {
        crewx_log_pending_exception(env, L"resolve product JAR URL methods");
        return 0;
    }
    path = new_wide_string(env, jar_path);
    file = path == NULL ? NULL : (*env)->NewObject(env, file_class, file_init, path);
    uri = file == NULL ? NULL : (*env)->CallObjectMethod(env, file, to_uri);
    url = uri == NULL ? NULL : (*env)->CallObjectMethod(env, uri, to_url);
    if (url == NULL || (*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"create product JAR URL");
        return 0;
    }
    if ((*env)->IsInstanceOf(env, *loader, url_loader_class)) {
        add_url = (*env)->GetMethodID(env, url_loader_class,
                "addURL", "(Ljava/net/URL;)V");
        if (add_url == NULL) {
            crewx_log_pending_exception(env, L"resolve URLClassLoader.addURL");
            return 0;
        }
        (*env)->CallVoidMethod(env, *loader, add_url, url);
        if ((*env)->ExceptionCheck(env)) {
            crewx_log_pending_exception(env, L"URLClassLoader.addURL");
            return 0;
        }
        crewx_log(L"appended product JAR to game URLClassLoader");
        return 1;
    }

    if (!(*env)->IsInstanceOf(env, *loader, url_loader_class)) {
        crewx_log(L"game ClassLoader is neither system nor URLClassLoader");
        return 0;
    }
    url_loader_init = (*env)->GetMethodID(env, url_loader_class, "<init>",
            "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    urls = (*env)->NewObjectArray(env, 1, url_class, NULL);
    if (url_loader_init == NULL || urls == NULL) {
        crewx_log_pending_exception(env, L"resolve child URLClassLoader constructor");
        return 0;
    }
    (*env)->SetObjectArrayElement(env, urls, 0, url);
    child_loader = (*env)->NewObject(env, url_loader_class, url_loader_init,
            urls, *loader);
    if (child_loader == NULL || (*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"create child product URLClassLoader");
        return 0;
    }
    (*env)->DeleteLocalRef(env, *loader);
    *loader = child_loader;
    crewx_log(L"created child URLClassLoader for game ClassLoader");
    return 1;
}

static int set_current_context_class_loader(JNIEnv *env, jobject loader) {
    jclass thread_class = (*env)->FindClass(env, "java/lang/Thread");
    jmethodID current_thread;
    jmethodID set_context_loader;
    jobject thread;
    if (thread_class == NULL) {
        crewx_log_pending_exception(env, L"resolve java.lang.Thread");
        return 0;
    }
    current_thread = (*env)->GetStaticMethodID(env, thread_class,
            "currentThread", "()Ljava/lang/Thread;");
    set_context_loader = (*env)->GetMethodID(env, thread_class,
            "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    if (current_thread == NULL || set_context_loader == NULL) {
        crewx_log_pending_exception(env, L"resolve Thread context ClassLoader methods");
        return 0;
    }
    thread = (*env)->CallStaticObjectMethod(env, thread_class, current_thread);
    if (thread == NULL || (*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"Thread.currentThread");
        return 0;
    }
    (*env)->CallVoidMethod(env, thread, set_context_loader, loader);
    if ((*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"Thread.setContextClassLoader");
        return 0;
    }
    return 1;
}

static int call_bootstrap_start(JNIEnv *env, jobject loader) {
    jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = (*env)->NewStringUTF(env, "crewx.inject.CrewXBootstrap");
    jclass bootstrap_class;
    jmethodID start;
    if (load_class == NULL || name == NULL) {
        crewx_log_pending_exception(env, L"resolve ClassLoader.loadClass");
        return 0;
    }
    bootstrap_class = (jclass)(*env)->CallObjectMethod(env, loader, load_class, name);
    if (bootstrap_class == NULL || (*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"load crewx.inject.CrewXBootstrap");
        return 0;
    }
    start = (*env)->GetStaticMethodID(env, bootstrap_class, "start", "()V");
    if (start == NULL) {
        crewx_log_pending_exception(env, L"resolve CrewXBootstrap.start");
        return 0;
    }
    (*env)->CallStaticVoidMethod(env, bootstrap_class, start);
    if ((*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"CrewXBootstrap.start");
        return 0;
    }
    return 1;
}

static jint initialize_jvmti(JavaVM *vm);
static void JNICALL class_file_load_hook(
        jvmtiEnv *jvmti_env,
        JNIEnv *env,
        jclass class_being_redefined,
        jobject loader,
        const char *name,
        jobject protection_domain,
        jint class_data_len,
        const unsigned char *class_data,
        jint *new_class_data_len,
        unsigned char **new_class_data);

static jint initialize_jvmti(JavaVM *vm) {
    jvmtiCapabilities potential;
    jvmtiCapabilities requested;
    jvmtiEventCallbacks callbacks;
    jint get_env_result;
    jvmtiError error;

    if (vm == NULL) {
        return JNI_ERR;
    }
    g_vm = vm;
    get_env_result = (*vm)->GetEnv(vm, (void **)&g_jvmti, JVMTI_VERSION_1_2);
    if (get_env_result != JNI_OK || g_jvmti == NULL) {
        crewx_log(L"JVMTI 1.2 is unavailable: %d", get_env_result);
        g_jvmti = NULL;
        return JNI_ERR;
    }
    memset(&potential, 0, sizeof(potential));
    memset(&requested, 0, sizeof(requested));
    error = (*g_jvmti)->GetPotentialCapabilities(g_jvmti, &potential);
    if (error != JVMTI_ERROR_NONE) {
        crewx_log(L"GetPotentialCapabilities failed: %d", error);
        return JNI_ERR;
    }

    requested.can_redefine_classes = potential.can_redefine_classes;
    requested.can_redefine_any_class = potential.can_redefine_any_class;
    requested.can_retransform_classes = potential.can_retransform_classes;
    requested.can_retransform_any_class = potential.can_retransform_any_class;
    requested.can_get_bytecodes = potential.can_get_bytecodes;
    error = (*g_jvmti)->AddCapabilities(g_jvmti, &requested);
    if (error != JVMTI_ERROR_NONE) {
        crewx_log(L"AddCapabilities failed: %d", error);
        return JNI_ERR;
    }

    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.ClassFileLoadHook = class_file_load_hook;
    error = (*g_jvmti)->SetEventCallbacks(g_jvmti, &callbacks, sizeof(callbacks));
    if (error != JVMTI_ERROR_NONE) {
        crewx_log(L"SetEventCallbacks failed: %d", error);
        return JNI_ERR;
    }
    return JNI_OK;
}

static void JNICALL class_file_load_hook(
        jvmtiEnv *jvmti_env,
        JNIEnv *env,
        jclass class_being_redefined,
        jobject loader,
        const char *name,
        jobject protection_domain,
        jint class_data_len,
        const unsigned char *class_data,
        jint *new_class_data_len,
        unsigned char **new_class_data) {
    unsigned char *copy;
    (void)jvmti_env;
    (void)loader;
    (void)name;
    (void)protection_domain;
    if (new_class_data_len != NULL) {
        *new_class_data_len = 0;
    }
    if (new_class_data != NULL) {
        *new_class_data = NULL;
    }
    if (env == NULL || class_being_redefined == NULL || class_data == NULL
            || class_data_len <= 0) {
        return;
    }
    if (g_capture_class != NULL
            && (*env)->IsSameObject(env, class_being_redefined, g_capture_class)) {
        copy = (unsigned char *)HeapAlloc(GetProcessHeap(), 0, (SIZE_T)class_data_len);
        if (copy == NULL) return;
        memcpy(copy, class_data, (size_t)class_data_len);
        if (g_capture_bytes != NULL) HeapFree(GetProcessHeap(), 0, g_capture_bytes);
        g_capture_bytes = copy;
        g_capture_length = class_data_len;
    }
    AcquireSRWLockShared(&g_patch_lock);
    for (jint i = 0; i < g_patch_count; ++i) {
        crewx_persistent_patch *patch = &g_patches[i];
        if (!(*env)->IsSameObject(env, class_being_redefined, patch->klass)) continue;
        unsigned char *replacement = NULL;
        if ((*g_jvmti)->Allocate(g_jvmti, (jlong)patch->length, &replacement)
                == JVMTI_ERROR_NONE && replacement != NULL) {
            memcpy(replacement, patch->bytes, (size_t)patch->length);
            *new_class_data_len = patch->length;
            *new_class_data = replacement;
            crewx_log(L"persistent hook applied (%d -> %d bytes)",
                    class_data_len, patch->length);
        }
        break;
    }
    ReleaseSRWLockShared(&g_patch_lock);
}

static jint JNICALL native_scb(
        JNIEnv *env, jclass bridge, jclass target, jbyteArray class_bytes) {
    jbyte *bytes;
    jsize length;
    jvmtiError error;
    (void)bridge;
    if (g_jvmti == NULL || target == NULL || class_bytes == NULL) {
        return JVMTI_ERROR_INVALID_ENVIRONMENT;
    }
    length = (*env)->GetArrayLength(env, class_bytes);
    bytes = (*env)->GetByteArrayElements(env, class_bytes, NULL);
    if (bytes == NULL) {
        return JVMTI_ERROR_OUT_OF_MEMORY;
    }
    AcquireSRWLockExclusive(&g_patch_lock);
    jint slot;
    for (slot = 0; slot < g_patch_count; ++slot) {
        if ((*env)->IsSameObject(env, target, g_patches[slot].klass)) break;
    }
    if (slot == CREWX_MAX_PERSISTENT_PATCHES) {
        ReleaseSRWLockExclusive(&g_patch_lock);
        (*env)->ReleaseByteArrayElements(env, class_bytes, bytes, JNI_ABORT);
        return JVMTI_ERROR_OUT_OF_MEMORY;
    }
    unsigned char *saved = (unsigned char *)HeapAlloc(GetProcessHeap(), 0, (SIZE_T)length);
    if (saved == NULL) {
        ReleaseSRWLockExclusive(&g_patch_lock);
        (*env)->ReleaseByteArrayElements(env, class_bytes, bytes, JNI_ABORT);
        return JVMTI_ERROR_OUT_OF_MEMORY;
    }
    memcpy(saved, bytes, (size_t)length);
    if (slot == g_patch_count) {
        g_patches[slot].klass = (jclass)(*env)->NewGlobalRef(env, target);
        if (g_patches[slot].klass == NULL) {
            HeapFree(GetProcessHeap(), 0, saved);
            ReleaseSRWLockExclusive(&g_patch_lock);
            (*env)->ReleaseByteArrayElements(env, class_bytes, bytes, JNI_ABORT);
            return JVMTI_ERROR_OUT_OF_MEMORY;
        }
        ++g_patch_count;
    } else if (g_patches[slot].bytes != NULL) {
        HeapFree(GetProcessHeap(), 0, g_patches[slot].bytes);
    }
    g_patches[slot].bytes = saved;
    g_patches[slot].length = length;
    ReleaseSRWLockExclusive(&g_patch_lock);
    (*g_jvmti)->SetEventNotificationMode(g_jvmti, JVMTI_ENABLE,
            JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);

    error = (*g_jvmti)->RetransformClasses(g_jvmti, 1, &target);
    (*env)->ReleaseByteArrayElements(env, class_bytes, bytes, JNI_ABORT);
    if (error != JVMTI_ERROR_NONE) {
        crewx_log(L"scb RetransformClasses failed: %d", error);
    }
    return error;
}


static jint JNICALL native_mbl(JNIEnv *env, jclass bridge, jclass target,
        jstring method_name, jstring signature) {
    const char *name = NULL;
    const char *sig = NULL;
    jmethodID method;
    jint length = -1;
    unsigned char *bytes = NULL;
    jvmtiError error;
    (void)bridge;
    if (g_jvmti == NULL || target == NULL || method_name == NULL || signature == NULL) {
        return -1;
    }
    name = (*env)->GetStringUTFChars(env, method_name, NULL);
    sig = (*env)->GetStringUTFChars(env, signature, NULL);
    if (name == NULL || sig == NULL) goto cleanup;
    method = (*env)->GetMethodID(env, target, name, sig);
    if (method == NULL) {
        (*env)->ExceptionClear(env);
        goto cleanup;
    }
    error = (*g_jvmti)->GetBytecodes(g_jvmti, method, &length, &bytes);
    if (error != JVMTI_ERROR_NONE) length = -1000 - (jint)error;
cleanup:
    if (bytes != NULL) (*g_jvmti)->Deallocate(g_jvmti, bytes);
    if (name != NULL) (*env)->ReleaseStringUTFChars(env, method_name, name);
    if (sig != NULL) (*env)->ReleaseStringUTFChars(env, signature, sig);
    return length;
}

static jbyteArray JNICALL native_gcb(JNIEnv *env, jclass bridge, jclass target) {
    jbyteArray result = NULL;
    jvmtiError error;
    (void)bridge;
    if (g_jvmti == NULL || target == NULL) {
        return NULL;
    }

    AcquireSRWLockExclusive(&g_capture_lock);
    if (g_capture_bytes != NULL) {
        HeapFree(GetProcessHeap(), 0, g_capture_bytes);
        g_capture_bytes = NULL;
    }
    g_capture_length = 0;
    g_capture_class = (jclass)(*env)->NewGlobalRef(env, target);
    if (g_capture_class == NULL) {
        ReleaseSRWLockExclusive(&g_capture_lock);
        return NULL;
    }

    error = (*g_jvmti)->SetEventNotificationMode(g_jvmti, JVMTI_ENABLE,
            JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
    if (error == JVMTI_ERROR_NONE) {
        error = (*g_jvmti)->RetransformClasses(g_jvmti, 1, &target);
        if (g_patch_count == 0) {
            (*g_jvmti)->SetEventNotificationMode(g_jvmti, JVMTI_DISABLE,
                    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
        }
    }
    if (error == JVMTI_ERROR_NONE && g_capture_bytes != NULL
            && g_capture_length > 0) {
        result = (*env)->NewByteArray(env, g_capture_length);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, g_capture_length,
                    (const jbyte *)g_capture_bytes);
        }
    } else {
        crewx_log(L"gcb RetransformClasses failed: %d (captured %d bytes)", error,
                g_capture_length);
    }

    (*env)->DeleteGlobalRef(env, g_capture_class);
    g_capture_class = NULL;
    if (g_capture_bytes != NULL) {
        HeapFree(GetProcessHeap(), 0, g_capture_bytes);
        g_capture_bytes = NULL;
    }
    g_capture_length = 0;
    ReleaseSRWLockExclusive(&g_capture_lock);
    return result;
}

static jint register_native_bridge(JNIEnv *env, jobject loader) {
    JNINativeMethod methods[] = {
        {"gcb", "(Ljava/lang/Class;)[B", (void *)native_gcb},
        {"scb", "(Ljava/lang/Class;[B)I", (void *)native_scb},
        {"mbl", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)I", (void *)native_mbl},
    };
    jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = (*env)->NewStringUTF(env, "crewx.inject.CrewXNative");
    jclass bridge_class;
    jint result;
    if (load_class == NULL || name == NULL) {
        crewx_log_pending_exception(env, L"resolve ClassLoader.loadClass");
        return JNI_ERR;
    }
    bridge_class = (jclass)(*env)->CallObjectMethod(env, loader, load_class, name);
    if (bridge_class == NULL || (*env)->ExceptionCheck(env)) {
        crewx_log_pending_exception(env, L"load crewx.inject.CrewXNative");
        return JNI_ERR;
    }
    result = (*env)->RegisterNatives(env, bridge_class, methods,
            (jint)(sizeof(methods) / sizeof(methods[0])));
    if (result != JNI_OK) {
        crewx_log_pending_exception(env, L"RegisterNatives CrewXNative");
        return result;
    }
    crewx_log(L"registered CrewXNative methods (gcb/scb)");
    return JNI_OK;
}

static DWORD WINAPI bootstrap_thread(LPVOID parameter) {
    HMODULE jvm_module;
    FARPROC created_vms_address;
    typedef jint (JNICALL *get_created_vms_fn)(JavaVM **, jsize, jsize *);
    get_created_vms_fn get_created_vms;
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jsize vm_count = 0;
    jobject loader = NULL;
    wchar_t jar_path[MAX_PATH];
    int attached = 0;
    int resource_id = 0;
    int attempt;
    DWORD exit_code = 1;
    (void)parameter;

    Sleep(150);
    for (attempt = 0; attempt < 600; ++attempt) {
        jvm_module = GetModuleHandleW(L"jvm.dll");
        if (jvm_module != NULL) break;
        Sleep(100);
    }
    if (jvm_module == NULL) {
        crewx_log(L"jvm.dll is not loaded");
        exit_code = 2;
        return exit_code;
    }
    created_vms_address = GetProcAddress(jvm_module, "JNI_GetCreatedJavaVMs");
    if (created_vms_address == NULL) {
        crewx_log(L"JNI_GetCreatedJavaVMs export is unavailable");
        exit_code = 3;
        return exit_code;
    }
    get_created_vms = (get_created_vms_fn)created_vms_address;
    for (attempt = 0; attempt < 600; ++attempt) {
        if (get_created_vms(&vm, 1, &vm_count) == JNI_OK
                && vm != NULL && vm_count >= 1) {
            break;
        }
        vm = NULL;
        vm_count = 0;
        Sleep(100);
    }
    if (vm == NULL || vm_count < 1) {
        crewx_log(L"JNI_GetCreatedJavaVMs returned no VM");
        exit_code = 4;
        return exit_code;
    }
    if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&env, NULL) != JNI_OK
            || env == NULL) {
        crewx_log(L"AttachCurrentThreadAsDaemon failed");
        exit_code = 5;
        return exit_code;
    }
    attached = 1;
    if (initialize_jvmti(vm) != JNI_OK) {
        goto cleanup;
    }
    for (attempt = 0; attempt < 600 && loader == NULL; ++attempt) {
        if (attempt % 10 == 0) {
            loader = find_loaded_minecraft_class_loader(env);
        }
        if (loader == NULL && attempt > 100) {
            loader = find_client_class_loader(env);
        }
        if (loader == NULL) {
            Sleep(100);
        }
    }
    if (loader == NULL) {
        crewx_log(L"game thread (Client/Render/main) was not found within 60 seconds");
        goto cleanup;
    }
    resource_id = payload_resource_for_loader(env, loader);
    if (resource_id == 0 || !materialize_embedded_product_jar(resource_id,
            jar_path, sizeof(jar_path) / sizeof(jar_path[0]))) {
        goto cleanup;
    }
    if (!add_jar_to_loader(env, &loader, jar_path)) {
        goto cleanup;
    }
    if (!set_current_context_class_loader(env, loader)) {
        goto cleanup;
    }
    crewx_log(L"CrewX payload linked from %ls", jar_path);
    if (register_native_bridge(env, loader) != JNI_OK) {
        goto cleanup;
    }
    if (!call_bootstrap_start(env, loader)) {
        goto cleanup;
    }
    crewx_log(L"CrewXBootstrap.start completed; injection is active");
    exit_code = 0;

cleanup:
    if (attached) {
        (*vm)->DetachCurrentThread(vm);
    }
    if (exit_code != 0) {
        crewx_log(L"bootstrap did not complete; code=%lu", exit_code);
    }
    return exit_code;
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    HANDLE thread;
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        g_module = instance;
        DisableThreadLibraryCalls(instance);
        thread = CreateThread(NULL, 0, bootstrap_thread, instance, 0, NULL);
        if (thread != NULL) {
            CloseHandle(thread);
        }
    }
    return TRUE;
}
