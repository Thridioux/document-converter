# Memory Diagnostics Commands for Kubernetes Pod

Run these commands one by one and share the output. Replace `documentconverter-6c4d56954c-wrs26` with your actual pod name and namespace.

## Setup Variables (Optional)
```bash
export POD_NAME="documentconverter-6c4d56954c-wrs26"
export NAMESPACE="default"  # Change to your namespace
```

---

## 1. Pod Resource Usage (kubectl top)
```bash
kubectl top pod documentconverter-6c4d56954c-wrs26 -n dev
```
documentconverter-6c4d56954c-wrs26   2m           543Mi           

---

## 2. All Processes by RSS Memory (Top 20)
```bash
kubectl exec -it documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux --sort=-rss | head -n 20"
```
kubectl exec -it documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux --sort=-rss | head -n 20"

USER         PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND
root           1  0.0  4.7 5808496 365472 ?      Ssl  Oct03  26:40 java -jar app.jar
root         132  0.0  0.3 731240 25676 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2005,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2005_tcpNoDelay-1
root         133  0.0  0.3 731240 25676 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2003,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2003_tcpNoDelay-1
root         145  0.0  0.3 731240 25668 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2002,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2002_tcpNoDelay-1
root         150  0.0  0.3 583776 25660 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2004,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2004_tcpNoDelay-1
root        1119  0.0  0.2 33982708 17852 ?      Sl   Oct03   0:53 /ms-playwright/chromium-1134/chrome-linux/chrome --type=gpu-process --no-sandbox --disable-dev-shm-usage --disable-breakpad --headless=old --ozone-platform=headless --use-angle=swiftshader-webgl --gpu-preferences=UAAAAAAAAAAgAAAMAAAAAAAAAAAAAAAAAABgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAEAAAAAAAAAAIAAAAAAAAAAgAAAAAAAAA --use-gl=angle --use-angle=swiftshader-webgl --shared-files --field-trial-handle=3,i,15667815327141421444,11644504968467793128,262144 --disable-features=AcceptCHFrame,AutoExpandDetailsElement,AvoidUnnecessaryBeforeUnloadCheckSync,CertificateTransparencyComponentUpdater,DestroyProfileOnBrowserClose,DialMediaRouteProvider,GlobalMediaControls,HttpsUpgrades,ImprovedCookieControls,LazyFrameLoading,MediaRouter,PaintHolding,PlzDedicatedWorker,Translate --variations-seed-version
root        1085  0.0  0.2 1030880 17548 ?       Sl   Oct03   0:00 /tmp/playwright-java-4540436133188306979/node /tmp/playwright-java-4540436133188306979/package/cli.js run-driver
root        1120  0.0  0.2 33936500 16176 ?      Sl   Oct03   0:59 /ms-playwright/chromium-1134/chrome-linux/chrome --type=utility --utility-sub-type=network.mojom.NetworkService --lang=en-US --service-sandbox-type=none --no-sandbox --disable-dev-shm-usage --use-angle=swiftshader-webgl --use-gl=angle --mute-audio --headless=old --shared-files=v8_context_snapshot_data:100 --field-trial-handle=3,i,15667815327141421444,11644504968467793128,262144 --disable-features=AcceptCHFrame,AutoExpandDetailsElement,AvoidUnnecessaryBeforeUnloadCheckSync,CertificateTransparencyComponentUpdater,DestroyProfileOnBrowserClose,DialMediaRouteProvider,GlobalMediaControls,HttpsUpgrades,ImprovedCookieControls,LazyFrameLoading,MediaRouter,PaintHolding,PlzDedicatedWorker,Translate --variations-seed-version
root        1099  0.0  0.2 34067304 15732 ?      Ssl  Oct03   1:41 /ms-playwright/chromium-1134/chrome-linux/chrome --disable-field-trial-config --disable-background-networking --disable-background-timer-throttling --disable-backgrounding-occluded-windows --disable-back-forward-cache --disable-breakpad --disable-client-side-phishing-detection --disable-component-extensions-with-background-pages --disable-component-update --no-default-browser-check --disable-default-apps --disable-dev-shm-usage --disable-extensions --disable-features=ImprovedCookieControls,LazyFrameLoading,GlobalMediaControls,DestroyProfileOnBrowserClose,MediaRouter,DialMediaRouteProvider,AcceptCHFrame,AutoExpandDetailsElement,CertificateTransparencyComponentUpdater,AvoidUnnecessaryBeforeUnloadCheckSync,Translate,HttpsUpgrades,PaintHolding,PlzDedicatedWorker --allow-pre-commit-input --disable-hang-monitor --disable-ipc-flooding-protection --disable-popup-blocking --disable-prompt-on-repost --disable-renderer-backgrounding --force-color-profile=srgb --metrics-recording-only --no-first-run --enable-automation --password-store=basic --use-mock-keychain --no-service-autorun --export-tagged-pdf --disable-search-engine-choice-screen --unsafely-disable-devtools-self-xss-warnings --headless=old --hide-scrollbars --mute-audio --blink-settings=primaryHoverType=2,availableHoverTypes=2,primaryPointerType=4,availablePointerTypes=4 --no-sandbox --no-sandbox --disable-dev-shm-usage --disable-gpu --disable-extensions --disable-plugins --disable-background-timer-throttling --disable-backgrounding-occluded-windows --disable-renderer-backgrounding --user-data-dir=/tmp/playwright_chromiumdev_profile-jR7eOO --remote-debugging-pipe --no-startup-window
root        1101  0.0  0.1 33878716 12464 ?      S    Oct03   0:00 /ms-playwright/chromium-1134/chrome-linux/chrome --type=zygote --no-zygote-sandbox --no-sandbox --headless=old
root        1102  0.0  0.1 33878704 11960 ?      S    Oct03   0:00 /ms-playwright/chromium-1134/chrome-linux/chrome --type=zygote --no-sandbox --headless=old
root        1165  0.0  0.0   7896  4032 pts/0    R+   14:08   0:00 ps aux --sort=-rss
root        1166  0.0  0.0   2712  1032 pts/0    S+   14:08   0:00 head -n 20
root        1159  0.0  0.0   2804   988 pts/0    Ss+  14:08   0:00 sh -c ps aux --sort=-rss | head -n 20
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % 

---

## 3. Java Process Details
```bash
kubectl exec -it documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | grep java | grep -v grep"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec -it documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | grep java | grep -v grep"

root           1  0.0  4.7 5808496 365472 ?      Ssl  Oct03  26:40 java -jar app.jar
root        1085  0.0  0.2 1030880 17548 ?       Sl   Oct03   0:00 /tmp/playwright-java-4540436133188306979/node /tmp/playwright-java-4540436133188306979/package/cli.js run-driver

---

## 4. Get Java PID (save this for next commands)
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | grep java | grep -v grep | awk '{print \$2}' | head -n 1"
```

erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | grep java | grep -v grep | awk '{print \$2}' | head -n 1"
1
**Save the PID number from output and use it in commands below (replace `1`)**

---

## 5. Java Memory from /proc
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "cat /proc/1/status | egrep 'VmSize|VmRSS|VmData|VmPeak|Threads'"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "cat /proc/1/status | egrep 'VmSize|VmRSS|VmData|VmPeak|Threads'"
VmPeak:  5853720 kB
VmSize:  5808496 kB
VmRSS:    365472 kB
VmData:  1329876 kB
Threads:        54

---

## 6. Java Native Memory Summary (if available)
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 VM.native_memory summary"
```
*If this fails, NMT is not enabled - that's okay*
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 VM.native_memory summary"

Picked up JAVA_TOOL_OPTIONS: -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms1g -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8
1:
Native memory tracking is not enabled
---

## 7. Java Heap Histogram (Top 30 classes)
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 GC.class_histogram | head -n 35"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 GC.class_histogram | head -n 35"

Picked up JAVA_TOOL_OPTIONS: -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms1g -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8
1:
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:         40994        2778424  [B (java.base@21.0.4)
   2:         56589        1358136  java.lang.String (java.base@21.0.4)
   3:          9317        1102400  java.lang.Class (java.base@21.0.4)
   4:         33002        1056064  java.util.concurrent.ConcurrentHashMap$Node (java.base@21.0.4)
   5:          9532         602000  [Ljava.lang.Object; (java.base@21.0.4)
   6:         10435         417400  java.util.LinkedHashMap$Entry (java.base@21.0.4)
   7:          2545         392200  [I (java.base@21.0.4)
   8:          4843         366608  [Ljava.util.HashMap$Node; (java.base@21.0.4)
   9:          5311         339904  java.util.LinkedHashMap (java.base@21.0.4)
  10:           283         290160  [Ljava.util.concurrent.ConcurrentHashMap$Node; (java.base@21.0.4)
  11:         17461         279376  java.lang.Object (java.base@21.0.4)
  12:          8514         272448  java.util.HashMap$Node (java.base@21.0.4)
  13:          3005         264440  java.lang.reflect.Method (java.base@21.0.4)
  14:          4402         211296  java.lang.invoke.MemberName (java.base@21.0.4)
  15:          3421         136840  java.lang.invoke.MethodType (java.base@21.0.4)
  16:          3577         114464  jdk.internal.util.WeakReferenceKey (java.base@21.0.4)
  17:          4052         111000  [Ljava.lang.Class; (java.base@21.0.4)
  18:          2683         107320  java.lang.ref.SoftReference (java.base@21.0.4)
  19:          1977          94896  com.google.gson.internal.LinkedTreeMap$Node
  20:          2090          83600  sun.util.locale.LocaleObjectCache$CacheEntry (java.base@21.0.4)
  21:          1720          82560  java.util.HashMap (java.base@21.0.4)
  22:          1467          82152  org.springframework.core.ResolvableType
  23:          3036          72864  java.lang.invoke.ResolvedMethodName (java.base@21.0.4)
  24:           316          60672  org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader$ConfigurationClassBeanDefinition
  25:          1573          59192  [Ljava.lang.String; (java.base@21.0.4)
  26:           255          59080  [C (java.base@21.0.4)
  27:          2382          57168  java.util.ArrayList (java.base@21.0.4)
  28:          1406          56240  java.lang.invoke.DirectMethodHandle (java.base@21.0.4)
  29:          1743          55776  java.lang.invoke.LambdaForm$Name (java.base@21.0.4)
  30:          1661          53152  java.util.concurrent.locks.ReentrantLock$NonfairSync (java.base@21.0.4)
  31:          1011          48528  java.lang.invoke.DirectMethodHandle$Constructor (java.base@21.0.4)
  32:          1203          48120  java.lang.invoke.BoundMethodHandle$Species_LL (java.base@21.0.4)
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % 

---

## 8. Java JVM Flags/Options
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 VM.flags"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 VM.flags"

Picked up JAVA_TOOL_OPTIONS: -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms1g -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8
1:
-XX:CICompilerCount=3 -XX:ConcGCThreads=1 -XX:G1ConcRefinementThreads=4 -XX:G1EagerReclaimRemSetThreshold=12 -XX:G1HeapRegionSize=1048576 -XX:G1RemSetArrayOfCardsEntries=12 -XX:G1RemSetHowlMaxNumBuckets=8 -XX:G1RemSetHowlNumBuckets=4 -XX:GCDrainStackTargetSize=64 -XX:InitialHeapSize=1073741824 -XX:MarkStackSize=4194304 -XX:MaxGCPauseMillis=200 -XX:MaxHeapSize=2147483648 -XX:MaxNewSize=1287651328 -XX:MinHeapDeltaBytes=1048576 -XX:MinHeapSize=1073741824 -XX:NonNMethodCodeHeapSize=5832780 -XX:NonProfiledCodeHeapSize=122912730 -XX:ProfiledCodeHeapSize=122912730 -XX:ReservedCodeCacheSize=251658240 -XX:+SegmentedCodeCache -XX:SoftMaxHeapSize=2147483648 -XX:-THPStackMitigation -XX:+UseCompressedOops -XX:+UseFastUnorderedTimeStamps -XX:+UseG1GC -XX:+UseStringDeduplication 

```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 VM.command_line"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "jcmd 1 VM.command_line"

Picked up JAVA_TOOL_OPTIONS: -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms1g -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8
1:
VM Arguments:
jvm_args: -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms1g -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8 
java_command: app.jar
java_class_path (initial): app.jar
Launcher Type: SUN_STANDARD
---

## 9. LibreOffice (soffice.bin) Processes
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | grep soffice.bin | grep -v grep"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | grep soffice.bin | grep -v grep"

root         132  0.0  0.3 731240 25676 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2005,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2005_tcpNoDelay-1
root         133  0.0  0.3 731240 25676 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2003,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2003_tcpNoDelay-1
root         145  0.0  0.3 731240 25668 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2002,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2002_tcpNoDelay-1
root         150  0.0  0.3 583776 25660 ?        Sl   Oct03   0:00 /usr/lib/libreoffice/program/soffice.bin --accept=socket,host=127.0.0.1,port=2004,tcpNoDelay=1;urp;StarOffice.ServiceManager --headless --invisible --nocrashreport --nodefault --nofirststartwizard --nolockcheck --nologo --norestore -env:UserInstallation=file:///tmp/.jodconverter_socket_host-127.0.0.1_port-2004_tcpNoDelay-1

---

## 10. Chromium/Playwright Browser Processes
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | egrep 'chrome|chromium|playwright' | head -n 20"
```
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps aux | egrep 'chrome|chromium|playwright' | head -n 20"

root        1085  0.0  0.2 1030880 17548 ?       Sl   Oct03   0:00 /tmp/playwright-java-4540436133188306979/node /tmp/playwright-java-4540436133188306979/package/cli.js run-driver
root        1099  0.0  0.2 34067304 15732 ?      Ssl  Oct03   1:41 /ms-playwright/chromium-1134/chrome-linux/chrome --disable-field-trial-config --disable-background-networking --disable-background-timer-throttling --disable-backgrounding-occluded-windows --disable-back-forward-cache --disable-breakpad --disable-client-side-phishing-detection --disable-component-extensions-with-background-pages --disable-component-update --no-default-browser-check --disable-default-apps --disable-dev-shm-usage --disable-extensions --disable-features=ImprovedCookieControls,LazyFrameLoading,GlobalMediaControls,DestroyProfileOnBrowserClose,MediaRouter,DialMediaRouteProvider,AcceptCHFrame,AutoExpandDetailsElement,CertificateTransparencyComponentUpdater,AvoidUnnecessaryBeforeUnloadCheckSync,Translate,HttpsUpgrades,PaintHolding,PlzDedicatedWorker --allow-pre-commit-input --disable-hang-monitor --disable-ipc-flooding-protection --disable-popup-blocking --disable-prompt-on-repost --disable-renderer-backgrounding --force-color-profile=srgb --metrics-recording-only --no-first-run --enable-automation --password-store=basic --use-mock-keychain --no-service-autorun --export-tagged-pdf --disable-search-engine-choice-screen --unsafely-disable-devtools-self-xss-warnings --headless=old --hide-scrollbars --mute-audio --blink-settings=primaryHoverType=2,availableHoverTypes=2,primaryPointerType=4,availablePointerTypes=4 --no-sandbox --no-sandbox --disable-dev-shm-usage --disable-gpu --disable-extensions --disable-plugins --disable-background-timer-throttling --disable-backgrounding-occluded-windows --disable-renderer-backgrounding --user-data-dir=/tmp/playwright_chromiumdev_profile-jR7eOO --remote-debugging-pipe --no-startup-window
root        1101  0.0  0.1 33878716 12464 ?      S    Oct03   0:00 /ms-playwright/chromium-1134/chrome-linux/chrome --type=zygote --no-zygote-sandbox --no-sandbox --headless=old
root        1102  0.0  0.1 33878704 11960 ?      S    Oct03   0:00 /ms-playwright/chromium-1134/chrome-linux/chrome --type=zygote --no-sandbox --headless=old
root        1119  0.0  0.2 33982708 17852 ?      Sl   Oct03   0:53 /ms-playwright/chromium-1134/chrome-linux/chrome --type=gpu-process --no-sandbox --disable-dev-shm-usage --disable-breakpad --headless=old --ozone-platform=headless --use-angle=swiftshader-webgl --gpu-preferences=UAAAAAAAAAAgAAAMAAAAAAAAAAAAAAAAAABgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAEAAAAAAAAAAIAAAAAAAAAAgAAAAAAAAA --use-gl=angle --use-angle=swiftshader-webgl --shared-files --field-trial-handle=3,i,15667815327141421444,11644504968467793128,262144 --disable-features=AcceptCHFrame,AutoExpandDetailsElement,AvoidUnnecessaryBeforeUnloadCheckSync,CertificateTransparencyComponentUpdater,DestroyProfileOnBrowserClose,DialMediaRouteProvider,GlobalMediaControls,HttpsUpgrades,ImprovedCookieControls,LazyFrameLoading,MediaRouter,PaintHolding,PlzDedicatedWorker,Translate --variations-seed-version
root        1120  0.0  0.2 33936500 16176 ?      Sl   Oct03   0:59 /ms-playwright/chromium-1134/chrome-linux/chrome --type=utility --utility-sub-type=network.mojom.NetworkService --lang=en-US --service-sandbox-type=none --no-sandbox --disable-dev-shm-usage --use-angle=swiftshader-webgl --use-gl=angle --mute-audio --headless=old --shared-files=v8_context_snapshot_data:100 --field-trial-handle=3,i,15667815327141421444,11644504968467793128,262144 --disable-features=AcceptCHFrame,AutoExpandDetailsElement,AvoidUnnecessaryBeforeUnloadCheckSync,CertificateTransparencyComponentUpdater,DestroyProfileOnBrowserClose,DialMediaRouteProvider,GlobalMediaControls,HttpsUpgrades,ImprovedCookieControls,LazyFrameLoading,MediaRouter,PaintHolding,PlzDedicatedWorker,Translate --variations-seed-version
root        1322  0.0  0.0   2804  1124 ?        Ss   14:13   0:00 sh -c ps aux | egrep 'chrome|chromium|playwright' | head -n 20
root        1329  0.0  0.0   3532  1636 ?        S    14:13   0:00 grep -E chrome|chromium|playwright
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % 

---

## 11. Thread Count by Process
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps -eLf | awk '{print \$4}' | sort | uniq -c | sort -rn | head -n 10"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ps -eLf | awk '{print \$4}' | sort | uniq -c | sort -rn | head -n 10"
      1 LWP
      1 805
      1 802
      1 801
      1 747
      1 745
      1 744
      1 743
      1 685
      1 683

---

## 12. Container Memory Limits (cgroup)
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "cat /sys/fs/cgroup/memory/memory.limit_in_bytes 2>/dev/null || cat /sys/fs/cgroup/memory.max 2>/dev/null"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "cat /sys/fs/cgroup/memory/memory.limit_in_bytes 2>/dev/null || cat /sys/fs/cgroup/memory.max 2>/dev/null"
9223372036854771712
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "cat /sys/fs/cgroup/memory/memory.usage_in_bytes 2>/dev/null || cat /sys/fs/cgroup/memory.current 2>/dev/null"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "cat /sys/fs/cgroup/memory/memory.usage_in_bytes 2>/dev/null || cat /sys/fs/cgroup/memory.current 2>/dev/null"
619376640

---

## 13. Open File Descriptors Count
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ls /proc/1/fd 2>/dev/null | wc -l"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "ls /proc/1/fd 2>/dev/null | wc -l"

19

---

## 14. Container Environment Variables (Check for JVM settings)
```bash
kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "env | egrep 'JAVA|JVM|MAVEN|CATALINA|SPRING'"
```
erdem@Erdems-MacBook-Pro Link.Cloud.K8S.Huawei-main % kubectl exec documentconverter-6c4d56954c-wrs26 -n dev -- sh -c "env | egrep 'JAVA|JVM|MAVEN|CATALINA|SPRING'"
JAVA_TOOL_OPTIONS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms1g -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

---

## Quick Analysis Tips:
- **Chromium processes**: Each browser instance uses 100-200MB
- **soffice.bin count**: Should see 1-4 processes max (configured via port-numbers)
- **Java VmRSS**: Actual RAM used by JVM (heap + native)
- **Thread count**: Spring Boot + Playwright + LibreOffice threads
- **Heap histogram**: Shows which Java objects are consuming heap

---

## What to Look For:
1. **High chromium memory** → Playwright browser eating RAM
2. **Multiple soffice.bin** → LibreOffice spawning too many processes
3. **Java VmRSS > 300MB** → JVM heap or native memory too large
4. **High thread count (>100)** → Thread pool configured too large
5. **No JVM heap limits in VM.flags** → Java using default (container-aware but risky)

---

**After running these, share the outputs and I'll analyze what's causing the 500MB idle usage and provide targeted fixes.**
