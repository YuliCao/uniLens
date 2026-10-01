"""Small Win32 boundary; every pointer-sized argument is explicitly typed."""
import ctypes
from ctypes import wintypes as w

u = ctypes.WinDLL("user32", use_last_error=True)
u.SetWindowDisplayAffinity.argtypes = [w.HWND, w.DWORD]
u.SetWindowDisplayAffinity.restype = w.BOOL
u.RegisterHotKey.argtypes = [w.HWND, ctypes.c_int, w.UINT, w.UINT]
u.RegisterHotKey.restype = w.BOOL
u.UnregisterHotKey.argtypes = [w.HWND, ctypes.c_int]
u.UnregisterHotKey.restype = w.BOOL
u.GetWindowLongPtrW.argtypes = [w.HWND, ctypes.c_int]
u.GetWindowLongPtrW.restype = ctypes.c_ssize_t
u.SetWindowLongPtrW.argtypes = [w.HWND, ctypes.c_int, ctypes.c_ssize_t]
u.SetWindowLongPtrW.restype = ctypes.c_ssize_t
u.MonitorFromWindow.argtypes = [w.HWND, w.DWORD]
u.MonitorFromWindow.restype = w.HANDLE
u.GetMonitorInfoW.argtypes = [w.HANDLE, ctypes.c_void_p]
u.GetMonitorInfoW.restype = w.BOOL


class MonitorInfo(ctypes.Structure):
    _fields_ = [("cbSize", w.DWORD), ("rcMonitor", w.RECT), ("rcWork", w.RECT), ("dwFlags", w.DWORD)]


def exclude_capture(widget) -> bool:
    return bool(u.SetWindowDisplayAffinity(int(widget.winId()), 0x11))


def click_through(widget):
    hwnd = int(widget.winId())
    style = u.GetWindowLongPtrW(hwnd, -20)
    u.SetWindowLongPtrW(hwnd, -20, style | 0x80000 | 0x20 | 0x08000000)


def physical_monitor_origin(widget):
    info = MonitorInfo()
    info.cbSize = ctypes.sizeof(info)
    if not u.GetMonitorInfoW(u.MonitorFromWindow(int(widget.winId()), 2), ctypes.byref(info)):
        raise OSError(ctypes.get_last_error(), "GetMonitorInfoW failed")
    return info.rcMonitor.left, info.rcMonitor.top
