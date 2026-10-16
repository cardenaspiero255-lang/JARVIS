package com.cardenaspiero255.jarvis;

/** Fixed, reviewed diagnostics only. No generic shell or arbitrary arguments. */
interface IJarvisShizukuReadOnly {
    int remoteUid();
    String readProbe(int fixedCode);
    void destroy() = 16777114;
}
