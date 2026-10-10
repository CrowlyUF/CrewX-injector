#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <tlhelp32.h>

#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

#define MAX_CANDIDATES 256
#define WINDOW_TITLE_CAPACITY 256
#define REFRESH_INTERVAL_MS 750

typedef struct process_candidate {
    DWORD process_id;
    wchar_t executable[MAX_PATH];
    wchar_t title[WINDOW_TITLE_CAPACITY];
} process_candidate;

typedef struct window_search_context {
    process_candidate *candidates;
    size_t count;
} window_search_context;

static FILE *g_inject_log;

static void log_inject(const wchar_t *format, ...) {
    va_list arguments;
    if (g_inject_log == NULL) return;
    va_start(arguments, format);
    vfwprintf(g_inject_log, format, arguments);
    va_end(arguments);
    fputwc(L'\n', g_inject_log);
    fflush(g_inject_log);
}

static void open_inject_log(void) {
    wchar_t temp[MAX_PATH];
    wchar_t directory[MAX_PATH];
    wchar_t path[MAX_PATH];
    if (GetTempPathW(MAX_PATH, temp) == 0) return;
    if (swprintf_s(directory, MAX_PATH, L"%lsCrewX21", temp) < 0) return;
    CreateDirectoryW(directory, NULL);
    if (swprintf_s(path, MAX_PATH, L"%ls\\injector.log", directory) < 0) return;
    _wfopen_s(&g_inject_log, path, L"a, ccs=UTF-8");
}

static void print_last_error(const wchar_t *operation) {
    DWORD error = GetLastError();
    wchar_t *message = NULL;
    FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER
                    | FORMAT_MESSAGE_FROM_SYSTEM
                    | FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL, error, 0, (wchar_t *)&message, 0, NULL);
    fwprintf(stderr, L"%ls failed (%lu): %ls\n", operation,
            (unsigned long)error, message == NULL ? L"unknown error" : message);
    log_inject(L"%ls failed (%lu): %ls", operation,
            (unsigned long)error, message == NULL ? L"unknown error" : message);
    if (message != NULL) {
        LocalFree(message);
    }
}

static int absolute_existing_file(
        const wchar_t *input, wchar_t *output, DWORD capacity) {
    DWORD length = GetFullPathNameW(input, capacity, output, NULL);
    DWORD attributes;
    if (length == 0 || length >= capacity) {
        return 0;
    }
    attributes = GetFileAttributesW(output);
    return attributes != INVALID_FILE_ATTRIBUTES
            && (attributes & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

static int stage_embedded_dll(DWORD target_pid, wchar_t *output, DWORD capacity) {
    HRSRC resource = FindResourceW(NULL, MAKEINTRESOURCEW(422), MAKEINTRESOURCEW(10));
    HGLOBAL loaded;
    const unsigned char *bytes;
    DWORD size;
    DWORD hash = 2166136261u;
    wchar_t temp[MAX_PATH];
    wchar_t directory[MAX_PATH];
    HANDLE file;
    DWORD written = 0;
    DWORD i;
    if (resource == NULL) return 0;
    loaded = LoadResource(NULL, resource);
    bytes = loaded == NULL ? NULL : (const unsigned char *)LockResource(loaded);
    size = SizeofResource(NULL, resource);
    if (bytes == NULL || size == 0) return 0;
    for (i = 0; i < size; ++i) hash = (hash ^ bytes[i]) * 16777619u;
    if (GetTempPathW(MAX_PATH, temp) == 0) return 0;
    if (swprintf_s(directory, MAX_PATH, L"%lsCrewX21", temp) < 0) return 0;
    if (!CreateDirectoryW(directory, NULL) && GetLastError() != ERROR_ALREADY_EXISTS) return 0;
    if (swprintf_s(output, capacity, L"%ls\\CrewXNative_%lu_%08lx.dll",
            directory, (unsigned long)target_pid, (unsigned long)hash) < 0) return 0;
    file = CreateFileW(output, GENERIC_WRITE, FILE_SHARE_READ, NULL, CREATE_NEW,
            FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) return GetLastError() == ERROR_FILE_EXISTS;
    if (!WriteFile(file, bytes, size, &written, NULL) || written != size) {
        CloseHandle(file);
        DeleteFileW(output);
        return 0;
    }
    CloseHandle(file);
    return 1;
}

static int is_java_process(const wchar_t *executable) {
    return _wcsicmp(executable, L"java.exe") == 0
            || _wcsicmp(executable, L"javaw.exe") == 0;
}

static BOOL CALLBACK capture_window_title(HWND window, LPARAM parameter) {
    window_search_context *context = (window_search_context *)parameter;
    wchar_t title[WINDOW_TITLE_CAPACITY];
    DWORD process_id = 0;
    size_t index;
    if (!IsWindowVisible(window) || GetWindowTextLengthW(window) == 0) {
        return TRUE;
    }
    GetWindowThreadProcessId(window, &process_id);
    if (process_id == 0
            || GetWindowTextW(window, title, WINDOW_TITLE_CAPACITY) == 0) {
        return TRUE;
    }
    for (index = 0; index < context->count; ++index) {
        process_candidate *candidate = &context->candidates[index];
        if (candidate->process_id == process_id && candidate->title[0] == L'\0') {
            wcscpy(candidate->title, title);
            break;
        }
    }
    return TRUE;
}

static int compare_candidates(const void *left, const void *right) {
    const process_candidate *a = (const process_candidate *)left;
    const process_candidate *b = (const process_candidate *)right;
    int a_visible = a->title[0] != L'\0';
    int b_visible = b->title[0] != L'\0';
    int a_javaw = _wcsicmp(a->executable, L"javaw.exe") == 0;
    int b_javaw = _wcsicmp(b->executable, L"javaw.exe") == 0;
    if (a_visible != b_visible) return b_visible - a_visible;
    if (a_javaw != b_javaw) return b_javaw - a_javaw;
    if (a->process_id > b->process_id) return -1;
    if (a->process_id < b->process_id) return 1;
    return 0;
}

static size_t enumerate_candidates(
        process_candidate *candidates, size_t capacity) {
    HANDLE snapshot;
    PROCESSENTRY32W entry;
    size_t count = 0;
    window_search_context context;

    snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Process32FirstW(snapshot, &entry)) {
        do {
            if (count < capacity && is_java_process(entry.szExeFile)) {
                process_candidate *candidate = &candidates[count++];
                memset(candidate, 0, sizeof(*candidate));
                candidate->process_id = entry.th32ProcessID;
                wcsncpy(candidate->executable, entry.szExeFile, MAX_PATH - 1);
            }
        } while (Process32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);

    context.candidates = candidates;
    context.count = count;
    EnumWindows(capture_window_title, (LPARAM)&context);



    {
        size_t index;
        size_t kept = 0;
        int visible_game = 0;
        for (index = 0; index < count; ++index) {
            if (candidates[index].title[0] != L'\0') visible_game = 1;
        }
        if (visible_game) {
            for (index = 0; index < count; ++index) {
                if (candidates[index].title[0] != L'\0'
                        || _wcsicmp(candidates[index].executable, L"javaw.exe") == 0) {
                    candidates[kept++] = candidates[index];
                }
            }
            count = kept;
        }
    }
    qsort(candidates, count, sizeof(*candidates), compare_candidates);
    return count;
}

#define SELECTOR_WIDTH 740
#define SELECTOR_HEIGHT 420
#define CARD_LEFT 190
#define CARD_TOP 174
#define CARD_WIDTH 360
#define CARD_HEIGHT 50
#define CARD_GAP 10
#define VISIBLE_CARDS 3

typedef struct selector_state {
    process_candidate candidates[MAX_CANDIDATES];
    size_t count;
    DWORD selected_process_id;
    size_t scroll_offset;
} selector_state;

static selector_state g_selector;
static int g_hovered_card = -1;

static void fill_round_rect(HDC dc, int left, int top, int right, int bottom,
        int radius, COLORREF color) {
    HBRUSH brush = CreateSolidBrush(color);
    HGDIOBJ old_brush = SelectObject(dc, brush);
    HGDIOBJ old_pen = SelectObject(dc, GetStockObject(NULL_PEN));
    RoundRect(dc, left, top, right, bottom, radius, radius);
    SelectObject(dc, old_pen);
    SelectObject(dc, old_brush);
    DeleteObject(brush);
}

static void centered_text(HDC dc, const wchar_t *text, int y, int width) {
    SIZE size;
    GetTextExtentPoint32W(dc, text, (int)wcslen(text), &size);
    TextOutW(dc, (width - size.cx) / 2, y, text, (int)wcslen(text));
}

static void refresh_selector(void) {
    size_t index;
    size_t visible_count = 0;
    g_selector.count = enumerate_candidates(g_selector.candidates, MAX_CANDIDATES);
    for (index = 0; index < g_selector.count; ++index) {
        process_candidate *candidate = &g_selector.candidates[index];
        if (candidate->title[0] == L'\0') continue;
        if (visible_count != index) {
            g_selector.candidates[visible_count] = *candidate;
        }
        ++visible_count;
    }
    g_selector.count = visible_count;
    if (g_selector.scroll_offset >= g_selector.count) g_selector.scroll_offset = 0;
}

static void paint_selector(HWND window) {
    PAINTSTRUCT paint;
    HDC dc = BeginPaint(window, &paint);
    RECT full;
    RECT card;
    HFONT title_font = CreateFontW(-58, 0, 0, 0, FW_BOLD, FALSE, FALSE,
            FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
            CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    HFONT card_font = CreateFontW(-14, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE,
            FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
            CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    HFONT detail_font = CreateFontW(-12, 0, 0, 0, FW_NORMAL, FALSE, FALSE,
            FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
            CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    HFONT control_font = CreateFontW(-15, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE,
            FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
            CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    HFONT previous_font;
    HBRUSH brush;
    size_t index;
    GetClientRect(window, &full);
    brush = CreateSolidBrush(RGB(42, 42, 43));
    FillRect(dc, &full, brush);
    DeleteObject(brush);
    {
        POINT top_accent[] = {{0, 0}, {136, 0}, {78, 98}, {0, 143}};
        POINT bottom_accent[] = {{580, 420}, {740, 275}, {740, 420}};
        HBRUSH accent = CreateSolidBrush(RGB(35, 35, 36));
        HGDIOBJ previous_brush = SelectObject(dc, accent);
        HGDIOBJ previous_pen = SelectObject(dc, GetStockObject(NULL_PEN));
        Polygon(dc, top_accent, 4);
        Polygon(dc, bottom_accent, 3);
        SelectObject(dc, previous_pen);
        SelectObject(dc, previous_brush);
        DeleteObject(accent);
    }
    {
        HPEN border = CreatePen(PS_SOLID, 1, RGB(91, 91, 95));
        HGDIOBJ previous_pen = SelectObject(dc, border);
        HGDIOBJ previous_brush = SelectObject(dc, GetStockObject(NULL_BRUSH));
        RoundRect(dc, 1, 1, SELECTOR_WIDTH - 1, SELECTOR_HEIGHT - 1, 12, 12);
        SelectObject(dc, previous_brush);
        SelectObject(dc, previous_pen);
        DeleteObject(border);
    }
    SetBkMode(dc, TRANSPARENT);
    previous_font = (HFONT)SelectObject(dc, title_font);
    {
        SIZE crew_size, x_size;
        int title_x;
        GetTextExtentPoint32W(dc, L"Crew", 4, &crew_size);
        GetTextExtentPoint32W(dc, L"X", 1, &x_size);
        title_x = (SELECTOR_WIDTH - crew_size.cx - x_size.cx) / 2;
        SetTextColor(dc, RGB(249, 249, 250));
        TextOutW(dc, title_x, 23, L"Crew", 4);
        SetTextColor(dc, RGB(230, 63, 68));
        TextOutW(dc, title_x + crew_size.cx, 23, L"X", 1);
    }
    SelectObject(dc, detail_font);
    SetTextColor(dc, RGB(223, 223, 225));
    centered_text(dc, L"Selecione o Minecraft para injetar", 111, SELECTOR_WIDTH);
    SetTextColor(dc, RGB(150, 150, 154));
    centered_text(dc, L"Aguarde o jogo carregar antes de continuar", 128, SELECTOR_WIDTH);
    fill_round_rect(dc, SELECTOR_WIDTH - 68, 12, SELECTOR_WIDTH - 40, 36,
            6, RGB(48, 48, 50));
    fill_round_rect(dc, SELECTOR_WIDTH - 36, 12, SELECTOR_WIDTH - 8, 36,
            6, RGB(48, 48, 50));
    SelectObject(dc, control_font);
    SetTextColor(dc, RGB(180, 180, 184));
    TextOutW(dc, SELECTOR_WIDTH - 60, 12, L"–", 1);
    TextOutW(dc, SELECTOR_WIDTH - 28, 12, L"×", 1);
    SelectObject(dc, detail_font);
    if (g_selector.count == 0) {
        SetTextColor(dc, RGB(160, 160, 165));
        centered_text(dc, L"Aguardando o Minecraft...", CARD_TOP + 18, SELECTOR_WIDTH);
    }
    for (index = g_selector.scroll_offset;
            index < g_selector.count && index < g_selector.scroll_offset + VISIBLE_CARDS;
            ++index) {
        wchar_t pid_label[40];
        int row = (int)(index - g_selector.scroll_offset);
        int top = CARD_TOP + row * (CARD_HEIGHT + CARD_GAP);
        card.left = CARD_LEFT;
        card.top = top;
        card.right = CARD_LEFT + CARD_WIDTH;
        card.bottom = top + CARD_HEIGHT;
        fill_round_rect(dc, card.left, card.top, card.right, card.bottom, 8,
                row == g_hovered_card ? RGB(78, 78, 81) : RGB(67, 67, 70));
        SetTextColor(dc, RGB(232, 232, 235));
        SelectObject(dc, card_font);
        card.left += 12;
        card.top += 7;
        card.right -= 12;
        card.bottom = card.top + 18;
        DrawTextW(dc, g_selector.candidates[index].title, -1, &card,
                DT_LEFT | DT_SINGLELINE | DT_END_ELLIPSIS);
        swprintf_s(pid_label, 40, L"PID %lu",
                (unsigned long)g_selector.candidates[index].process_id);
        SelectObject(dc, detail_font);
        SetTextColor(dc, RGB(173, 173, 177));
        TextOutW(dc, CARD_LEFT + 12, top + 30, pid_label, (int)wcslen(pid_label));
    }
    SelectObject(dc, previous_font);
    DeleteObject(title_font);
    DeleteObject(card_font);
    DeleteObject(detail_font);
    DeleteObject(control_font);
    EndPaint(window, &paint);
}

static LRESULT CALLBACK selector_window_proc(HWND window, UINT message,
        WPARAM wparam, LPARAM lparam) {
    switch (message) {
        case WM_CREATE:
            refresh_selector();
            SetTimer(window, 1, REFRESH_INTERVAL_MS, NULL);
            return 0;
        case WM_TIMER:
            refresh_selector();
            InvalidateRect(window, NULL, FALSE);
            return 0;
        case WM_ERASEBKGND:
            return 1;
        case WM_MOUSEMOVE: {
            int x = (short)LOWORD(lparam);
            int y = (short)HIWORD(lparam);
            int hovered = -1;
            if (x >= CARD_LEFT && x < CARD_LEFT + CARD_WIDTH && y >= CARD_TOP) {
                int row = (y - CARD_TOP) / (CARD_HEIGHT + CARD_GAP);
                if (row < VISIBLE_CARDS
                        && (y - CARD_TOP) % (CARD_HEIGHT + CARD_GAP) < CARD_HEIGHT
                        && g_selector.scroll_offset + (size_t)row < g_selector.count) {
                    hovered = row;
                }
            }
            if (hovered != g_hovered_card) {
                g_hovered_card = hovered;
                InvalidateRect(window, NULL, FALSE);
            }
            return 0;
        }
        case WM_MOUSEWHEEL:
            if (g_selector.count > VISIBLE_CARDS) {
                int delta = GET_WHEEL_DELTA_WPARAM(wparam);
                if (delta < 0 && g_selector.scroll_offset + VISIBLE_CARDS < g_selector.count)
                    ++g_selector.scroll_offset;
                if (delta > 0 && g_selector.scroll_offset > 0)
                    --g_selector.scroll_offset;
                InvalidateRect(window, NULL, FALSE);
            }
            return 0;
        case WM_LBUTTONDOWN: {
            int x = (short)LOWORD(lparam);
            int y = (short)HIWORD(lparam);
            if (y >= 12 && y < 36 && x >= SELECTOR_WIDTH - 36 && x < SELECTOR_WIDTH - 8) {
                DestroyWindow(window);
                return 0;
            }
            if (y >= 12 && y < 36 && x >= SELECTOR_WIDTH - 68 && x < SELECTOR_WIDTH - 40) {
                ShowWindow(window, SW_MINIMIZE);
                return 0;
            }
            if (x >= CARD_LEFT && x < CARD_LEFT + CARD_WIDTH && y >= CARD_TOP) {
                size_t row = (size_t)((y - CARD_TOP) / (CARD_HEIGHT + CARD_GAP));
                size_t index = g_selector.scroll_offset + row;
                if (row < VISIBLE_CARDS && index < g_selector.count
                        && (y - CARD_TOP) % (CARD_HEIGHT + CARD_GAP) < CARD_HEIGHT) {
                    g_selector.selected_process_id = g_selector.candidates[index].process_id;
                    DestroyWindow(window);
                }
            }
            ReleaseCapture();
            SendMessageW(window, WM_NCLBUTTONDOWN, HTCAPTION, 0);
            return 0;
        }
        case WM_PAINT:
            paint_selector(window);
            return 0;
        case WM_DESTROY:
            KillTimer(window, 1);
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(window, message, wparam, lparam);
}

static DWORD select_process(const wchar_t *dll_path) {
    WNDCLASSW window_class;
    HWND window;
    MSG message;
    (void)dll_path;
    memset(&g_selector, 0, sizeof(g_selector));
    memset(&window_class, 0, sizeof(window_class));
    window_class.lpfnWndProc = selector_window_proc;
    window_class.hInstance = GetModuleHandleW(NULL);
    window_class.lpszClassName = L"CrewXProcessSelector";
    window_class.hCursor = LoadCursorW(NULL, MAKEINTRESOURCEW(32512));
    window_class.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    if (!RegisterClassW(&window_class) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) return 0;
    window = CreateWindowExW(WS_EX_APPWINDOW, window_class.lpszClassName,
            L"CrewX", WS_POPUP | WS_SYSMENU | WS_MINIMIZEBOX,
            (GetSystemMetrics(SM_CXSCREEN) - SELECTOR_WIDTH) / 2,
            (GetSystemMetrics(SM_CYSCREEN) - SELECTOR_HEIGHT) / 2,
            SELECTOR_WIDTH, SELECTOR_HEIGHT, NULL, NULL,
            window_class.hInstance, NULL);
    if (window == NULL) return 0;
    SetWindowRgn(window, CreateRoundRectRgn(0, 0, SELECTOR_WIDTH + 1,
            SELECTOR_HEIGHT + 1, 12, 12), TRUE);
    ShowWindow(window, SW_SHOW);
    UpdateWindow(window);
    SetForegroundWindow(window);
    while (GetMessageW(&message, NULL, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    UnregisterClassW(window_class.lpszClassName, window_class.hInstance);
    return g_selector.selected_process_id;
}

static uintptr_t remote_module_base(DWORD process_id, const wchar_t *module_name) {
    HANDLE snapshot;
    MODULEENTRY32W entry;
    uintptr_t result = 0;
    snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, process_id);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Module32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szModule, module_name) == 0) {
                result = (uintptr_t)entry.modBaseAddr;
                break;
            }
        } while (Module32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return result;
}

static uintptr_t remote_module_by_path(
        DWORD process_id, const wchar_t *module_path) {
    HANDLE snapshot;
    MODULEENTRY32W entry;
    uintptr_t result = 0;
    snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, process_id);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Module32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szExePath, module_path) == 0) {
                result = (uintptr_t)entry.modBaseAddr;
                break;
            }
        } while (Module32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return result;
}

static int remote_has_crewx(DWORD process_id) {
    HANDLE snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, process_id);
    MODULEENTRY32W entry;
    int found = 0;
    if (snapshot == INVALID_HANDLE_VALUE) return 0;
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Module32FirstW(snapshot, &entry)) {
        do {
            if (_wcsnicmp(entry.szModule, L"CrewXNative", 11) == 0) {
                found = 1;
                break;
            }
        } while (Module32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return found;
}

static int require_x64_target(HANDLE process) {
    typedef BOOL (WINAPI *is_wow64_process2_fn)(HANDLE, USHORT *, USHORT *);
    is_wow64_process2_fn is_wow64_process2 =
            (is_wow64_process2_fn)GetProcAddress(
                    GetModuleHandleW(L"kernel32.dll"), "IsWow64Process2");
    if (is_wow64_process2 != NULL) {
        USHORT process_machine = IMAGE_FILE_MACHINE_UNKNOWN;
        USHORT native_machine = IMAGE_FILE_MACHINE_UNKNOWN;
        if (!is_wow64_process2(process, &process_machine, &native_machine)) {
            return 0;
        }
        return process_machine == IMAGE_FILE_MACHINE_UNKNOWN
                && native_machine == IMAGE_FILE_MACHINE_AMD64;
    }
    {
        BOOL wow64 = FALSE;
        if (!IsWow64Process(process, &wow64)) {
            return 0;
        }
        return !wow64 && sizeof(void *) == 8;
    }
}

static DWORD process_integrity(HANDLE process) {
    HANDLE token = NULL;
    DWORD bytes = 0;
    DWORD rid = 0;
    TOKEN_MANDATORY_LABEL *label;
    if (!OpenProcessToken(process, TOKEN_QUERY, &token)) return 0;
    GetTokenInformation(token, TokenIntegrityLevel, NULL, 0, &bytes);
    label = bytes == 0 ? NULL : (TOKEN_MANDATORY_LABEL *)malloc(bytes);
    if (label != NULL && GetTokenInformation(token, TokenIntegrityLevel,
            label, bytes, &bytes)) {
        DWORD count = *GetSidSubAuthorityCount(label->Label.Sid);
        if (count != 0) rid = *GetSidSubAuthority(label->Label.Sid, count - 1);
    }
    free(label);
    CloseHandle(token);
    return rid;
}

static int inject_library(DWORD process_id, const wchar_t *dll_path) {
    HANDLE process = NULL;
    HANDLE thread = NULL;
    LPVOID remote_path = NULL;
    HMODULE local_kernel;
    FARPROC local_load_library;
    uintptr_t remote_kernel;
    uintptr_t load_library_offset;
    LPTHREAD_START_ROUTINE remote_load_library;
    SIZE_T path_bytes = (wcslen(dll_path) + 1) * sizeof(wchar_t);
    SIZE_T written = 0;
    DWORD wait_result;
    DWORD thread_exit = 0;
    int have_thread_exit = 0;
    int attempt;
    int result = 0;

    log_inject(L"attempt PID=%lu DLL=%ls", (unsigned long)process_id, dll_path);

    if (remote_has_crewx(process_id) || remote_module_by_path(process_id, dll_path) != 0) {
        log_inject(L"CrewX DLL already mapped in PID=%lu", (unsigned long)process_id);
        return 2;
    }

    process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION
                    | PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ,
            FALSE, process_id);
    if (process == NULL) {
        print_last_error(L"OpenProcess");
        goto cleanup;
    }
    log_inject(L"integrity injector=0x%lx target=0x%lx",
            (unsigned long)process_integrity(GetCurrentProcess()),
            (unsigned long)process_integrity(process));
    if (!require_x64_target(process)) {
        fwprintf(stderr, L"Target process is not x64; injection refused.\n");
        log_inject(L"target architecture check failed");
        goto cleanup;
    }
    remote_path = VirtualAllocEx(process, NULL, path_bytes,
            MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (remote_path == NULL) {
        print_last_error(L"VirtualAllocEx");
        goto cleanup;
    }
    if (!WriteProcessMemory(process, remote_path, dll_path,
            path_bytes, &written) || written != path_bytes) {
        print_last_error(L"WriteProcessMemory");
        goto cleanup;
    }

    local_kernel = GetModuleHandleW(L"kernel32.dll");
    local_load_library = local_kernel == NULL ? NULL
            : GetProcAddress(local_kernel, "LoadLibraryW");
    remote_kernel = remote_module_base(process_id, L"kernel32.dll");
    if (local_kernel == NULL || local_load_library == NULL) {
        fwprintf(stderr, L"Could not resolve remote kernel32!LoadLibraryW.\n");
        log_inject(L"local kernel32 or LoadLibraryW unavailable");
        goto cleanup;
    }
    load_library_offset = (uintptr_t)local_load_library - (uintptr_t)local_kernel;
    if (remote_kernel != 0) {
        remote_load_library = (LPTHREAD_START_ROUTINE)(
                remote_kernel + load_library_offset);
    } else {

        remote_load_library = (LPTHREAD_START_ROUTINE)local_load_library;
        log_inject(L"remote kernel32 not enumerable; using local LoadLibraryW address");
    }
    thread = CreateRemoteThread(process, NULL, 0, remote_load_library,
            remote_path, 0, NULL);
    if (thread == NULL) {
        print_last_error(L"CreateRemoteThread");
        goto cleanup;
    }
    wait_result = WaitForSingleObject(thread, 30000);
    if (wait_result != WAIT_OBJECT_0) {
        fwprintf(stderr, L"Remote LoadLibraryW did not finish within 30 seconds.\n");
        log_inject(L"remote LoadLibraryW wait result=%lu", (unsigned long)wait_result);
        goto cleanup;
    }
    have_thread_exit = GetExitCodeThread(thread, &thread_exit) != 0;
    if (have_thread_exit) {
        log_inject(L"remote LoadLibraryW exit code=0x%08lx", (unsigned long)thread_exit);
    } else {
        print_last_error(L"GetExitCodeThread");
    }
    for (attempt = 0; attempt < 100; ++attempt) {
        if (remote_module_by_path(process_id, dll_path) != 0) {
            result = 1;
            break;
        }
        Sleep(50);
    }
    if (result == 0) {
        if (have_thread_exit && thread_exit != 0) {
            log_inject(L"LoadLibraryW reported success; module snapshot unavailable or incomplete");
            result = 1;
            goto cleanup;
        }
        fwprintf(stderr, L"LoadLibraryW returned, but the DLL is not mapped. "
                L"Inspect crewx-native.log for bootstrap failure.\n");
        log_inject(L"DLL not visible in target module list after LoadLibraryW");
        goto cleanup;
    }
    log_inject(L"DLL mapped in PID=%lu", (unsigned long)process_id);

cleanup:
    if (thread != NULL) CloseHandle(thread);
    if (remote_path != NULL && process != NULL) {
        VirtualFreeEx(process, remote_path, 0, MEM_RELEASE);
    }
    if (process != NULL) CloseHandle(process);
    return result;
}

static void usage(const wchar_t *program) {
    fwprintf(stderr,
            L"Usage: %ls [minecraft-pid]\n"
            L"Without a PID, an automatically refreshing Java window selector is shown.\n"
            L"The injector and payload are bundled in this EXE.\n", program);
}

int wmain(int argc, wchar_t **argv) {
    wchar_t dll_path[MAX_PATH];
    wchar_t *end = NULL;
    unsigned long process_id = 0;
    open_inject_log();
    log_inject(L"CrewX injector started");
    if (argc < 1 || argc > 2) {
        usage(argv[0]);
        return 2;
    }
    if (argc == 2) {
        process_id = wcstoul(argv[1], &end, 10);
        if (process_id == 0 || end == argv[1] || *end != L'\0') {
            fwprintf(stderr, L"Invalid process id: %ls\n", argv[1]);
            return 2;
        }
    }
    if (argc != 2) {
        process_id = (unsigned long)select_process(L"DLL embutida no EXE");
        if (process_id == 0) {
            return 1;
        }
    }
    if (!stage_embedded_dll((DWORD)process_id, dll_path, MAX_PATH)) {
        fwprintf(stderr, L"Could not extract the bundled injection component.\n");
        log_inject(L"embedded DLL staging failed; Windows error=%lu", (unsigned long)GetLastError());
        MessageBoxW(NULL, L"Nao foi possivel preparar o CrewX. Consulte o log em %TEMP%\\CrewX21\\injector.log.",
                L"CrewX", MB_ICONERROR | MB_OK);
        return 3;
    }
    {
        int injection_result = inject_library((DWORD)process_id, dll_path);
        if (injection_result == 0) {
            MessageBoxW(NULL, L"A injecao falhou. Consulte o log em %TEMP%\\CrewX21\\injector.log.",
                    L"CrewX", MB_ICONERROR | MB_OK);
            return 3;
        }
        if (injection_result == 2) {
            wprintf(L"%ls is already loaded in PID %lu; no second bootstrap was requested.\n",
                    dll_path, process_id);
            return 0;
        }
    }
    wprintf(L"Loaded %ls into PID %lu; Java bootstrap is running asynchronously.\n",
            dll_path, process_id);
    log_inject(L"DLL load requested successfully for PID=%lu", process_id);
    return 0;
}
