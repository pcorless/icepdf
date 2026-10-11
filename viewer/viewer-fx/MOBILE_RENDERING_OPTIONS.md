# Java2D Rendering Options for Mobile (2026 Analysis)

**Date:** March 20, 2026  
**Context:** Mobile deployment options for ICEpdf with Java2D rendering  
**Question:** Is Java2D available on mobile platforms in 2026?

---

## Executive Summary

**Short Answer:** ❌ **No native Java2D on iOS/Android**, but ✅ **viable alternatives exist**.

**Reality Check:**
- Java2D is NOT available on iOS (never was, never will be)
- Java2D is NOT available on standard Android (removed in 2010)
- However, there ARE ways to deploy Java applications to mobile in 2026

---

## Mobile Platform Analysis

### iOS (iPhone/iPad)

#### Java2D Status: ❌ NOT AVAILABLE

**Why:**
- Apple doesn't allow JVM on iOS (App Store policy)
- No Java runtime permitted
- Swift/Objective-C only for native apps

**Alternatives:**
1. **Gluon Mobile** (JavaFX-based)
2. **Multi-OS Engine (MOE)** (Dead/deprecated)
3. **RoboVM** (Dead since 2016)
4. **Codename One** (Write-once-run-anywhere)

#### Verdict for ICEpdf on iOS:
🟡 **Possible but requires major changes** - Would need to use JavaFX rendering (not Java2D)

---

### Android

#### Java2D Status: ❌ NOT AVAILABLE (Standard Android)

**Why:**
- Android removed AWT/Swing in Android 1.0 (2008)
- Android uses Android Canvas API (not Java2D)
- Graphics stack is completely different

**Android Graphics Stack:**
```
Android App
    ↓
Android Canvas API (android.graphics.Canvas)
    ↓
Skia Graphics Engine (C++)
    ↓
OpenGL ES / Vulkan
    ↓
GPU
```

**NOT:**
```
Java2D (Graphics2D)
    ↓
Doesn't exist on Android!
```

#### Alternative 1: Gluon Mobile
🟢 **Viable with JavaFX**

**Status in 2026:** Active, mature
- JavaFX runs on Android via Gluon Mobile
- Uses native rendering (Monocle + Android Canvas)
- Supports JavaFX Canvas and Nodes
- **Java2D NOT included**

#### Alternative 2: Codename One
🟢 **Viable with their graphics API**

**Status in 2026:** Active
- Write-once-run-anywhere framework
- Own graphics API (similar to Java2D)
- Would require porting ICEpdf rendering code
- Proven for complex apps

#### Alternative 3: Native PDF Rendering
🟢 **Best Performance**

**Android PDFRenderer API:**
```java
// Android native (since Android 5.0)
PdfRenderer renderer = new PdfRenderer(parcelFileDescriptor);
PdfRenderer.Page page = renderer.openPage(pageIndex);
page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
// Fast, native, hardware accelerated
```

**Problem:** You lose ICEpdf's custom features (annotations, forms, etc.)

#### Verdict for ICEpdf on Android:
🟡 **Possible but requires significant work** - Either JavaFX via Gluon or port to Android APIs

---

## Detailed Analysis: Mobile Java Options (2026)

### Option 1: Gluon Mobile 🟢 (Most Viable for JavaFX)

**What it is:**
- Commercial product (free tier + paid support)
- Brings JavaFX to iOS and Android
- Uses native rendering backends
- Active development and support

**Architecture:**
```
Your JavaFX App
    ↓
Gluon Mobile Runtime
    ↓
Monocle (JavaFX on mobile)
    ↓
Native Graphics (iOS: Metal, Android: OpenGL ES)
    ↓
GPU
```

**What Works:**
- ✅ JavaFX Scene Graph
- ✅ JavaFX Canvas
- ✅ JavaFX Controls
- ✅ JavaFX Properties and Bindings
- ✅ FXML
- ✅ CSS Styling

**What Doesn't Work:**
- ❌ Java2D (Graphics2D, BufferedImage)
- ❌ Swing components
- ❌ AWT
- ❌ Desktop-only APIs

**For ICEpdf:**
- ✅ Your JavaFX viewer-fx could work
- ❌ Your Java2D rendering would need conversion
- 🟡 Hybrid approach won't work (no Java2D)
- ⚠️ Would need full JavaFX Canvas rendering

**Effort:** 
- If using pure JavaFX rendering: 3-6 months
- Plus mobile-specific optimizations: +2-3 months

**Website:** https://gluonhq.com/products/mobile/

---

### Option 2: Codename One 🟢 (Cross-Platform Alternative)

**What it is:**
- Write-once-run-anywhere framework
- Own graphics API (CodenameOneGraphics)
- Similar to Java2D but not identical
- Active community, good support

**Architecture:**
```
Your Codename One App
    ↓
Codename One Graphics API
    ↓
Platform-Specific Renderers
    ↓
iOS: CoreGraphics | Android: Canvas | Desktop: Java2D
    ↓
Native Rendering
```

**What Works:**
- ✅ Custom graphics API (similar to Java2D)
- ✅ Cross-platform (iOS, Android, Desktop, Web)
- ✅ Rich UI components
- ✅ Native look and feel
- ✅ Hardware acceleration

**What Doesn't Work:**
- ❌ Java2D directly (must port to their API)
- ❌ JavaFX (different framework)
- ❌ Swing

**For ICEpdf:**
- 🟡 Would need to port rendering code to Codename One API
- 🟡 Their graphics API is reasonably similar to Java2D
- ✅ Proven for complex graphics applications
- ✅ Real mobile deployment (not just "runs on mobile")

**Effort:**
- Port rendering engine: 4-6 months
- Mobile UI: 2-3 months
- Testing and optimization: 2-3 months
- **Total: 8-12 months**

**Website:** https://www.codenameone.com/

---

### Option 3: Multi-Platform Progressive Web App (PWA) 🟡

**What it is:**
- Run Java in browser via WebAssembly
- Deploy as PWA (works on all mobile browsers)
- No app store needed

**Technologies:**
- **TeaVM** - Java → WebAssembly compiler
- **CheerpJ** - JVM in browser
- **GWT** (older, less suitable)

**Architecture:**
```
Your Java Code
    ↓
Compile to WebAssembly
    ↓
Browser (Chrome, Safari, Firefox)
    ↓
HTML5 Canvas
    ↓
Mobile GPU
```

**What Works:**
- ✅ Core Java code (logic, PDF parsing)
- ✅ HTML5 Canvas for rendering
- ✅ Works on any mobile browser
- ✅ No app store approval needed
- ✅ Auto-updates

**What Doesn't Work:**
- ❌ Java2D (must convert to Canvas API)
- ❌ File system access (limited)
- ❌ Native features (camera, etc.)
- 🟡 Performance (good but not native)

**For ICEpdf:**
- 🟡 Significant porting effort
- 🟡 Would need to convert rendering to HTML5 Canvas
- ✅ Same code for mobile and desktop web
- ✅ No platform gatekeepers

**Effort:**
- Port rendering to Canvas: 4-6 months
- Web UI: 2-3 months
- **Total: 6-9 months**

---

### Option 4: Native Mobile PDF Frameworks 🟢 (Pragmatic)

**Recommendation:** Don't fight the platform, use native PDF rendering.

#### iOS: PDFKit (Apple's Framework)
```swift
import PDFKit

let pdfView = PDFView()
pdfView.document = PDFDocument(url: fileURL)
// Native, fast, hardware accelerated
// Full annotation support
// Text selection built-in
```

#### Android: PdfRenderer + PDFium
```java
import android.graphics.pdf.PdfRenderer;

PdfRenderer renderer = new PdfRenderer(parcelFileDescriptor);
PdfRenderer.Page page = renderer.openPage(pageIndex);
Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
// Fast, native, hardware accelerated
```

**For ICEpdf:**
- ❌ Complete rewrite for mobile
- ❌ Lose ICEpdf features (custom rendering, forms)
- ✅ Best performance
- ✅ Native platform integration
- ✅ App store friendly

**Effort:**
- iOS app: 2-3 months
- Android app: 2-3 months
- Feature parity with ICEpdf: +3-6 months
- **Total: 7-12 months**

---

## Comparison Matrix (2026)

| Solution | iOS Support | Android Support | Java2D | JavaFX | Performance | Effort |
|----------|-------------|-----------------|--------|--------|-------------|--------|
| **Gluon Mobile** | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes | ⭐⭐⭐ | High |
| **Codename One** | ✅ Yes | ✅ Yes | 🟡 Similar | ❌ No | ⭐⭐⭐⭐ | High |
| **WebAssembly/PWA** | ✅ Browser | ✅ Browser | ❌ No | ❌ No | ⭐⭐⭐ | High |
| **Native APIs** | ✅ Yes | ✅ Yes | ❌ No | ❌ No | ⭐⭐⭐⭐⭐ | Very High |
| **Desktop Only** | ❌ No | ❌ No | ✅ Yes | ✅ Yes | ⭐⭐⭐⭐⭐ | None |

---

## The Hard Truth About Mobile + Java2D

### Java2D is Desktop-Only (2026)

```
Desktop Platforms:        Mobile Platforms:
✅ Windows               ❌ iOS (never had Java2D)
✅ macOS                 ❌ Android (removed in 2008)
✅ Linux                 ❌ iPadOS
✅ BSD                   ❌ Mobile web browsers

Java2D = Desktop Graphics API
Not designed for mobile
Not ported to mobile
Will never be on mobile
```

### Why Java2D Never Came to Mobile

1. **AWT/Swing Dependency** - Too heavyweight for mobile
2. **Desktop-centric API** - Assumes mouse, keyboard, windowing
3. **Memory footprint** - Too large for mobile devices (historically)
4. **Performance** - Not optimized for mobile GPUs
5. **Platform politics** - Apple/Google want native frameworks

---

## Realistic Mobile Strategies for ICEpdf

### Strategy A: Desktop-Only (Recommended for Now) ✅

**Verdict:** Focus on desktop JavaFX viewer with hybrid rendering

**Rationale:**
- Java2D works perfectly on desktop
- JavaFX provides modern UI
- No compromises needed
- Full feature set available
- Best performance

**Mobile Access:** Web browser or remote desktop

---

### Strategy B: JavaFX Mobile (Gluon) 🟡

**Verdict:** Possible but requires JavaFX-only rendering

**Requirements:**
- ✅ Port ICEpdf rendering to JavaFX Canvas (6-12 months)
- ✅ Optimize for mobile GPUs
- ✅ Reduce memory footprint
- ✅ Touch-optimized UI
- ✅ Pay for Gluon Mobile license (commercial apps)

**Pros:**
- Single codebase for desktop + mobile
- Modern JavaFX UI
- Cross-platform

**Cons:**
- Massive rendering rewrite needed
- Performance concerns
- Ongoing Gluon licensing costs
- Mobile-specific bugs to fix

**Timeline:** 12-18 months for feature-complete mobile app

---

### Strategy C: Progressive Web App 🟡

**Verdict:** Future-proof but significant work

**Requirements:**
- Port rendering to HTML5 Canvas or WebGL
- JavaScript interop or WebAssembly
- Web-based UI
- Cloud storage integration

**Pros:**
- Works on all mobile browsers
- No app store approval needed
- Auto-updates
- Desktop + mobile from same code

**Cons:**
- Different graphics API (Canvas/WebGL)
- Limited file system access
- Network dependency
- Performance not as good as native

**Timeline:** 8-12 months

---

### Strategy D: Separate Native Mobile Apps 🟢

**Verdict:** Best performance, most work

**Approach:**
- iOS: Swift + PDFKit
- Android: Kotlin + PdfRenderer/PDFium
- Desktop: Java + ICEpdf (existing)

**Pros:**
- Best performance (native APIs)
- Best platform integration
- Best user experience
- App store friendly

**Cons:**
- Maintain 3 codebases
- Platform-specific expertise needed
- No code sharing for rendering

**Timeline:** 12-18 months (both platforms)

---

## Technology Deep Dive

### Gluon Mobile Details (2026)

**Current Status:**
- Active development ✅
- Latest version: Gluon Mobile 6.x
- JavaFX 21 support
- iOS 17 and Android 14 support

**What You Get:**
```java
// Your JavaFX app runs on mobile!
public class MobileApp extends Application {
    @Override
    public void start(Stage stage) {
        // Same JavaFX code
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        // Render using JavaFX Canvas (NOT Java2D)
        
        Scene scene = new Scene(new StackPane(canvas));
        stage.setScene(scene);
        stage.show();
    }
}
```

**What You DON'T Get:**
```java
// This code won't work on mobile:
import java.awt.*;
import java.awt.image.BufferedImage;

Graphics2D g2d = bufferedImage.createGraphics();  // ❌ Not available
g2d.drawString("text", x, y);  // ❌ Won't compile
```

**Licensing (2026):**
- Free tier: For development and testing
- Commercial: ~$999-2999/year per developer
- Enterprise: Custom pricing

**Limitations:**
- No AWT/Swing
- No Java2D
- No Desktop-only APIs
- File access restrictions
- Camera/GPS via Gluon Attach plugins

---

### Codename One Details (2026)

**Current Status:**
- Very active ✅
- Large community
- Proven for enterprise apps
- Good documentation

**Graphics API:**
```java
// Codename One graphics API (similar to Java2D)
import com.codename1.ui.Graphics;

public void paint(Graphics g) {
    g.setColor(0xFF0000);
    g.fillRect(x, y, width, height);
    g.drawString("text", x, y);
    // Similar to Java2D but not identical
}
```

**Porting Effort:**
```java
// Java2D code:
g2d.setColor(Color.RED);
g2d.fillRect(x, y, w, h);
g2d.drawString("text", x, y);

// Codename One equivalent:
g.setColor(0xFF0000);  // RGB hex instead of Color object
g.fillRect(x, y, w, h);  // Same signature
g.drawString("text", x, y);  // Same signature

// ~70-80% compatible, rest needs adaptation
```

**For ICEpdf:**
- 🟡 API is similar enough that porting is feasible
- 🟡 Would need to abstract graphics operations
- 🟡 Create interface that works for both Java2D and CN1
- ✅ Proven for PDF apps (others have done it)

**Licensing:**
- Free tier: Limited features
- Pro: $19/month per developer
- Enterprise: Custom pricing

---

## Mobile Deployment Comparison

### Desktop JavaFX + Hybrid (Your Current Plan)

**Platforms:** Windows, macOS, Linux
```
✅ Java2D rendering (fast, proven)
✅ JavaFX UI (modern, smooth)
✅ Full feature set
✅ Best performance
✅ Zero compromises
```

**Mobile:** Access via remote desktop, VNC, or web-based viewer

---

### Gluon Mobile (JavaFX Only)

**Platforms:** iOS, Android, Desktop
```
❌ No Java2D (must use JavaFX Canvas)
✅ JavaFX UI (cross-platform)
🟡 Good performance (not as fast as Java2D)
🟡 Most features possible
🟡 Mobile-specific limitations
```

**Code Changes Needed:**
- Port entire rendering engine to JavaFX Canvas (6-12 months)
- Optimize for mobile GPU/memory
- Touch-optimized UI
- Mobile file access handling

---

### Codename One (Custom Graphics API)

**Platforms:** iOS, Android, Desktop, Web
```
🟡 Similar to Java2D (requires porting)
🟡 Codename One UI framework
✅ Good performance
✅ Most features possible
✅ True mobile apps
```

**Code Changes Needed:**
- Port rendering to Codename One Graphics API (4-6 months)
- Port UI to Codename One components (2-3 months)
- Mobile-specific features
- Testing on all platforms

---

### Native Mobile Apps (Separate)

**Platforms:** iOS (native), Android (native), Desktop (ICEpdf)
```
✅ Native PDF APIs (fastest)
✅ Native UI (best UX)
❌ Limited feature set (platform capabilities)
❌ 3 separate codebases
```

**Code Changes Needed:**
- iOS app from scratch (3-4 months)
- Android app from scratch (3-4 months)
- Limited code sharing (business logic only)

---

## My Recommendations

### Short Term (2026-2027): Desktop Focus ✅

**Recommendation:** Build excellent desktop JavaFX viewer, don't worry about mobile yet.

**Rationale:**
1. **Java2D works great on desktop** - Why compromise?
2. **Mobile market unclear** - Is there demand for mobile PDF viewer from ICEpdf?
3. **Native competitors strong** - Adobe, Foxit, Apple PDFKit, Google PDF Viewer
4. **Desktop is your strength** - Leverage it
5. **Web viewer option** - Can build web-based viewer later if needed

**Mobile Access Options:**
- Remote desktop (TeamViewer, Chrome Remote Desktop)
- Web-based viewer (separate project)
- Partner with native mobile app (viewer consumes ICEpdf via API)

---

### Medium Term (2027-2028): Evaluate Mobile Demand 🔍

**If users request mobile:**

**Option 1: Progressive Web App** (Recommended)
- Build web viewer using WebAssembly
- Deploy as PWA (works on all mobile browsers)
- No app store hassles
- Auto-updates
- Same codebase for mobile web + desktop web

**Option 2: Gluon Mobile**
- Only if desktop viewer already using pure JavaFX rendering
- Cross-platform JavaFX codebase
- Native mobile apps

**Option 3: License to Mobile Developer**
- Let someone else build mobile apps
- They integrate ICEpdf core (if portable)
- You focus on desktop excellence

---

### Long Term (2028+): True Mobile Strategy 🔮

**If mobile becomes critical:**

**Recommended Approach:**
1. **Create rendering abstraction layer**
   ```java
   public interface PdfRenderer {
       void renderPage(Page page, RenderContext context);
   }
   
   // Desktop implementation
   public class Java2DRenderer implements PdfRenderer { ... }
   
   // Mobile implementation
   public class JavaFXRenderer implements PdfRenderer { ... }
   
   // Or
   public class CodenameOneRenderer implements PdfRenderer { ... }
   ```

2. **Port incrementally**
   - Abstract graphics operations
   - Test on desktop first
   - Deploy to mobile when ready

3. **Or partner/license**
   - License ICEpdf to mobile developers
   - They create native mobile apps
   - You get royalties, they get powerful PDF engine

---

## Real-World Examples (2026)

### PDF Apps Using Java on Mobile

#### 1. **None using Java2D** ❌
- No mainstream PDF apps use Java2D on mobile
- Technical impossibility

#### 2. **Some using JavaFX (via Gluon)** 🟡
- Mostly internal/enterprise apps
- Not mainstream consumer apps
- Limited market presence

#### 3. **Most using Native APIs** ✅
- Adobe Acrobat (native iOS/Android)
- Foxit PDF (native iOS/Android)
- PDF Expert (native iOS)
- Xodo (native Android)
- **All use platform PDF APIs**

### Lesson: Mobile PDF = Use Native APIs

---

## Technical Constraints (2026)

### Why Java2D Doesn't Work on Mobile

#### 1. **AWT Dependency**
```java
// Java2D depends on AWT
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;

// AWT not available on iOS/Android
// No windowing system
// No desktop paradigm
```

#### 2. **Architecture Mismatch**
```
Desktop (Java2D):
User Input → AWT Event Queue → Graphics2D → X11/Win32/Quartz

Mobile (iOS):
Touch → UIKit → CoreGraphics → Metal

Mobile (Android):
Touch → View System → Canvas → Skia → OpenGL ES/Vulkan

// Completely different architectures!
```

#### 3. **Memory Model**
- Java2D assumes desktop memory (GBs available)
- Mobile more constrained (even in 2026)
- BufferedImage too heavyweight for mobile

#### 4. **Threading Model**
- Java2D assumes Event Dispatch Thread
- Mobile has different threading models (Main thread, UI thread)

---

## Alternative: Server-Side Rendering

### Hybrid Mobile Strategy

**Architecture:**
```
Mobile App (Native iOS/Android)
    ↓ HTTP Request (page, zoom, annotations)
Server (ICEpdf + Java2D)
    ↓ Render page to PNG/JPEG
    ↓ Send back image
Mobile App
    ↓ Display image
    ↓ Add JavaFX/native overlays for interactions
```

**Pros:**
- ✅ Use existing Java2D rendering (no changes!)
- ✅ Thin mobile client (fast, simple)
- ✅ Server-side caching
- ✅ Centralized document management
- ✅ Works on any mobile platform

**Cons:**
- ❌ Requires network connection
- ❌ Server infrastructure needed
- ❌ Latency on interactions
- ❌ Privacy concerns (documents on server)

**Use Cases:**
- Enterprise document management systems
- Cloud-based PDF services
- Remote document viewing
- Collaborative review

---

## Recommendations by Use Case

### Use Case 1: Desktop Professional Tool
**Recommendation:** ✅ **Desktop JavaFX + Java2D Hybrid**
- Best performance
- Full features
- No compromises
- Your current plan is perfect!

**Mobile:** Not needed or use web viewer

---

### Use Case 2: Cross-Platform Including Mobile
**Recommendation:** 🟡 **Gluon Mobile + JavaFX Canvas Rendering**
- Single codebase
- Desktop + iOS + Android
- Good (not great) performance
- Most features portable

**Requirements:**
- Convert to JavaFX Canvas (6-12 months)
- Accept some performance loss
- Budget for Gluon licensing

---

### Use Case 3: Consumer Mobile App
**Recommendation:** 🟢 **Native Mobile Apps**
- iOS: Swift + PDFKit
- Android: Kotlin + PdfRenderer
- Best performance and UX
- App store friendly

**Trade-off:**
- Separate codebases
- Can't reuse ICEpdf rendering
- More development effort
- Platform expertise needed

---

### Use Case 4: Web-Based Access
**Recommendation:** 🟡 **Progressive Web App**
- HTML5 Canvas rendering
- Works on all mobile browsers
- No app store needed
- Future-proof

**Requirements:**
- Port rendering to Canvas API (6-9 months)
- JavaScript or WebAssembly
- Cloud storage integration

---

## Decision Tree

```
Do you NEED mobile in 2026?
    │
    ├─ NO → ✅ Desktop JavaFX + Java2D Hybrid
    │        (Your current plan is perfect!)
    │
    └─ YES → Is native performance critical?
            │
            ├─ YES → 🟢 Native iOS/Android apps
            │        (Separate codebases, best UX)
            │
            └─ NO → Can you wait 6-12 months?
                   │
                   ├─ YES → 🟡 Gluon Mobile (JavaFX only)
                   │        (Port to JavaFX Canvas)
                   │
                   └─ NO → 🟡 Web viewer/PWA
                           (HTML5 Canvas, works everywhere)
```

---

## Bottom Line

### For Your ICEpdf Project:

**Java2D on Mobile?** ❌ **NO - Not possible in 2026**

**Alternatives?** ✅ **YES - But they all require significant work:**

1. **Gluon Mobile:** Requires converting to JavaFX Canvas (6-12 months)
2. **Codename One:** Requires porting to their API (8-12 months)
3. **WebAssembly/PWA:** Requires HTML5 Canvas port (6-9 months)
4. **Native Apps:** Requires full rewrite (12-18 months)

**My Recommendation:** 🎯

**Stick with desktop focus (Windows/Mac/Linux) using your hybrid approach:**
- ✅ Java2D rendering (fast, proven)
- ✅ JavaFX UI (modern, smooth)
- ✅ Zero compromises
- ✅ Full feature set
- ✅ Best performance

**For mobile users:**
- 🌐 Build web-based viewer (future project)
- 🖥️ Remote desktop access
- 📱 Partner with native mobile developer

**Don't sacrifice desktop performance for mobile compatibility you may not need.**

---

## Market Reality (2026)

### Desktop PDF Viewers - Healthy Market ✅
- Enterprise users need desktop tools
- Power users prefer desktop
- Legal, architecture, engineering industries
- Complex annotations and forms
- Large documents (100s-1000s of pages)

### Mobile PDF Viewers - Saturated Market ⚠️
- Strong native competitors (Adobe, Apple, Google)
- Users expect native feel
- Simple viewing is commoditized
- Advanced features rare (native APIs limited)

### Opportunity:
- **Desktop:** Less competition, more features possible, power users
- **Mobile:** Huge competition, feature limitations, casual users

**Focus on desktop where you can differentiate!**

---

## Conclusion

### The Answer: Java2D on Mobile in 2026

**Direct Java2D:** ❌ Not available, never was, never will be

**Viable Alternatives:** ✅ Yes, but all require significant work:
- Gluon Mobile (JavaFX only)
- Codename One (similar API)
- WebAssembly (HTML5 Canvas)
- Native apps (platform APIs)

**My Recommendation:** 
Don't worry about mobile for now. Build an excellent desktop JavaFX viewer with the hybrid approach (Java2D + JavaFX overlays). 

**If mobile becomes essential later:**
- Evaluate market demand first
- Consider web-based viewer (PWA)
- Or partner with native mobile developers
- Don't compromise desktop for mobile speculation

**Your hybrid approach is perfect for desktop and that's where the professional PDF user market is anyway!** 🎯

---

**Summary:**
- Java2D = Desktop only ✅
- Mobile = Requires different approach ⚠️
- Your plan = Perfect for desktop 🎉
- Mobile = Future consideration, not urgent 📅

---

**Created:** March 20, 2026  
**Analysis:** Java2D not available on mobile platforms  
**Recommendation:** Focus on desktop hybrid approach ⭐⭐⭐⭐⭐

