package com.cardenaspiero255.jarvis;

/** Fixed, reviewed diagnostics only. No generic shell or arbitrary arguments. */
interface IJarvisShizukuReadOnly {
    int remoteUid() = 1;
    String readProbe(int fixedCode) = 2;
    void destroy() = 16777114;
}
